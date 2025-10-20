package neoproject.neoproxy.core.management;

import neoproject.neoproxy.NeoProxyServer;
import neoproject.neoproxy.core.HostClient;
import neoproject.neoproxy.core.exceptions.NoMoreNetworkFlowException;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static neoproject.neoproxy.NeoProxyServer.*;

/**
 * 表示一个序列密钥（Sequence Key），用于流量控制和授权。
 * <p>
 * 注意：此类不是线程安全的。每个实例应仅由单个线程使用，或通过外部同步保护。
 * 所有公共方法均会捕获内部异常，并通过 {@link NeoProxyServer#debugOperation} 记录，
 * 不会将任何异常抛出到调用方。
 */
public class SequenceKey {

    // 新增常量
    public static final int DYNAMIC_PORT = -1;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd-HH:mm");
    private static final String DB_URL = "jdbc:h2:./sk";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    private static final long CLEANUP_INTERVAL_MINUTES = 1; // 每 1 分钟清理一次过期的 key
    private static volatile boolean shutdownHookRegistered = false;
    // 后台清理任务
    private static ScheduledExecutorService cleanupScheduler;
    // 字段保持包可见性以兼容现有代码
    protected String name;
    protected double balance; // 单位：MiB
    protected String expireTime; // 格式：yyyy/MM/dd-HH:mm
    protected String port; // 已从 int 改为 String 以支持动态端口范围
    protected double rate; // 单位：Mbps
    protected boolean isEnable; // 新增的启用状态字段

    // 正则表达式，用于高效判断字符串是否为纯数字
    private static final Pattern PURE_NUMBER_PATTERN = Pattern.compile("^\\d+$");

    /**
     * 私有构造函数，用于从数据库或创建时初始化对象。
     * 此构造函数不进行空值检查，因为其调用者（如 {@link #getKeyFromDB}）已确保参数有效。
     */
    private SequenceKey(String name, double balance, String expireTime, String port, double rate, boolean isEnable) {
        this.name = name;
        this.balance = balance;
        this.expireTime = expireTime;
        this.port = port;
        this.rate = rate;
        this.isEnable = isEnable;
    }

    // ==================== 数据库连接管理 ====================

