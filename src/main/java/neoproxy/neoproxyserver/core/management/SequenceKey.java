package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.exceptions.NoMoreNetworkFlowException;
import plethora.thread.ThreadManager;

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
 * 序列密钥管理类 (完整修复兼容版)
 * <p>
 * 修复特性：
 * 1. 采用“快照差分同步”机制，修复高并发下流量回退/无限刷流量漏洞。
 * 2. 完美保留 enableKey, disableKey, isOutOfDate 等旧有API，确保无副作用。
 * 3. 线程安全的 mineMib 方法，支持 UDP 高并发。
 */
public class SequenceKey {

    // ==================== 常量定义 ====================
    public static final int DYNAMIC_PORT = -1;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd-HH:mm");
    private static final String DB_URL = "jdbc:h2:./sk";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    // 自动保存间隔（分钟）
    private static final long CLEANUP_INTERVAL_MINUTES = 1;

    private static final Pattern PURE_NUMBER_PATTERN = Pattern.compile("^\\d+$");

    // 全局缓存池
    private static final Map<String, SequenceKey> keyCache = new ConcurrentHashMap<>();

    private static volatile boolean shutdownHookRegistered = false;
    private static ScheduledExecutorService cleanupScheduler;

    // ==================== 实例字段 ====================
    protected final String name; // 唯一标识

    // --- 易变字段 ---
    protected volatile double balance;      // 当前内存中的实时余额

    /**
     * 【核心修复】上一次与数据库同步时的余额快照。
     * 用途：计算内存中已经扣除但尚未保存到数据库的“脏数据”增量。
     */
    protected volatile double lastSyncedBalance;

    protected volatile String expireTime;
    protected volatile long expireTimestamp;
    protected volatile String port;
    protected volatile double rate;
    protected volatile boolean isEnable;
    protected volatile boolean enableWebHTML;

    /**
     * 私有构造函数
     */
    private SequenceKey(String name, double balance, String expireTime, String port, double rate, boolean isEnable, boolean enableWebHTML) {
        this.name = name;
        this.balance = balance;
        // 初始化时，假设数据库与内存一致
        this.lastSyncedBalance = balance;
        this.port = port;
        this.rate = rate;
        this.isEnable = isEnable;
        this.enableWebHTML = enableWebHTML;
        updateExpireTimestamp(expireTime);
    }

