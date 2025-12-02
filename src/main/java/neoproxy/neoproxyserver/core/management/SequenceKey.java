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
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static neoproxy.neoproxyserver.NeoProxyServer.*;

/**
 * 序列密钥管理类 (Java 21 Virtual Threads Compatible)
 * <p>
 * 修改说明：
 * 1. 移除了所有 synchronized 关键字，替换为 ReentrantLock，防止虚拟线程 Pinning。
 * 2. 引入了 STATIC_CACHE_LOCK 用于控制 keyCache 的并发加载。
 * 3. 实例操作使用独立的 ReentrantLock 保护。
 */
public class SequenceKey {

    // ==================== 常量定义 ====================
    public static final int DYNAMIC_PORT = -1;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd-HH:mm");
    private static final String DB_URL = "jdbc:h2:./sk";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    private static final long CLEANUP_INTERVAL_MINUTES = 1;

    private static final Pattern PURE_NUMBER_PATTERN = Pattern.compile("^\\d+$");

    // 缓存容器
    private static final Map<String, SequenceKey> keyCache = new ConcurrentHashMap<>();

    // 【核心修复】全局静态锁，用于替代 synchronized(keyCache)
    private static final ReentrantLock STATIC_CACHE_LOCK = new ReentrantLock();

    private static volatile boolean shutdownHookRegistered = false;
    private static ScheduledExecutorService cleanupScheduler;

    // ==================== 实例字段 ====================
    protected final String name;

    // 【核心修复】实例级锁，保护单个 Key 的状态（如 balance）及相关 DB 操作
    private final ReentrantLock lock = new ReentrantLock();

    protected volatile double balance;
    protected volatile double lastSyncedBalance;
    protected volatile String expireTime;
    protected volatile long expireTimestamp;
    protected volatile String port;
    protected volatile double rate;
    protected volatile boolean isEnable;
    protected volatile boolean enableWebHTML;

    private SequenceKey(String name, double balance, String expireTime, String port, double rate, boolean isEnable, boolean enableWebHTML) {
        this.name = name;
        this.balance = balance;
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
        // keyCache 是 ConcurrentHashMap，迭代是弱一致性的，无需加锁
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
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE sk SET isEnable = FALSE WHERE isEnable = TRUE AND PARSEDATETIME(expireTime, 'yyyy/MM/dd-HH:mm') < NOW()")) {
                stmt.executeUpdate();
            }
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

        // 一级缓存检查
        SequenceKey cached = keyCache.get(name);
        if (cached != null) return cached;

