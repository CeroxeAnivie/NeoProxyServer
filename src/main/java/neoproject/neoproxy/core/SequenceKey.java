package neoproject.neoproxy.core;

import neoproject.neoproxy.NeoProxyServer;
import neoproject.neoproxy.core.exceptions.NoMoreNetworkFlowException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static neoproject.neoproxy.NeoProxyServer.debugOperation;
import static neoproject.neoproxy.NeoProxyServer.myConsole;

/**
 * 表示一个序列密钥（Sequence Key），用于流量控制和授权。
 * <p>
 * 注意：此类不是线程安全的。每个实例应仅由单个线程使用，或通过外部同步保护。
 * 所有公共方法均会捕获内部异常，并通过 {@link NeoProxyServer#debugOperation} 记录，
 * 不会将任何异常抛出到调用方。
 */
public class SequenceKey {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd-HH:mm");
    private static final String DB_URL = "jdbc:h2:./sk";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    private static volatile boolean shutdownHookRegistered = false;

    // 后台清理任务
    private static ScheduledExecutorService cleanupScheduler;
    private static final long CLEANUP_INTERVAL_MINUTES = 1; // 每 1 分钟清理一次过期的 key

    // 字段保持包可见性以兼容现有代码
    protected String name;
    protected double balance; // 单位：MiB
    protected String expireTime; // 格式：yyyy/MM/dd-HH:mm
    protected int port;
    protected double rate; // 单位：Mbps

    /**
     * 私有构造函数，用于从数据库或创建时初始化对象。
     * 此构造函数不进行空值检查，因为其调用者（如 {@link #getKeyFromDB}）已确保参数有效。
     */
    private SequenceKey(String name, double balance, String expireTime, int port, double rate) {
        this.name = name;
        this.balance = balance;
        this.expireTime = expireTime;
        this.port = port;
        this.rate = rate;
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
            String deleteSql = """
                DELETE FROM sk 
                WHERE PARSEDATETIME(expireTime, 'yyyy/MM/dd-HH:mm') < NOW()
                """;

            try (Connection conn = getConnection();
                 PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {

                int deletedCount = deleteStmt.executeUpdate();

                if (deletedCount>0){
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

    public static SequenceKey getKeyFromDB(String name) {
        try {
            if (name == null) {
                debugOperation(new IllegalArgumentException("name must not be null"));
                return null;
            }
            String sql = "SELECT name, balance, expireTime, port, rate FROM sk WHERE name = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new SequenceKey(
                                rs.getString("name"),
                                rs.getDouble("balance"),
                                rs.getString("expireTime"),
                                rs.getInt("port"),
                                rs.getDouble("rate")
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

            String sql = """
                CREATE TABLE IF NOT EXISTS sk (
                    name VARCHAR(50) PRIMARY KEY,
                    balance DOUBLE NOT NULL,
                    expireTime VARCHAR(50) NOT NULL,
                    port INT NOT NULL,
                    rate DOUBLE NOT NULL
                )
                """;
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.execute();
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
                myConsole.warn("SK-Manager","Background cleanup task started. Interval: " + CLEANUP_INTERVAL_MINUTES + " minutes.");
            }
        } catch (Exception e) {
            debugOperation(e);
        }
    }

    public static boolean createNewKey(String name, double balance, String expireTime, int port, double rate) {
        try {
            if (name == null) {
                debugOperation(new IllegalArgumentException("name must not be null"));
                return false;
            }
            if (expireTime == null) {
                debugOperation(new IllegalArgumentException("expireTime must not be null"));
                return false;
            }
            if (port <= 0 || port > 65535) {
                return false;
            }
            if (isKeyExistsByName(name)) {
                return false;
            }
            String sql = "INSERT INTO sk (name, balance, expireTime, port, rate) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                stmt.setDouble(2, balance);
                stmt.setString(3, expireTime);
                stmt.setInt(4, port);
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

    public static boolean saveToDB(SequenceKey sequenceKey) {
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
                SET balance = ?, expireTime = ?, port = ?, rate = ?
                WHERE name = ?
                """;
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setDouble(1, sequenceKey.getBalance());
                stmt.setString(2, sequenceKey.getExpireTime());
                stmt.setInt(3, sequenceKey.getPort());
                stmt.setDouble(4, sequenceKey.getRate());
                stmt.setString(5, name);
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

    public int getPort() {
        return port;
    }

    public boolean setPort(int port) {
        if (port > 0 && port <= 65535) {
            this.port = port;
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

    public void addMib(double mib) {
        try {
            if (mib < 0) {
                debugOperation(new IllegalArgumentException("mib must be non-negative"));
                return;
            }
            this.balance += mib;
        } catch (Exception e) {
            debugOperation(e);
        }
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
        } catch (Exception e) {
            debugOperation(e);
            NoMoreNetworkFlowException.throwException(name);
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
                '}';
    }
}