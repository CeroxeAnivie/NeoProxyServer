package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.exceptions.NoMoreNetworkFlowException;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static neoproxy.neoproxyserver.NeoProxyServer.*;

/**
 * 表示一个序列密钥（Sequence Key），用于流量控制和授权。
 * <p>
 * 高并发优化版：
 * 1. 使用 ConcurrentHashMap 实现享元模式（Flyweight），确保同一 Key 在内存中单例。
 * 2. 优化时间解析性能，缓存时间戳。
 * 3. 增加余额双重检查机制，修复高并发下余额状态不一致的问题。
 */
public class SequenceKey {

    // 常量定义
    public static final int DYNAMIC_PORT = -1;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd-HH:mm");
    private static final String DB_URL = "jdbc:h2:./sk";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    private static final long CLEANUP_INTERVAL_MINUTES = 1; // 每 1 分钟清理一次

    // 正则表达式：判断字符串是否为纯数字
    private static final Pattern PURE_NUMBER_PATTERN = Pattern.compile("^\\d+$");

    // 全局缓存池：确保同一个 name 对应唯一的 SequenceKey 实例
    private static final Map<String, SequenceKey> keyCache = new ConcurrentHashMap<>();

    private static volatile boolean shutdownHookRegistered = false;
    private static ScheduledExecutorService cleanupScheduler;

    // 实例字段
    protected final String name; // 唯一标识，不可变

    // 易变字段，使用 volatile 保证可见性，配合 synchronized 保证原子性
    protected volatile double balance;      // 单位：MiB
    protected volatile String expireTime;   // 格式：yyyy/MM/dd-HH:mm
    protected volatile long expireTimestamp;// 缓存的过期时间戳（毫秒），优化性能
    protected volatile String port;         // 支持动态端口范围
    protected volatile double rate;         // 单位：Mbps
    protected volatile boolean isEnable;    // 启用状态
    protected volatile boolean enableWebHTML; // Web HTML 启用状态

    /**
     * 私有构造函数，强制通过工厂方法获取实例，确保单例性。
     */
    private SequenceKey(String name, double balance, String expireTime, String port, double rate, boolean isEnable, boolean enableWebHTML) {
        this.name = name;
        this.balance = balance;
        this.port = port;
        this.rate = rate;
        this.isEnable = isEnable;
        this.enableWebHTML = enableWebHTML;
        // 初始化时间戳
        updateExpireTimestamp(expireTime);
    }

    /**
     * 获取数据库连接
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // ==================== 核心工厂方法 (单例/缓存管理) ====================

    /**
     * 从数据库获取密钥。
     * 优先从内存缓存获取，如果缓存不存在则查询数据库并放入缓存。
     * 保证同一名称返回同一对象实例。
     */
    public static SequenceKey getKeyFromDB(String name) {
        if (name == null) {
            debugOperation(new IllegalArgumentException("name must not be null"));
            return null;
        }

        // 1. 尝试从缓存获取
        SequenceKey cachedKey = keyCache.get(name);
        if (cachedKey != null) {
            return cachedKey;
        }

        // 2. 缓存未命中，进行数据库查询（加锁防止并发创建）
        synchronized (keyCache) {
            // 双重检查
            cachedKey = keyCache.get(name);
            if (cachedKey != null) {
                return cachedKey;
            }

            SequenceKey dbKey = loadKeyFromDatabase(name, false);
            if (dbKey != null) {
                keyCache.put(name, dbKey);
            }
            return dbKey;
        }
    }

    /**
     * 从数据库获取已启用的密钥。
     */
    public static SequenceKey getEnabledKeyFromDB(String name) {
        SequenceKey key = getKeyFromDB(name);
        if (key != null && key.isEnable()) {
            return key;
        }
        return null;
    }