    /**
     * 获取一个新的数据库连接。
     *
     * @return 数据库连接
     * @throws SQLException 如果获取连接失败
     */
    protected static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    /**
     * 执行数据库清理任务：扫描所有密钥，删除已过期的。
     */
    private static void performDatabaseCleanup() {
        try {
            // 优化：直接在SQL中判断过期，避免全表扫描和Java日期解析
            // H2 的 PARSEDATETIME 函数可以解析字符串为时间戳
            // 只清理已启用的过期密钥
            String deleteSql = """
                    DELETE FROM sk 
                    WHERE isEnable = TRUE AND PARSEDATETIME(expireTime, 'yyyy/MM/dd-HH:mm') < NOW()
                    """;

            try (Connection conn = getConnection();
                 PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {

                int deletedCount = deleteStmt.executeUpdate();

                if (deletedCount > 0) {
                    myConsole.warn("SK-Manager", "Database cleanup completed. Deleted " + deletedCount + " expired key(s).");
                }
            }
        } catch (Exception e) {
            // 特别处理 H2 可能因格式错误导致的异常
            if (e.getMessage() != null && e.getMessage().contains("Cannot parse")) {
                myConsole.error("SK-Manager", "Found invalid expireTime format in database. Please check data integrity.");
            }
            debugOperation(e);
        }
    }

    /**
     * 优雅关闭 H2 数据库和后台任务。
     * 此方法会被 JVM Shutdown Hook 自动调用。
     */
    private static void shutdown() {
        // 1. 先关闭后台任务
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        }

        // 2. 再关闭数据库
        String shutdownSql = "SHUTDOWN";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(shutdownSql)) {
            stmt.execute();
        } catch (Exception e) {
            debugOperation(e);
        }
    }

    // ==================== 公共数据库操作方法 (所有异常被捕获并记录) ====================

    public static boolean isKeyExistsByName(String name) {
        try {
            if (name == null) {
                debugOperation(new IllegalArgumentException("name must not be null"));
                return false;
            }
            String sql = "SELECT 1 FROM sk WHERE name = ? LIMIT 1";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (Exception e) {
            debugOperation(e);
            return false;
        }
    }

    /**
     * 从数据库获取密钥，不管是否启用，只要存在就返回。
     */
    public static SequenceKey getKeyFromDB(String name) {
        try {
            if (name == null) {
                debugOperation(new IllegalArgumentException("name must not be null"));
                return null;
            }
            String sql = "SELECT name, balance, expireTime, port, rate, isEnable FROM sk WHERE name = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new SequenceKey(
                                rs.getString("name"),
                                rs.getDouble("balance"),
                                rs.getString("expireTime"),
                                rs.getString("port"), // 从 getString 读取
                                rs.getDouble("rate"),
                                rs.getBoolean("isEnable")
                        );
                    }
                    return null;
                }
            }
        } catch (Exception e) {
            debugOperation(e);
            return null;
        }
    }

    /**
     * 从数据库获取已启用的密钥，如果密钥不存在或已禁用则返回 null。
     */
    public static SequenceKey getEnabledKeyFromDB(String name) {
        try {
            if (name == null) {
                debugOperation(new IllegalArgumentException("name must not be null"));
                return null;
            }
            String sql = "SELECT name, balance, expireTime, port, rate, isEnable FROM sk WHERE name = ? AND isEnable = TRUE";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new SequenceKey(
                                rs.getString("name"),
                                rs.getDouble("balance"),
                                rs.getString("expireTime"),
                                rs.getString("port"), // 从 getString 读取
                                rs.getDouble("rate"),
                                rs.getBoolean("isEnable")
                        );
                    }
                    return null;
                }
            }
        } catch (Exception e) {
            debugOperation(e);
            return null;
        }
    }

    /**
     * 检查并执行数据库迁移，将 port 列从 INT 转换为 VARCHAR。
     * 这个方法保证了向后兼容性，旧版本的数据可以无缝升级。
     *
     * @param conn 当前数据库连接
     * @throws SQLException 如果迁移过程中发生错误
     */
    private static void migratePortColumnTypeIfNeeded(Connection conn) throws SQLException {
        // 查询 INFORMATION_SCHEMA 以确定 port 列的当前数据类型
        String checkTypeSql = """
                SELECT DATA_TYPE 
                FROM INFORMATION_SCHEMA.COLUMNS 
                WHERE TABLE_NAME = 'SK' AND COLUMN_NAME = 'PORT'
                """;
        try (PreparedStatement checkStmt = conn.prepareStatement(checkTypeSql);
             ResultSet rs = checkStmt.executeQuery()) {
            if (rs.next()) {
                String currentType = rs.getString("DATA_TYPE").toUpperCase();
                // 如果当前类型是 INT 或 INTEGER，需要执行迁移
                if ("INT".equals(currentType) || "INTEGER".equals(currentType)) {
                    myConsole.log("SK-Manager", "Detected old 'port' column type (INT). Starting migration to VARCHAR...");

                    // 执行迁移脚本：创建新表 -> 复制数据 -> 删除旧表 -> 重命名新表
                    String migrationScript = """
                            CREATE TABLE sk_new (
                                name VARCHAR(50) PRIMARY KEY,
                                balance DOUBLE NOT NULL,
                                expireTime VARCHAR(50) NOT NULL,
                                port VARCHAR(50) NOT NULL, -- Changed to VARCHAR
                                rate DOUBLE NOT NULL,
                                isEnable BOOLEAN DEFAULT TRUE NOT NULL
                            );
                            INSERT INTO sk_new (name, balance, expireTime, port, rate, isEnable)
                            SELECT name, balance, expireTime, CAST(port AS VARCHAR), rate, isEnable FROM sk;
                            DROP TABLE sk;
                            ALTER TABLE sk_new RENAME TO sk;
                            """;
                    try (Statement migrationStmt = conn.createStatement()) {
                        // H2 支持在一个 Statement 中执行多条 SQL
                        migrationStmt.execute(migrationScript);
                    }
                    myConsole.log("SK-Manager", "Migration of 'port' column to VARCHAR completed successfully.");
                }
            }
        }
    }

    /**
     * 初始化密钥数据库。
     * 此方法会自动注册一个 JVM Shutdown Hook 来确保数据库被优雅关闭，
     * 并启动一个后台任务定期清理过期的密钥。
     */
    public static void initKeyDatabase() {
        try {
            // 注册 Shutdown Hook 以确保资源被优雅关闭
            if (!shutdownHookRegistered) {
                Runtime.getRuntime().addShutdownHook(new Thread(SequenceKey::shutdown));
                shutdownHookRegistered = true;
            }

            try (Connection conn = getConnection()) {
                // 先创建基础表结构 (使用新的 VARCHAR 类型)
                String createTableSql = """
                        CREATE TABLE IF NOT EXISTS sk (
                            name VARCHAR(50) PRIMARY KEY,
                            balance DOUBLE NOT NULL,
                            expireTime VARCHAR(50) NOT NULL,
                            port VARCHAR(50) NOT NULL, -- 使用 VARCHAR 以支持端口范围
                            rate DOUBLE NOT NULL,
                            isEnable BOOLEAN DEFAULT TRUE NOT NULL
                        )
                        """;
                try (PreparedStatement stmt = conn.prepareStatement(createTableSql)) {
                    stmt.execute();
                }

                // 检查并执行必要的迁移
                migratePortColumnTypeIfNeeded(conn);

                // 确保 isEnable 列存在，如果不存在则添加（H2 支持 IF NOT EXISTS）
                String addColumnSql = "ALTER TABLE sk ADD COLUMN IF NOT EXISTS isEnable BOOLEAN DEFAULT TRUE NOT NULL";
                try (PreparedStatement stmt = conn.prepareStatement(addColumnSql)) {
                    stmt.execute();
                }
            }

            // 启动后台清理任务（只启动一次）
            if (cleanupScheduler == null) {
                cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "SequenceKey-Cleanup-Thread");
                    t.setDaemon(true); // 设置为守护线程，不影响JVM退出
                    return t;
                });
                cleanupScheduler.scheduleAtFixedRate(
                        SequenceKey::performDatabaseCleanup,
                        CLEANUP_INTERVAL_MINUTES, // 初始延迟
                        CLEANUP_INTERVAL_MINUTES, // 周期
                        TimeUnit.MINUTES
                );
            }
        } catch (Exception e) {
            debugOperation(e);
        }
    }

    /**
     * 创建一个新的密钥，支持字符串形式的端口（例如动态端口范围 "3344-3350"）。
     * <p>
     * 用于在需要存储非数字端口标识（如端口范围）时使用。
     * <p>
     * 注意：此方法不会对 {@code portStr} 进行 "1-65535" 的范围验证，因为动态端口范围（如 "3344-3350"）
     * 是合法的输入。调用方应确保 {@code portStr} 的格式正确。
     *
     * @param name       密钥名称
     * @param balance    余额 (MiB)
     * @param expireTime 过期时间 (格式: yyyy/MM/dd-HH:mm)
     * @param portStr    端口字符串，可以是纯数字（如 "8080"）或范围（如 "3344-3350"）
     * @param rate       速率 (Mbps)
     * @return 如果创建成功返回 {@code true}，如果密钥已存在或发生错误返回 {@code false}
     */
    public static boolean createNewKey(String name, double balance, String expireTime, String portStr, double rate) {
        try {
            if (name == null) {
                debugOperation(new IllegalArgumentException("name must not be null"));
                return false;
            }
            if (expireTime == null) {
                debugOperation(new IllegalArgumentException("expireTime must not be null"));
                return false;
            }
            if (portStr == null || portStr.isEmpty()) {
                debugOperation(new IllegalArgumentException("portStr must not be null or empty"));
                return false;
            }
            if (isKeyExistsByName(name)) {
                return false;
            }
            // 创建时默认启用
            String sql = "INSERT INTO sk (name, balance, expireTime, port, rate, isEnable) VALUES (?, ?, ?, ?, ?, TRUE)";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                stmt.setDouble(2, balance);
                stmt.setString(3, expireTime);
                stmt.setString(4, portStr); // 直接存储字符串
                stmt.setDouble(5, rate);
                stmt.executeUpdate();
                return true;
            }
        } catch (Exception e) {
            debugOperation(e);
            return false;
        }
    }

    public static boolean removeKey(String name) {
        try {
            if (name == null) {
                debugOperation(new IllegalArgumentException("name must not be null"));
                return false;
            }
            for (HostClient hostClient : availableHostClient) {//先断开所有的连接
                if (hostClient.getKey().getName().equals(name)) {
                    hostClient.close();
                }
            }
            String sql = "DELETE FROM sk WHERE name = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (Exception e) {
            debugOperation(e);
            return false;
        }
    }

    /**
     * 启用指定的密钥
     */
    public static boolean enableKey(String name) {
        try {
            if (name == null) {
                debugOperation(new IllegalArgumentException("name must not be null"));
                return false;
            }
            if (!isKeyExistsByName(name)) {
                return false;
            }
            // 只更新数据库，不需要处理内存中的对象
            String sql = "UPDATE sk SET isEnable = TRUE WHERE name = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (Exception e) {
            debugOperation(e);
            return false;
        }
    }

    /**
     * 禁用指定的密钥
     */
    public static boolean disableKey(String name) {
        try {
            if (name == null) {
                debugOperation(new IllegalArgumentException("name must not be null"));
                return false;
            }
            if (!isKeyExistsByName(name)) {
                return false;
            }

            // 1. 先更新内存中的活跃对象（如果存在）
            for (HostClient hostClient : NeoProxyServer.availableHostClient) {
                if (hostClient != null && hostClient.getKey() != null &&
                        name.equals(hostClient.getKey().getName())) {
                    hostClient.getKey().setEnable(false);
                }
            }

            // 2. 再更新数据库
            String sql = "UPDATE sk SET isEnable = FALSE WHERE name = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (Exception e) {
            debugOperation(e);
            return false;
        }
    }

    public static synchronized boolean saveToDB(SequenceKey sequenceKey) {
        try {
            if (sequenceKey == null) {
                debugOperation(new IllegalArgumentException("sequenceKey must not be null"));
                return false;
            }
            String name = sequenceKey.getName();
            if (name == null || !isKeyExistsByName(name)) {
                return false;
            }
            String sql = """
                    UPDATE sk 
                    SET balance = ?, expireTime = ?, port = ?, rate = ?, isEnable = ?
                    WHERE name = ?
                    """;
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setDouble(1, sequenceKey.getBalance());
                stmt.setString(2, sequenceKey.getExpireTime());
                stmt.setString(3, sequenceKey.port); // 直接存储 String
                stmt.setDouble(4, sequenceKey.getRate());
                stmt.setBoolean(5, sequenceKey.isEnable);
                stmt.setString(6, name);
                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (Exception e) {
            debugOperation(e);
            return false;
        }
    }

    // ==================== 业务逻辑方法 ====================

    public static boolean isOutOfDate(String endTime) {
        try {
            if (endTime == null) {
                debugOperation(new IllegalArgumentException("endTime must not be null"));
                return false;
            }
            LocalDateTime inputTime = LocalDateTime.parse(endTime, FORMATTER);
            LocalDateTime now = LocalDateTime.now();
            return now.isAfter(inputTime);
        } catch (DateTimeParseException e) {
            // 无效格式视为未过期（保守策略）
            debugOperation(e);
            return false;
        } catch (Exception e) {
            debugOperation(e);
            return false;
        }
    }

    // ==================== 实例方法 ====================

    public String getExpireTime() {
        return expireTime;
    }

    public boolean isEnable() {
        return isEnable;
    }

    public void setEnable(boolean enable) {
        isEnable = enable;
    }

    /**
     * 检查此密钥是否已过期。
     * <p>
     * 此方法是核心业务逻辑：
     * 1. 每次调用都会读取对象当前的 {@code expireTime} 字符串。
     * 2. 如果已过期，会自动从数据库中删除该密钥。
     * 3. 返回最新的过期状态。
     *
     * @return {@code true} 如果已过期，{@code false} 如果未过期或格式无效。
     */
    public boolean isOutOfDate() {
        try {
            String currentExpireTime = this.expireTime;
            if (currentExpireTime == null) {
                debugOperation(new IllegalStateException("expireTime is null for key: " + this.name));
                removeKey(this.name);
                return true;
            }

            boolean isExpired = SequenceKey.isOutOfDate(currentExpireTime);
            if (isExpired) {
                removeKey(this.name);
            }
            return isExpired;
        } catch (Exception e) {
            debugOperation(e);
            removeKey(this.name);
            return true;
        }
    }

    /**
     * 获取端口号。
     * <p>
     * - 如果内部存储的是纯数字字符串（如 "3344"），则返回对应的整数值。
     * - 如果内部存储的是动态端口范围（如 "3344-3350"），则返回 {@link #DYNAMIC_PORT} (-1)。
     *
     * @return 端口号或 -1
     */
    public int getPort() {
        if (port == null) {
            return DYNAMIC_PORT;
        }
        // 使用预编译的正则表达式高效判断是否为纯数字
        if (PURE_NUMBER_PATTERN.matcher(port).matches()) {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException e) {
                // 理论上不会发生，因为正则已保证是数字
                debugOperation(e);
                return DYNAMIC_PORT;
            }
        } else {
            // 包含非数字字符，视为动态端口范围
            return DYNAMIC_PORT;
        }
    }

    /**
     * 设置端口号。
     * <p>
     * 为了保持向后兼容性，此方法接收一个 `int` 值。
     * 如果传入的是 {@link #DYNAMIC_PORT} (-1)，则内部存储 "-1"。
     * 否则，将 `int` 值转换为字符串存储。
     *
     * @param port 端口号
     * @return 是否设置成功
     */
    public boolean setPort(int port) {
        if (port == DYNAMIC_PORT) {
            this.port = String.valueOf(port);
            return true;
        }
        if (port > 0 && port <= 65535) {
            this.port = String.valueOf(port);
            return true;
        }
        return false;
    }

    public double getBalance() {
        return balance;
    }

    public String getName() {
        return name;
    }

    public synchronized void addMib(double mib) {
        if (mib < 0) {
            debugOperation(new IllegalArgumentException("mib must be non-negative"));
            return;
        }
        this.balance += mib;
    }

    public synchronized void mineMib(double mib) throws NoMoreNetworkFlowException {
        try {
            if (mib < 0) {
                debugOperation(new IllegalArgumentException("mib must be non-negative"));
                NoMoreNetworkFlowException.throwException("Invalid mib value for key: " + name);
            }
            if (this.isOutOfDate()) {
                NoMoreNetworkFlowException.throwException(name);
            }
            if (balance <= 0) {
                NoMoreNetworkFlowException.throwException(name);
            }
            this.balance -= mib;
            if (this.balance < 0) {
                this.balance = 0;
            }
        } catch (NoMoreNetworkFlowException e) {
            debugOperation(e);
            throw e;
        }
    }

    public double getRate() {
        return rate;
    }

    @Override
    public String toString() {
        return "SequenceKey{" +
                "name='" + name + '\'' +
                ", balance=" + balance +
                ", expireTime='" + expireTime + '\'' +
                ", port=" + port +
                ", rate=" + rate +
                ", isEnable=" + isEnable +
                '}';
    }

    /**
     * 获取动态端口范围的起始端口号。
     * <p>
     * - 如果内部存储的 {@code port} 字段是有效的动态端口范围（格式为 "start-end"，且 start <= end），
     * 则返回起始端口号。
     * - 如果 {@code port} 是单个端口号、格式无效、为 null 或解析失败，则返回 -1。
     *
     * @return 动态端口起始号，或 -1
     */
    public int getDyStart() {
        if (port == null) {
            return DYNAMIC_PORT;
        }
        String[] parts = port.split("-", -1); // -1 to keep trailing empty strings, though not expected here
        if (parts.length != 2) {
            // Not a range format
            return DYNAMIC_PORT;
        }
        try {
            int start = Integer.parseInt(parts[0].trim());
            int end = Integer.parseInt(parts[1].trim());
            // Validate port range and order
            if (start >= 1 && end >= 1 && end <= 65535 && start <= end) {
                return start;
            } else {
                // Invalid port numbers or start > end
                return DYNAMIC_PORT;
            }
        } catch (NumberFormatException e) {
            debugOperation(new IllegalArgumentException("Invalid port range format in key: " + this.name + ", port value: '" + port + "'", e));
            return DYNAMIC_PORT;
        }
    }

    /**
     * 获取动态端口范围的结束端口号。
     * <p>
     * - 如果内部存储的 {@code port} 字段是有效的动态端口范围（格式为 "start-end"，且 start <= end），
     * 则返回结束端口号。
     * - 如果 {@code port} 是单个端口号、格式无效、为 null 或解析失败，则返回 -1。
     *
     * @return 动态端口结束号，或 -1
     */
    public int getDyEnd() {
        if (port == null) {
            return DYNAMIC_PORT;
        }
        String[] parts = port.split("-", -1);
        if (parts.length != 2) {
            // Not a range format
            return DYNAMIC_PORT;
        }
        try {
            int start = Integer.parseInt(parts[0].trim());
            int end = Integer.parseInt(parts[1].trim());
            // Validate port range and order
            if (start >= 1 && end >= 1 && end <= 65535 && start <= end) {
                return end;
            } else {
                // Invalid port numbers or start > end
                return DYNAMIC_PORT;
            }
        } catch (NumberFormatException e) {
            debugOperation(new IllegalArgumentException("Invalid port range format in key: " + this.name + ", port value: '" + port + "'", e));
            return DYNAMIC_PORT;
        }
    }
}