        // 【核心修复】使用 STATIC_CACHE_LOCK 替代 synchronized(keyCache)
        STATIC_CACHE_LOCK.lock();
        try {
            // 双重检查
            cached = keyCache.get(name);
            if (cached != null) return cached;

            SequenceKey dbKey = loadKeyFromDatabase(name, false);
            if (dbKey != null) {
                keyCache.put(name, dbKey);
            }
            return dbKey;
        } finally {
            STATIC_CACHE_LOCK.unlock();
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
     */
    public static boolean saveToDB(SequenceKey sequenceKey) {
        if (sequenceKey == null) return false;

        // 【核心修复】使用实例锁 sequenceKey.lock
        // ReentrantLock 对虚拟线程友好，不会 Pinning
        sequenceKey.lock.lock();
        try {
            String sql = """
                    UPDATE sk 
                    SET balance = ?, expireTime = ?, port = ?, rate = ?, isEnable = ?, enableWebHTML = ?
                    WHERE name = ?
                    """;
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // 使用 NoLock 内部方法防止自我调用时死锁（虽然 ReentrantLock 可重入，但直接访问更高效）
                double currentBalance = sequenceKey.getBalanceNoLock();

                stmt.setDouble(1, currentBalance);
                stmt.setString(2, sequenceKey.getExpireTime());
                stmt.setString(3, sequenceKey.port);
                stmt.setDouble(4, sequenceKey.getRateNoLock());
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
        } finally {
            sequenceKey.lock.unlock();
        }
    }

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

    public static boolean enableKey(String name) {
        SequenceKey key = keyCache.get(name);
        if (key != null) key.setEnable(true);
        return updateKeyStatusInDB(name, true);
    }

    public static boolean disableKey(String name) {
        SequenceKey key = keyCache.get(name);
        if (key != null) key.setEnable(false);
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

    public static boolean isOutOfDate(String endTime) {
        try {
            if (endTime == null) return false;
            LocalDateTime inputTime = LocalDateTime.parse(endTime, FORMATTER);
            return LocalDateTime.now().isAfter(inputTime);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 核心业务逻辑 (MINE MIB) ====================

    // 内部私有方法，调用前必须持有 lock
    private void smartSyncWithDB() {
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

    public void mineMib(String sourceSubject, double mib) throws NoMoreNetworkFlowException {
        if (mib <= 0) return;

        // 【核心修复】使用 ReentrantLock 替代 synchronized
        lock.lock();
        try {
            if (isOutOfDate()) {
                NoMoreNetworkFlowException.throwException("SK-Manager", "exception.keyOutOfDateForFlow", name);
            }

            double estimatedBalance = this.balance - mib;

            if (estimatedBalance < 0) {
                // 余额不足，尝试从 DB 同步一次最新余额
                smartSyncWithDB();
                estimatedBalance = this.balance - mib;
            }

            if (estimatedBalance < 0) {
                if (this.balance > 0) {
                    this.balance = 0;
                    // 异步保存归零状态，runAsync 内部会调用 saveToDB，后者会再次获取锁，
                    // 由于是不同线程，saveToDB 会在 lock.lock() 处等待当前 mineMib 释放锁，这是安全的。
                    ThreadManager.runAsync(() -> saveToDB(this));
                }
                NoMoreNetworkFlowException.throwException(sourceSubject, "exception.insufficientBalance", name);
            }

            this.balance = estimatedBalance;

            if (this.balance == 0) {
                ThreadManager.runAsync(() -> saveToDB(this));
            }
        } finally {
            lock.unlock();
        }
    }

    public void addMib(double mib) {
        if (mib < 0) return;
        lock.lock();
        try {
            this.balance += mib;
        } finally {
            lock.unlock();
        }
        ThreadManager.runAsync(() -> saveToDB(this));
    }

    // ==================== Getter / Setter / Util ====================

    public double getBalance() {
        // 读取加锁，确保读取到最新的写入（虽然 volatile 提供了可见性，但为了配合 lock 的互斥语义，建议加上）
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    // 内部专用，避免持有锁时调用 getBalance() 导致的重入开销（虽然不会死锁）
    private double getBalanceNoLock() {
        return balance;
    }

    public String getName() {
        return name;
    }

    public double getRate() {
        lock.lock();
        try {
            return rate;
        } finally {
            lock.unlock();
        }
    }

    private double getRateNoLock() {
        return rate;
    }

    public boolean isEnable() {
        return isEnable;
    }

    public void setEnable(boolean enable) {
        lock.lock();
        try {
            this.isEnable = enable;
        } finally {
            lock.unlock();
        }
    }

    public boolean isHTMLEnabled() {
        return enableWebHTML;
    }

    public void setHTMLEnabled(boolean enableWebHTML) {
        lock.lock();
        try {
            this.enableWebHTML = enableWebHTML;
        } finally {
            lock.unlock();
        }
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
            // 这里会导致递归调用 saveToDB 吗？disableKey -> updateKeyStatusInDB (独立SQL) -> 安全
            disableKey(this.name);
            lock.lock();
            try {
                this.isEnable = false;
            } finally {
                lock.unlock();
            }
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

    public boolean setPort(int port) {
        lock.lock();
        try {
            if (port == DYNAMIC_PORT || (port > 0 && port <= 65535)) {
                this.port = String.valueOf(port);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
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