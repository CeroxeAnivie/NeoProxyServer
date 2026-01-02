package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.core.ConfigOperator;
import neoproxy.neoproxyserver.core.Debugger;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.exceptions.*;
import neoproxy.neoproxyserver.core.management.provider.KeyDataProvider;
import neoproxy.neoproxyserver.core.management.provider.LocalKeyProvider;
import neoproxy.neoproxyserver.core.management.provider.RemoteKeyProvider;

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

import static neoproxy.neoproxyserver.core.Debugger.debugOperation;

public class SequenceKey {
    public static final int DYNAMIC_PORT = -1;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd-HH:mm");
    private static final String DB_URL = "jdbc:h2:./sk";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private static final Map<String, SequenceKey> keyCache = new ConcurrentHashMap<>();

    // 全局数据提供者
    public static KeyDataProvider PROVIDER;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (PROVIDER != null) {
                Debugger.debugOperation("SequenceKey ShutdownHook triggered.");
                PROVIDER.shutdown();
            }
        }));
    }

    protected final String name;
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
        Debugger.debugOperation("Reloading KeyDataProvider...");
        if (PROVIDER != null) {
            try {
                PROVIDER.shutdown();
            } catch (Exception e) {
                // Log: Error shutting down provider during reload: {0}
                ServerLogger.error("sequenceKey.providerShutdownError", e, e.getMessage());
                Debugger.debugOperation(e);
            }
        }

        keyCache.clear();
        initProvider();

        String type = (PROVIDER instanceof RemoteKeyProvider) ? "REMOTE (NKM)" : "LOCAL (H2)";
        // Log: Provider reloaded. Current type: {0}
        ServerLogger.info("sequenceKey.providerReloaded", type);
        Debugger.debugOperation("Provider reloaded. Type: " + type);
    }

    public static void initProvider() {
        Debugger.debugOperation("Initializing KeyDataProvider...");
        if (ConfigOperator.MANAGER_URL != null && !ConfigOperator.MANAGER_URL.isBlank()) {
            Debugger.debugOperation("Using RemoteKeyProvider with URL: " + ConfigOperator.MANAGER_URL);
            PROVIDER = new RemoteKeyProvider(
                    ConfigOperator.MANAGER_URL,
                    ConfigOperator.MANAGER_TOKEN,
                    ConfigOperator.NODE_ID
            );
        } else {
            Debugger.debugOperation("Using LocalKeyProvider (H2 Database).");
            PROVIDER = new LocalKeyProvider();
        }
        PROVIDER.init();
    }

    public static void initKeyDatabase() {
        Debugger.debugOperation("Initializing Key Database (sk table)...");
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
                    Debugger.debugOperation("Table 'sk' check/creation completed.");
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

    // 必须添加 throws 声明，将异常向上传递给调用者 (例如 Server 握手层)
    public static SequenceKey getKeyFromDB(String name)
            throws PortOccupiedException, NoMorePortException, UnRecognizedKeyException, OutDatedKeyException {

        if (name == null) return null;
        if (PROVIDER instanceof LocalKeyProvider) {
            // 本地模式：获取对象后，由上层调用者调用 isOutOfDate() 检查
            return keyCache.get(name);
        }

        // 远程模式：getKey 内部直接抛出异常
        if (PROVIDER != null) {
            SequenceKey key = PROVIDER.getKey(name);
            if (key != null) keyCache.put(name, key);
            return key;
        }
        return null;
    }

    public static SequenceKey getEnabledKeyFromDB(String name) throws PortOccupiedException, NoMorePortException, UnRecognizedKeyException, OutDatedKeyException {
        SequenceKey key = getKeyFromDB(name);
        if (key != null && key.isEnable()) return key;
        if (key != null) Debugger.debugOperation("Key exists but disabled: " + name);
        return null;
    }

    public static void releaseKey(String name) {
        Debugger.debugOperation("Releasing key: " + name);
        if (name != null && PROVIDER != null) {
            PROVIDER.releaseKey(name);
        }
    }

    public static SequenceKey loadKeyFromDatabase(String name, boolean onlyEnabled) {
        Debugger.debugOperation("Loading key directly from DB: " + name + " (onlyEnabled=" + onlyEnabled + ")");
        try {
            String sql = "SELECT * FROM sk WHERE name = ?" + (onlyEnabled ? " AND isEnable = TRUE" : "");
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Debugger.debugOperation("Key found in DB: " + name);
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
        Debugger.debugOperation("Key not found in DB: " + name);
        return null;
    }

    public static boolean saveToDB(SequenceKey sequenceKey) {
        if (sequenceKey == null) return false;
        // Debugger.debugOperation("Saving key to DB: " + sequenceKey.getName());
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
                boolean result = stmt.executeUpdate() > 0;
                // Debugger.debugOperation("Save result for " + sequenceKey.getName() + ": " + result);
                return result;
            } catch (Exception e) {
                debugOperation(e);
                return false;
            }
        } finally {
            sequenceKey.lock.unlock();
        }
    }

    public static boolean createNewKey(String name, double balance, String expireTime, String portStr, double rate) {
        Debugger.debugOperation("Creating new key: " + name + " Port: " + portStr);
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
        Debugger.debugOperation("Removing key: " + name);
        keyCache.remove(name);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM sk WHERE name = ?")) {
            stmt.setString(1, name);
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

    public static void updateBalanceInDB(String name, double mib) {
        // Debugger.debugOperation("Updating balance in DB for: " + name + " decreasing by " + mib);
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
        Debugger.debugOperation("Enabling key: " + name);
        SequenceKey key = keyCache.get(name);
        if (key != null) key.setEnable(true);
        return updateKeyStatusInDB(name, true);
    }

    public static boolean disableKey(String name) {
        Debugger.debugOperation("Disabling key: " + name);
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

    public void refreshFrom(SequenceKey freshKey) {
        if (freshKey == null) return;
        lock.lock();
        try {
            Debugger.debugOperation("Refreshing key data for: " + this.name);
            this.balance = freshKey.balance;
            this.isEnable = freshKey.isEnable;
            this.enableWebHTML = freshKey.enableWebHTML;
            this.rate = freshKey.rate;

            if (!String.valueOf(this.expireTime).equals(freshKey.expireTime)) {
                this.expireTime = freshKey.expireTime;
                updateExpireTimestamp(this.expireTime);
            }
            if (!String.valueOf(this.port).equals(freshKey.port)) {
                this.port = freshKey.port;
            }
        } finally {
            lock.unlock();
        }
    }

    public void mineMib(String sourceSubject, double mib) throws NoMoreNetworkFlowException {
        if (mib <= 0) return;
        lock.lock();
        try {
            if (isOutOfDate()) {
                Debugger.debugOperation("Key expired during flow mining: " + name);
                // EXCEPTION KEY: exception.keyOutOfDateForFlow
                NoMoreNetworkFlowException.throwException("SK-Manager", "exception.keyOutOfDateForFlow", name);
            }
            if (!isEnable) {
                Debugger.debugOperation("Key disabled during flow mining: " + name);
                // EXCEPTION KEY: exception.keyDisabled
                NoMoreNetworkFlowException.throwException("SK-Manager", "exception.keyDisabled", name);
            }

            this.balance -= mib;
            // Debugger.debugOperation("Mined " + mib + " MB from " + name + ". Remaining: " + this.balance);

            if (this.balance <= 0) {
                if (PROVIDER instanceof LocalKeyProvider) {
                    this.balance = 0;
                    Debugger.debugOperation("Insufficient balance for: " + name);
                    // EXCEPTION KEY: exception.insufficientBalance
                    NoMoreNetworkFlowException.throwException(sourceSubject, "exception.insufficientBalance", name);
                }
            }
        } finally {
            lock.unlock();
        }

        if (PROVIDER != null) {
            PROVIDER.consumeFlow(this.name, mib);
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