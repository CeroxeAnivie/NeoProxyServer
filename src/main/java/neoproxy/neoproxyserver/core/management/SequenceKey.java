package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.core.ConfigOperator;
import neoproxy.neoproxyserver.core.Debugger;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.exceptions.*;
import neoproxy.neoproxyserver.core.management.provider.KeyDataProvider;
import neoproxy.neoproxyserver.core.management.provider.LocalKeyProvider;
import neoproxy.neoproxyserver.core.management.provider.RemoteKeyProvider;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * SequenceKey - 访问密钥实体类
 *
 * <p>表示一个内网穿透访问密钥，包含密钥的所有属性：</p>
 * <ul>
 *   <li>密钥名称（唯一标识）</li>
 *   <li>流量余额（MiB）</li>
 *   <li>过期时间</li>
 *   <li>端口配置</li>
 *   <li>速率限制</li>
 *   <li>启用状态</li>
 *   <li>Web HTML 访问权限</li>
 * </ul>
 *
 * <p>线程安全：此类使用 ReentrantLock 保护流量扣减操作，
 * 使用 volatile 保证字段可见性。</p>
 *
 * <p>数据持久化：通过 KeyDataProvider 实现数据的加载和保存，
 * 支持本地文件和远程数据库两种模式。</p>
 *
 * @author Ceroxe
 * @version 6.1.0
 * @see KeyDataProvider
 * @since 6.1.0
 */
public class SequenceKey {
    public static final int DYNAMIC_PORT = -1;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd-HH:mm");

    // 【优化】预编译正则，避免每次调用 getPort() 都重新编译
    private static final Pattern DIGIT_ONLY_PATTERN = Pattern.compile("^\\d+$");

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

    // [新增] 获取当前的 Provider 实例，供 UpdateManager 调用
    public static KeyDataProvider getKeyDataProvider() {
        return PROVIDER;
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
                ServerLogger.error("sequenceKey.providerShutdownError", e, e.getMessage());
                Debugger.debugOperation(e);
            }
        }

        keyCache.clear();
        initProvider();

        String type = (PROVIDER instanceof RemoteKeyProvider) ? "REMOTE (NKM)" : "LOCAL (SQLite)";
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
            Debugger.debugOperation("Using LocalKeyProvider (SQLite Database).");
            PROVIDER = new LocalKeyProvider();
        }
        PROVIDER.init();
    }

    public static void initKeyDatabase() {
        // [Refactor] 委托给 Database 类处理
        Database.init();
    }

    // 必须添加 throws 声明，将异常向上传递给调用者 (例如 Server 握手层)
    public static SequenceKey getKeyFromDB(String name)
            throws PortOccupiedException, NoMorePortException, UnRecognizedKeyException, OutDatedKeyException {

        if (name == null) return null;
        if (PROVIDER instanceof LocalKeyProvider) {
            // 本地模式必须复用同一个内存对象，否则扣费线程和落盘线程会读写两份余额快照。
            SequenceKey cached = keyCache.get(name);
            if (cached != null) return cached;
            SequenceKey key = loadKeyFromDatabase(name, false);
            if (key != null) keyCache.put(name, key);
            return key;
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
        if (key == null) {
            return null;
        }
        if (!key.isEnable()) {
            Debugger.debugOperation("Key exists but disabled: " + name);
            return null;
        }
        if (key.isOutOfDate()) {
            OutDatedKeyException.throwException(name);
        }
        return key;
    }

    public static void releaseKey(String name) {
        Debugger.debugOperation("Releasing key: " + name);
        if (name != null && PROVIDER != null) {
            PROVIDER.releaseKey(name);
        }
    }

    public static SequenceKey loadKeyFromDatabase(String name, boolean onlyEnabled) {
        Debugger.debugOperation("Loading key directly from DB: " + name + " (onlyEnabled=" + onlyEnabled + ")");
        // [Refactor] 调用 Database
        SequenceKey key = Database.getKey(name, onlyEnabled);
        if (key != null) {
            Debugger.debugOperation("Key found in DB: " + name);
            return key;
        }
        Debugger.debugOperation("Key not found in DB: " + name);
        return null;
    }

    public static boolean saveToDB(SequenceKey sequenceKey) {
        if (sequenceKey == null) return false;
        sequenceKey.lock.lock();
        try {
            // [Refactor] 调用 Database
            return Database.saveKey(sequenceKey);
        } finally {
            sequenceKey.lock.unlock();
        }
    }

    public static boolean createNewKey(String name, double balance, String expireTime, String portStr, double rate) {
        Debugger.debugOperation("Creating new key: " + name + " Port: " + portStr);
        if (name == null) return false;
        // [Refactor] 调用 Database
        return Database.createKey(name, balance, expireTime, portStr, rate);
    }

    public static boolean removeKey(String name) {
        Debugger.debugOperation("Removing key: " + name);
        keyCache.remove(name);
        // [Refactor] 调用 Database
        return Database.deleteKey(name);
    }

    public static boolean isKeyExistsByName(String name) {
        if (keyCache.containsKey(name)) return true;
        // [Refactor] 调用 Database
        return Database.exists(name);
    }

    public static void updateBalanceInDB(String name, double mib) {
        // [Refactor] 调用 Database
        Database.updateBalance(name, mib);
    }

    public static boolean enableKey(String name) {
        Debugger.debugOperation("Enabling key: " + name);
        SequenceKey key = keyCache.get(name);
        if (key != null) key.setEnable(true);
        // [Refactor] 调用 Database
        return Database.updateStatus(name, true);
    }

    public static boolean disableKey(String name) {
        Debugger.debugOperation("Disabling key: " + name);
        SequenceKey key = keyCache.get(name);
        if (key != null) key.setEnable(false);
        // [Refactor] 调用 Database
        return Database.updateStatus(name, false);
    }

    // ================== 下方业务逻辑保持不变 ==================

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
                NoMoreNetworkFlowException.throwException("SK-Manager", "exception.keyOutOfDateForFlow", name);
            }
            if (!isEnable) {
                Debugger.debugOperation("Key disabled during flow mining: " + name);
                NoMoreNetworkFlowException.throwException("SK-Manager", "exception.keyDisabled", name);
            }

            this.balance -= mib;

            if (this.balance <= 0) {
                if (PROVIDER instanceof LocalKeyProvider) {
                    this.balance = 0;
                    Debugger.debugOperation("Insufficient balance for: " + name);
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

    /**
     * 不加锁版本 — 仅允许在已持有 sequenceKey.lock 的上下文中调用（如 saveToDB）
     */
    public boolean isEnableNoLock() {
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
        lock.lock();
        try {
            return enableWebHTML;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 不加锁版本 — 仅允许在已持有 sequenceKey.lock 的上下文中调用（如 saveToDB）
     */
    public boolean isHTMLEnabledNoLock() {
        return enableWebHTML;
    }

    public void setHTMLEnabled(boolean enable) {
        lock.lock();
        try {
            this.enableWebHTML = enable;
        } finally {
            lock.unlock();
        }
    }

    // 新增：获取原始端口字符串，供 Database 类使用
    public String getPortStr() {
        lock.lock();
        try {
            return port;
        } finally {
            lock.unlock();
        }
    }

    public int getPort() {
        lock.lock();
        try {
            String p = this.port;
            if (p == null) return DYNAMIC_PORT;
            if (DIGIT_ONLY_PATTERN.matcher(p).matches()) {
                try {
                    return Integer.parseInt(p);
                } catch (Exception e) {
                    return DYNAMIC_PORT;
                }
            }
            return DYNAMIC_PORT;
        } finally {
            lock.unlock();
        }
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