    // ==================== 数据库连接与初始化 ====================

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static void initKeyDatabase() {
        try {
            if (!shutdownHookRegistered) {
                Runtime.getRuntime().addShutdownHook(new Thread(SequenceKey::shutdown));
                shutdownHookRegistered = true;
            }

            try (Connection conn = getConnection()) {
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

                migratePortColumnTypeIfNeeded(conn);

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE sk ADD COLUMN IF NOT EXISTS isEnable BOOLEAN DEFAULT TRUE NOT NULL");
                    stmt.execute("ALTER TABLE sk ADD COLUMN IF NOT EXISTS enableWebHTML BOOLEAN DEFAULT FALSE NOT NULL");
                }
            }

            // 启动定时任务
            if (cleanupScheduler == null) {
                cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "SequenceKey-Manager-Thread");
                    t.setDaemon(true);
                    return t;
                });
                cleanupScheduler.scheduleAtFixedRate(
                        SequenceKey::performMaintenance,
                        CLEANUP_INTERVAL_MINUTES,
                        CLEANUP_INTERVAL_MINUTES,
                        TimeUnit.MINUTES
                );
            }
        } catch (Exception e) {
            debugOperation(e);
        }
    }

    private static void shutdown() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
        }
        for (SequenceKey key : keyCache.values()) {
            saveToDB(key);
        }
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN");
        } catch (Exception e) {
            debugOperation(e);
        }
    }

    private static void performMaintenance() {
        try {
            // 禁用过期 Key
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE sk SET isEnable = FALSE WHERE isEnable = TRUE AND PARSEDATETIME(expireTime, 'yyyy/MM/dd-HH:mm') < NOW()")) {
                stmt.executeUpdate();
            }
            // 自动保存缓存中的数据
            for (SequenceKey key : keyCache.values()) {
                saveToDB(key);
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
                    myConsole.log("SK-Manager", "Migrating 'port' column...");
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
                            INSERT INTO sk_new SELECT name, balance, expireTime, CAST(port AS VARCHAR), rate, isEnable, enableWebHTML FROM sk;
                            DROP TABLE sk;
                            ALTER TABLE sk_new RENAME TO sk;
                            """;
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(migrationScript);
                    }
                }
            }
        }
    }

    // ==================== 静态工厂与管理方法 ====================

    public static SequenceKey getKeyFromDB(String name) {
        if (name == null) return null;

        SequenceKey cached = keyCache.get(name);
        if (cached != null) return cached;

        synchronized (keyCache) {
            cached = keyCache.get(name);
            if (cached != null) return cached;

            SequenceKey dbKey = loadKeyFromDatabase(name, false);
            if (dbKey != null) {
                keyCache.put(name, dbKey);
            }
            return dbKey;
        }
    }

    public static SequenceKey getEnabledKeyFromDB(String name) {
        SequenceKey key = getKeyFromDB(name);
        if (key != null && key.isEnable()) return key;
        return null;
    }

    private static SequenceKey loadKeyFromDatabase(String name, boolean onlyEnabled) {
        try {
            String sql = "SELECT * FROM sk WHERE name = ?" + (onlyEnabled ? " AND isEnable = TRUE" : "");
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new SequenceKey(
                                rs.getString("name"),
                                rs.getDouble("balance"),
                                rs.getString("expireTime"),
                                rs.getString("port"),
                                rs.getDouble("rate"),
                                rs.getBoolean("isEnable"),
                                rs.getBoolean("enableWebHTML")
                        );
                    }
                }
            }
        } catch (Exception e) {
            debugOperation(e);
        }
        return null;
    }

    /**
     * 将 Key 的状态保存到数据库。
     * 成功后更新快照基准。
     */
    public static boolean saveToDB(SequenceKey sequenceKey) {
        if (sequenceKey == null) return false;

        synchronized (sequenceKey) {
            String sql = """
                    UPDATE sk 
                    SET balance = ?, expireTime = ?, port = ?, rate = ?, isEnable = ?, enableWebHTML = ?
                    WHERE name = ?
                    """;
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                double currentBalance = sequenceKey.getBalance();

                stmt.setDouble(1, currentBalance);
                stmt.setString(2, sequenceKey.getExpireTime());
                stmt.setString(3, sequenceKey.port);
                stmt.setDouble(4, sequenceKey.getRate());
                stmt.setBoolean(5, sequenceKey.isEnable);
                stmt.setBoolean(6, sequenceKey.enableWebHTML);
                stmt.setString(7, sequenceKey.getName());

                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    sequenceKey.lastSyncedBalance = currentBalance;
                    return true;
                }
                return false;
            } catch (Exception e) {
                debugOperation(e);
                return false;
            }
        }
    }

    // ==================== [恢复] 兼容性静态方法 ====================

    public static boolean createNewKey(String name, double balance, String expireTime, String portStr, double rate) {
        if (name == null || isKeyExistsByName(name)) return false;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO sk (name, balance, expireTime, port, rate, isEnable, enableWebHTML) VALUES (?, ?, ?, ?, ?, TRUE, FALSE)")) {
            stmt.setString(1, name);
            stmt.setDouble(2, balance);
            stmt.setString(3, expireTime);
            stmt.setString(4, portStr);
            stmt.setDouble(5, rate);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            debugOperation(e);
            return false;
        }
    }

    public static boolean removeKey(String name) {
        if (name == null) return false;
        keyCache.remove(name);
        for (HostClient hc : availableHostClient) {
            if (hc.getKey() != null && hc.getKey().getName().equals(name)) {
                hc.close();
            }
        }
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM sk WHERE name = ?")) {
            stmt.setString(1, name);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            debugOperation(e);
            return false;
        }
    }

    // [恢复] 启用 Key
    public static boolean enableKey(String name) {
        // 更新缓存
        SequenceKey key = keyCache.get(name);
        if (key != null) key.setEnable(true); // Setter 只更新内存
        // 更新数据库
        return updateKeyStatusInDB(name, true);
    }

    // [恢复] 禁用 Key
    public static boolean disableKey(String name) {
        // 更新缓存
        SequenceKey key = keyCache.get(name);
        if (key != null) key.setEnable(false);
        // 更新数据库
        return updateKeyStatusInDB(name, false);
    }

    // [恢复] 辅助方法
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
        if (keyCache.containsKey(name)) return true;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM sk WHERE name = ? LIMIT 1")) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    // [恢复] 静态工具方法
    public static boolean isOutOfDate(String endTime) {
        try {
            if (endTime == null) return false;
            LocalDateTime inputTime = LocalDateTime.parse(endTime, FORMATTER);
            return LocalDateTime.now().isAfter(inputTime);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== [核心修复] mineMib 方法 ====================

    private synchronized void smartSyncWithDB() {
        SequenceKey dbKey = loadKeyFromDatabase(this.name, false);
        if (dbKey != null) {
            double usedSinceLastSync = this.lastSyncedBalance - this.balance;
            if (usedSinceLastSync < 0) usedSinceLastSync = 0;

            double dbLatestBalance = dbKey.balance;
            double newCorrectBalance = dbLatestBalance - usedSinceLastSync;
            if (newCorrectBalance < 0) newCorrectBalance = 0;

            this.balance = newCorrectBalance;
            this.lastSyncedBalance = dbLatestBalance;

            this.isEnable = dbKey.isEnable;
            this.rate = dbKey.rate;
            this.port = dbKey.port;
            this.updateExpireTimestamp(dbKey.expireTime);
        }
    }

    /**
     * 【修复版】扣除流量
     * 1. 忽略负数输入，防止因 Socket 错误导致的脏数据踢掉客户端。
     * 2. 流量耗尽时的 DB 保存改为异步，避免阻塞。
     */
    public synchronized void mineMib(String sourceSubject, double mib) throws NoMoreNetworkFlowException {
        // 【修复】忽略非法流量值，而不是抛出异常
        if (mib <= 0) {
            return;
        }

        if (isOutOfDate()) {
            NoMoreNetworkFlowException.throwException("SK-Manager", "exception.keyOutOfDateForFlow", name);
        }

        double estimatedBalance = this.balance - mib;

        if (estimatedBalance < 0) {
            smartSyncWithDB();
            estimatedBalance = this.balance - mib;
        }

        if (estimatedBalance < 0) {
            if (this.balance > 0) {
                this.balance = 0;
                // 【优化】流量归零是低频事件，但为防阻塞，建议异步保存
                // 这里使用 ThreadManager 的虚拟线程来执行保存，避免卡顿当前数据包处理
                ThreadManager.runAsync(() -> saveToDB(this));
            }
            NoMoreNetworkFlowException.throwException(sourceSubject, "exception.insufficientBalance", name);
        }

        this.balance = estimatedBalance;

        if (this.balance == 0) {
            ThreadManager.runAsync(() -> saveToDB(this));
        }
    }

    public synchronized void addMib(double mib) {
        if (mib < 0) return;
        this.balance += mib;
        ThreadManager.runAsync(() -> saveToDB(this));
    }

    // ==================== Getter / Setter / Util ====================

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
        // 原代码 Setter 只有内存操作，此处保持一致，以免影响性能
    }

    public boolean isHTMLEnabled() {
        return enableWebHTML;
    }

    public void setHTMLEnabled(boolean enableWebHTML) {
        this.enableWebHTML = enableWebHTML;
    }

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

    public boolean isOutOfDate() {
        if (expireTimestamp == 0) return true;
        boolean expired = System.currentTimeMillis() > expireTimestamp;
        if (expired && isEnable) {
            // 状态变更，通过静态方法更新数据库，确保行为一致
            disableKey(this.name);
            this.isEnable = false;
        }
        return expired;
    }

    public int getPort() {
        String p = this.port;
        if (p == null) return DYNAMIC_PORT;
        if (PURE_NUMBER_PATTERN.matcher(p).matches()) {
            try {
                return Integer.parseInt(p);
            } catch (Exception e) {
                return DYNAMIC_PORT;
            }
        }
        return DYNAMIC_PORT;
    }

    public synchronized boolean setPort(int port) {
        if (port == DYNAMIC_PORT || (port > 0 && port <= 65535)) {
            this.port = String.valueOf(port);
            return true;
        }
        return false;
    }

    public int getDyStart() {
        return parseRange(0);
    }

    public int getDyEnd() {
        return parseRange(1);
    }

    private int parseRange(int index) {
        String p = this.port;
        if (p == null) return DYNAMIC_PORT;
        String[] parts = p.split("-", -1);
        if (parts.length == 2) {
            try {
                int start = Integer.parseInt(parts[0].trim());
                int end = Integer.parseInt(parts[1].trim());
                if (start >= 1 && end <= 65535 && start <= end) {
                    return index == 0 ? start : end;
                }
            } catch (Exception ignored) {
            }
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
                '}';
    }
}