package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.core.ConfigOperator;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.exceptions.NoMoreNetworkFlowException;
import neoproxy.neoproxyserver.core.exceptions.PortOccupiedException;
import neoproxy.neoproxyserver.core.management.provider.KeyDataProvider;
import neoproxy.neoproxyserver.core.management.provider.LocalKeyProvider;
import neoproxy.neoproxyserver.core.management.provider.RemoteKeyProvider;
import plethora.thread.ThreadManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static neoproxy.neoproxyserver.NeoProxyServer.debugOperation;

public class SequenceKey {
    public static final int DYNAMIC_PORT = -1;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd-HH:mm");
    private static final String DB_URL = "jdbc:h2:./sk";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private static final Map<String, SequenceKey> keyCache = new ConcurrentHashMap<>();
    public static KeyDataProvider PROVIDER;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (PROVIDER != null) PROVIDER.shutdown();
        }));
    }

    // 实例字段
    protected final String name;

    // ==================== 以下代码保持不变，为了节省篇幅省略部分内容 ====================
    // (构造函数、initProvider、initKeyDatabase 等逻辑保持原样)

    // ... [Rest of the file remains exactly the same] ...
    private final ReentrantLock lock = new ReentrantLock();
    protected volatile double balance;
    protected volatile String expireTime;
    protected volatile long expireTimestamp;
    protected volatile String port;
    protected volatile double rate;
    protected volatile boolean isEnable;
    protected volatile boolean enableWebHTML;

    public SequenceKey(String name, double balance, String expireTime, String port, double rate, boolean isEnable, boolean enableWebHTML) {
        this.name = name;
        this.balance = balance;
        this.port = port;
        this.rate = rate;
        this.isEnable = isEnable;
        this.enableWebHTML = enableWebHTML;
        updateExpireTimestamp(expireTime);
    }

    public static Map<String, SequenceKey> getKeyCacheSnapshot() {
        return new HashMap<>(keyCache);
    }

    public static synchronized void reloadProvider() {
        if (PROVIDER != null) {
            try {
                PROVIDER.shutdown();
            } catch (Exception e) {
                // 这里使用 error，确保传递的是 Key
                ServerLogger.error("sequenceKey.providerShutdownError", e);
            }
        }

        keyCache.clear();
        initProvider();

        String type = (PROVIDER instanceof RemoteKeyProvider) ? "REMOTE (NKM)" : "LOCAL (H2)";
        // 【修复】将硬编码字符串改为资源 Key
        ServerLogger.info("sequenceKey.providerReloaded", type);
    }

    public static void initProvider() {
        if (ConfigOperator.MANAGER_URL != null && !ConfigOperator.MANAGER_URL.isBlank()) {
            PROVIDER = new RemoteKeyProvider(
                    ConfigOperator.MANAGER_URL,
                    ConfigOperator.MANAGER_TOKEN,
                    ConfigOperator.NODE_ID
            );
        } else {
            PROVIDER = new LocalKeyProvider();
        }
        PROVIDER.init();
    }

    public static void initKeyDatabase() {
        try {
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
                try (Statement stmt = conn.createStatement()) {
                    try {
                        stmt.execute("ALTER TABLE sk ADD COLUMN IF NOT EXISTS isEnable BOOLEAN DEFAULT TRUE");
                    } catch (SQLException ignored) {
                    }
                    try {
                        stmt.execute("ALTER TABLE sk ADD COLUMN IF NOT EXISTS enableWebHTML BOOLEAN DEFAULT FALSE");
                    } catch (SQLException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            debugOperation(e);
        }
    }

    static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static SequenceKey getKeyFromDB(String name) throws PortOccupiedException {
        if (name == null) return null;
        if (PROVIDER instanceof LocalKeyProvider) {
            SequenceKey cached = keyCache.get(name);
            if (cached != null) return cached;
        }
        if (PROVIDER == null) return null;
        SequenceKey key = PROVIDER.getKey(name);
        if (key != null) {
            keyCache.put(name, key);
        }
        return key;
    }

    public static SequenceKey getEnabledKeyFromDB(String name) throws PortOccupiedException {
        SequenceKey key = getKeyFromDB(name);
        if (key != null && key.isEnable()) return key;
        return null;
    }

    public static void releaseKey(String name) {
        if (name != null && PROVIDER != null) {
            PROVIDER.releaseKey(name);
        }
    }

    public static SequenceKey loadKeyFromDatabase(String name, boolean onlyEnabled) {
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

    public static boolean saveToDB(SequenceKey sequenceKey) {
        if (sequenceKey == null) return false;
        sequenceKey.lock.lock();
        try {
            String sql = "MERGE INTO sk KEY(name) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, sequenceKey.getName());
                stmt.setDouble(2, sequenceKey.getBalanceNoLock());
                stmt.setString(3, sequenceKey.getExpireTime());
                stmt.setString(4, sequenceKey.port);
                stmt.setDouble(5, sequenceKey.getRateNoLock());
                stmt.setBoolean(6, sequenceKey.isEnable);
                stmt.setBoolean(7, sequenceKey.enableWebHTML);
                return stmt.executeUpdate() > 0;
            } catch (Exception e) {
                debugOperation(e);
                return false;
            }
        } finally {
            sequenceKey.lock.unlock();
        }
    }

    public static boolean createNewKey(String name, double balance, String expireTime, String portStr, double rate) {
        if (name == null) return false;
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
        keyCache.remove(name);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM sk WHERE name = ?")) {
            stmt.setString(1, name);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
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

    public static void updateBalanceInDB(String name, double mib) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE sk SET balance = balance - ? WHERE name = ?")) {
            stmt.setDouble(1, mib);
            stmt.setString(2, name);
            stmt.executeUpdate();
        } catch (Exception e) {
            debugOperation(e);
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

    public static boolean isOutOfDate(String endTime) {
        try {
            if (endTime == null || endTime.isBlank() || endTime.equalsIgnoreCase("PERMANENT")) return false;
            LocalDateTime inputTime = LocalDateTime.parse(endTime, FORMATTER);
            return LocalDateTime.now().isAfter(inputTime);
        } catch (Exception e) {
            return false;
        }
    }

    public void mineMib(String sourceSubject, double mib) throws NoMoreNetworkFlowException {
        if (mib <= 0) return;
        lock.lock();
        try {
            if (isOutOfDate()) {
                NoMoreNetworkFlowException.throwException("SK-Manager", "exception.keyOutOfDateForFlow", name);
            }
            if (!isEnable) {
                NoMoreNetworkFlowException.throwException("SK-Manager", "exception.keyDisabled", name);
            }
            this.balance -= mib;
            if (this.balance <= 0) {
                this.balance = 0;
                NoMoreNetworkFlowException.throwException(sourceSubject, "exception.insufficientBalance", name);
            }
        } finally {
            lock.unlock();
        }
        if (PROVIDER != null) {
            PROVIDER.consumeFlow(this.name, mib);
        }
        if (PROVIDER instanceof LocalKeyProvider && this.balance <= 0) {
            ThreadManager.runAsync(() -> saveToDB(this));
        }
    }

    private void updateExpireTimestamp(String expireTime) {
        this.expireTime = expireTime;
        try {
            if (expireTime != null && !expireTime.isBlank() && !expireTime.equalsIgnoreCase("PERMANENT")) {
                LocalDateTime ldt = LocalDateTime.parse(expireTime, FORMATTER);
                this.expireTimestamp = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } else {
                this.expireTimestamp = 0;
            }
        } catch (DateTimeParseException e) {
            this.expireTimestamp = 0;
        }
    }

    public boolean isOutOfDate() {
        if (expireTimestamp == 0) return false;
        return System.currentTimeMillis() > expireTimestamp;
    }

    public String getName() {
        return name;
    }

    public double getBalanceNoLock() {
        return balance;
    }

    public double getRateNoLock() {
        return rate;
    }

    public double getBalance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    public void setBalance(double balance) {
        lock.lock();
        try {
            this.balance = balance;
        } finally {
            lock.unlock();
        }
    }

    public String getExpireTime() {
        lock.lock();
        try {
            return expireTime;
        } finally {
            lock.unlock();
        }
    }

    public void setExpireTime(String expireTime) {
        lock.lock();
        try {
            this.expireTime = expireTime;
            updateExpireTimestamp(expireTime);
        } finally {
            lock.unlock();
        }
    }

    public double getRate() {
        lock.lock();
        try {
            return rate;
        } finally {
            lock.unlock();
        }
    }

    public void setRate(double rate) {
        lock.lock();
        try {
            this.rate = rate;
        } finally {
            lock.unlock();
        }
    }

    public boolean isEnable() {
        lock.lock();
        try {
            return isEnable;
        } finally {
            lock.unlock();
        }
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
        lock.lock();
        try {
            return enableWebHTML;
        } finally {
            lock.unlock();
        }
    }

    public void setHTMLEnabled(boolean enable) {
        lock.lock();
        try {
            this.enableWebHTML = enable;
        } finally {
            lock.unlock();
        }
    }

    public int getPort() {
        String p = this.port;
        if (p == null) return DYNAMIC_PORT;
        if (Pattern.compile("^\\d+$").matcher(p).matches()) {
            try {
                return Integer.parseInt(p);
            } catch (Exception e) {
                return DYNAMIC_PORT;
            }
        }
        return DYNAMIC_PORT;
    }

    public void setPort(String port) {
        lock.lock();
        try {
            this.port = port;
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
}