    /**
     * 内部辅助方法：纯粹从数据库加载数据，不涉及缓存逻辑
     */
    private static SequenceKey loadKeyFromDatabase(String name, boolean onlyEnabled) {
        try {
            String sql = "SELECT name, balance, expireTime, port, rate, isEnable, enableWebHTML FROM sk WHERE name = ?" + (onlyEnabled ? " AND isEnable = TRUE" : "");
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        boolean enableWebHTML;
                        try {
                            enableWebHTML = rs.getBoolean("enableWebHTML");
                        } catch (SQLException e) {
                            enableWebHTML = false;
                        }
                        return new SequenceKey(
                                rs.getString("name"),
                                rs.getDouble("balance"),
                                rs.getString("expireTime"),
                                rs.getString("port"),
                                rs.getDouble("rate"),
                                rs.getBoolean("isEnable"),
                                enableWebHTML
                        );
                    }
                }
            }
        } catch (Exception e) {
            debugOperation(e);
        }
        return null;
    }

    // ==================== 数据库管理与初始化 ====================

    /**
     * 数据库清理任务：
     * 1. 禁用 DB 中过期的 Key。
     * 2. 清理内存 Cache 中已过期或已禁用的 Key，释放内存。
     */
    private static void performDatabaseCleanup() {
        try {
            // 1. 数据库层面禁用过期 Key
            String updateSql = """
                    UPDATE sk 
                    SET isEnable = FALSE 
                    WHERE isEnable = TRUE AND PARSEDATETIME(expireTime, 'yyyy/MM/dd-HH:mm') < NOW()
                    """;

            try (Connection conn = getConnection();
                 PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                int disabledCount = updateStmt.executeUpdate();
                if (disabledCount > 0) {
                    myConsole.warn("SK-Manager", "Cleanup: Disabled " + disabledCount + " expired key(s) in DB.");
                }
            }

            // 2. 内存层面清理缓存
            // 移除那些已过期或被禁用的对象，以便下次获取时能从数据库拉取最新状态（如果有变更的话）
            keyCache.values().removeIf(key -> {
                boolean shouldRemove = key.isOutOfDate() || !key.isEnable();
                if (shouldRemove) {
                    // 如果刚好有连接正在使用这个key，虽然从cache移除，但连接持有的引用依然有效，直到连接断开
                    // 这里不做强制断开，交给具体的业务逻辑处理
                }
                return shouldRemove;
            });

        } catch (Exception e) {
            // H2 异常处理
            if (e.getMessage() != null && e.getMessage().contains("Cannot parse")) {
                myConsole.error("SK-Manager", "Invalid expireTime format detected in cleanup.");
            }
            debugOperation(e);
        }
    }

    private static void shutdown() {
        // 关闭清理任务
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 关闭数据库
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN");
        } catch (Exception e) {
            debugOperation(e);
        }
    }

    public static void initKeyDatabase() {
        try {
            if (!shutdownHookRegistered) {
                Runtime.getRuntime().addShutdownHook(new Thread(SequenceKey::shutdown));
                shutdownHookRegistered = true;
            }

            try (Connection conn = getConnection()) {
                // 建表
                String createTableSql = """
                        CREATE TABLE IF NOT EXISTS sk (
                            name VARCHAR(50) PRIMARY KEY,
                            balance DOUBLE NOT NULL,
                            expireTime VARCHAR(50) NOT NULL,
                            port VARCHAR(50) NOT NULL, 
                            rate DOUBLE NOT NULL,
                            isEnable BOOLEAN DEFAULT TRUE NOT NULL,
                            enableWebHTML BOOLEAN DEFAULT FALSE NOT NULL
                        )
                        """;
                try (PreparedStatement stmt = conn.prepareStatement(createTableSql)) {
                    stmt.execute();
                }

                // 迁移 port 列类型
                migratePortColumnTypeIfNeeded(conn);

                // 补充列
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE sk ADD COLUMN IF NOT EXISTS isEnable BOOLEAN DEFAULT TRUE NOT NULL");
                    stmt.execute("ALTER TABLE sk ADD COLUMN IF NOT EXISTS enableWebHTML BOOLEAN DEFAULT FALSE NOT NULL");
                }
            }

            // 启动定时清理
            if (cleanupScheduler == null) {
                cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "SequenceKey-Cleanup-Thread");
                    t.setDaemon(true);
                    return t;
                });
                cleanupScheduler.scheduleAtFixedRate(
                        SequenceKey::performDatabaseCleanup,
                        CLEANUP_INTERVAL_MINUTES,
                        CLEANUP_INTERVAL_MINUTES,
                        TimeUnit.MINUTES
                );
            }
        } catch (Exception e) {
            debugOperation(e);
        }
    }

    private static void migratePortColumnTypeIfNeeded(Connection conn) throws SQLException {
        String checkTypeSql = "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'SK' AND COLUMN_NAME = 'PORT'";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkTypeSql);
             ResultSet rs = checkStmt.executeQuery()) {
            if (rs.next()) {
                String currentType = rs.getString("DATA_TYPE").toUpperCase();
                if ("INT".equals(currentType) || "INTEGER".equals(currentType)) {
                    myConsole.log("SK-Manager", "Migrating 'port' column from INT to VARCHAR...");
                    String migrationScript = """
                            CREATE TABLE sk_new (
                                name VARCHAR(50) PRIMARY KEY,
                                balance DOUBLE NOT NULL,
                                expireTime VARCHAR(50) NOT NULL,
                                port VARCHAR(50) NOT NULL,
                                rate DOUBLE NOT NULL,
                                isEnable BOOLEAN DEFAULT TRUE NOT NULL,
                                enableWebHTML BOOLEAN DEFAULT FALSE NOT NULL
                            );
                            INSERT INTO sk_new (name, balance, expireTime, port, rate, isEnable)
                            SELECT name, balance, expireTime, CAST(port AS VARCHAR), rate, isEnable FROM sk;
                            DROP TABLE sk;
                            ALTER TABLE sk_new RENAME TO sk;
                            """;
                    try (Statement migrationStmt = conn.createStatement()) {
                        migrationStmt.execute(migrationScript);
                    }
                    myConsole.log("SK-Manager", "Migration completed.");
                }
            }
        }
    }

    // ==================== 静态操作方法 ====================

    public static boolean createNewKey(String name, double balance, String expireTime, String portStr, double rate) {
        try {
            if (name == null || expireTime == null || portStr == null) {
                return false;
            }
            if (isKeyExistsByName(name)) {
                return false;
            }
            String sql = "INSERT INTO sk (name, balance, expireTime, port, rate, isEnable, enableWebHTML) VALUES (?, ?, ?, ?, ?, TRUE, FALSE)";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                stmt.setDouble(2, balance);
                stmt.setString(3, expireTime);
                stmt.setString(4, portStr);
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
            if (name == null) return false;

            // 1. 从缓存移除
            keyCache.remove(name);

            // 2. 断开相关连接
            for (HostClient hostClient : availableHostClient) {
                if (hostClient.getKey() != null && hostClient.getKey().getName().equals(name)) {
                    hostClient.close();
                }
            }

            // 3. 删除数据库记录
            String sql = "DELETE FROM sk WHERE name = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                return stmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            debugOperation(e);
            return false;
        }
    }

    public static boolean enableKey(String name) {
        // 更新内存状态
        SequenceKey key = keyCache.get(name);
        if (key != null) key.setEnable(true);

        // 更新数据库
        return updateKeyStatusInDB(name, true);
    }

    public static boolean disableKey(String name) {
        // 更新内存状态
        SequenceKey key = keyCache.get(name);
        if (key != null) key.setEnable(false);

        // 更新数据库
        return updateKeyStatusInDB(name, false);
    }

    private static boolean updateKeyStatusInDB(String name, boolean status) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE sk SET isEnable = ? WHERE name = ?")) {
            stmt.setBoolean(1, status);
            stmt.setString(2, name);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            debugOperation(e);
            return false;
        }
    }

    public static boolean isKeyExistsByName(String name) {
        if (name == null) return false;
        if (keyCache.containsKey(name)) return true;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM sk WHERE name = ? LIMIT 1")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            debugOperation(e);
            return false;
        }
    }

    /**
     * 将对象状态保存到数据库。
     * 注意：请确保 sequenceKey 是从 getKeyFromDB 获取的实例。
     */
    public static boolean saveToDB(SequenceKey sequenceKey) {
        if (sequenceKey == null) return false;

        // 验证一致性（可选）：确保我们正在保存的是当前缓存的实例
        SequenceKey cached = keyCache.get(sequenceKey.getName());
        if (cached != null && cached != sequenceKey) {
            myConsole.warn("SK-Manager", "Warning: Saving a sequence key instance that does not match the cached one.");
        }

        synchronized (sequenceKey) {
            String sql = """
                    UPDATE sk 
                    SET balance = ?, expireTime = ?, port = ?, rate = ?, isEnable = ?, enableWebHTML = ?
                    WHERE name = ?
                    """;
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setDouble(1, sequenceKey.getBalance());
                stmt.setString(2, sequenceKey.getExpireTime());
                stmt.setString(3, sequenceKey.port);
                stmt.setDouble(4, sequenceKey.getRate());
                stmt.setBoolean(5, sequenceKey.isEnable);
                stmt.setBoolean(6, sequenceKey.enableWebHTML);
                stmt.setString(7, sequenceKey.getName());
                return stmt.executeUpdate() > 0;
            } catch (Exception e) {
                debugOperation(e);
                return false;
            }
        }
    }

    // 保留旧的静态方法以兼容，但建议使用实例方法
    public static boolean isOutOfDate(String endTime) {
        try {
            if (endTime == null) return false;
            LocalDateTime inputTime = LocalDateTime.parse(endTime, FORMATTER);
            return LocalDateTime.now().isAfter(inputTime);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 实例方法 (业务逻辑) ====================

    /**
     * 更新过期时间并重新计算时间戳
     */
    private void updateExpireTimestamp(String expireTime) {
        this.expireTime = expireTime;
        try {
            if (expireTime != null) {
                LocalDateTime ldt = LocalDateTime.parse(expireTime, FORMATTER);
                this.expireTimestamp = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } else {
                this.expireTimestamp = 0;
            }
        } catch (DateTimeParseException e) {
            this.expireTimestamp = 0;
        }
    }

    public String getExpireTime() {
        return expireTime;
    }

    /**
     * 检查是否过期。
     * 优化：使用 long 比较，极大提升高并发下的判断速度。
     */
    public boolean isOutOfDate() {
        if (expireTimestamp == 0) return true; // 未设置或格式错误视为过期/无效

        boolean expired = System.currentTimeMillis() > expireTimestamp;

        if (expired && isEnable) {
            // 状态变更，禁用之
            disableKey(this.name);
            this.isEnable = false;
        }
        return expired;
    }

    /**
     * 强制从数据库刷新当前对象的状态。
     * 用于解决内存与数据库数据不一致的问题。
     */
    public synchronized void reloadFromDB() {
        SequenceKey dbKey = loadKeyFromDatabase(this.name, false);
        if (dbKey != null) {
            this.balance = dbKey.balance;
            this.isEnable = dbKey.isEnable;
            this.rate = dbKey.rate;
            this.port = dbKey.port;
            this.updateExpireTimestamp(dbKey.expireTime);
            myConsole.log("SK-Manager", "Key [" + name + "] reloaded from DB. New Balance: " + balance);
        }
    }

    /**
     * 扣除流量（线程安全且具备容错能力）。
     */
    public synchronized void mineMib(String sourceSubject, double mib) throws NoMoreNetworkFlowException {
        if (mib < 0) {
            NoMoreNetworkFlowException.throwException("SK-Manager", "exception.invalidMibValue", name);
        }

        if (isOutOfDate()) {
            NoMoreNetworkFlowException.throwException("SK-Manager", "exception.keyOutOfDateForFlow", name);
        }

        // 核心修复逻辑：
        // 如果内存余额不足，先不急着抛异常，尝试从数据库拉取最新数据。
        // 这解决了“管理员刚充值，但内存未刷新导致 FNT3044 无效”的问题。
        if (this.balance < mib) {
            reloadFromDB();
        }

        // 二次检查
        if (this.balance <= 0 || this.balance < mib) {
            NoMoreNetworkFlowException.throwException(sourceSubject, "exception.insufficientBalance", name);
        }

        this.balance -= mib;
        if (this.balance < 0) this.balance = 0;

        // 注意：此处仅更新内存。
        // 建议外部有一个定时任务周期性调用 saveToDB(key) 来持久化余额，
        // 否则服务器崩溃会导致部分流量统计丢失。
    }

    public synchronized void addMib(double mib) {
        if (mib < 0) return;
        this.balance += mib;
        // 充值操作通常不频繁，建议立即落盘
        saveToDB(this);
    }

    public synchronized double getBalance() {
        return balance;
    }

    public String getName() {
        return name;
    }

    public synchronized double getRate() {
        return rate;
    }

    public boolean isEnable() {
        return isEnable;
    }

    public synchronized void setEnable(boolean enable) {
        this.isEnable = enable;
    }

    public boolean isHTMLEnabled() {
        return enableWebHTML;
    }

    public void setHTMLEnabled(boolean enableWebHTML) {
        this.enableWebHTML = enableWebHTML;
    }

    public int getPort() {
        String p = this.port; // 读取 volatile
        if (p == null) return DYNAMIC_PORT;
        if (PURE_NUMBER_PATTERN.matcher(p).matches()) {
            try {
                return Integer.parseInt(p);
            } catch (NumberFormatException e) {
                return DYNAMIC_PORT;
            }
        }
        return DYNAMIC_PORT;
    }

    public synchronized boolean setPort(int port) {
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

    public int getDyStart() {
        String p = this.port;
        if (p == null) return DYNAMIC_PORT;
        String[] parts = p.split("-", -1);
        if (parts.length != 2) return DYNAMIC_PORT;
        try {
            int start = Integer.parseInt(parts[0].trim());
            int end = Integer.parseInt(parts[1].trim());
            if (start >= 1 && end >= 1 && end <= 65535 && start <= end) {
                return start;
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return DYNAMIC_PORT;
    }

    public int getDyEnd() {
        String p = this.port;
        if (p == null) return DYNAMIC_PORT;
        String[] parts = p.split("-", -1);
        if (parts.length != 2) return DYNAMIC_PORT;
        try {
            int start = Integer.parseInt(parts[0].trim());
            int end = Integer.parseInt(parts[1].trim());
            if (start >= 1 && end >= 1 && end <= 65535 && start <= end) {
                return end;
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return DYNAMIC_PORT;
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
                ", enableWebHTML=" + enableWebHTML +
                '}';
    }
}