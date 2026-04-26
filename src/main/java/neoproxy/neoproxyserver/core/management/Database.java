package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.core.Debugger;
import neoproxy.neoproxyserver.core.ServerLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 核心数据库管理类 (SQLite 版)
 * 负责 sk 表的持久化存储，采用 WAL 模式高性能读写。
 * [新增] 支持 reload() 重载连接
 */
public class Database {
    private static final String DB_DRIVER = "org.sqlite.JDBC";
    private static final String DB_URL = "jdbc:sqlite:./sk";

    // 保持一个连接以锁定 WAL 共享内存，减少 IO 开销
    private static Connection keepAliveConn;

    public static void init() {
        Debugger.debugOperation("Initializing SQLite Database (sk)...");
        try {
            Class.forName(DB_DRIVER);
            connectAndConfigure();
            ServerLogger.info("Database initialized (SQLite WAL Mode).");
        } catch (Exception e) {
            ServerLogger.error("db.initFailed", e, e.getMessage());
            Debugger.debugOperation(e);
            throw new RuntimeException("Database init failed", e);
        }
    }

    /**
     * [新增] 彻底重载数据库连接
     * 用于在 reload 命令时确保数据库连接状态刷新（例如文件被外部替换后）
     */
    public static synchronized void reload() {
        Debugger.debugOperation("Reloading Database connection...");
        // 1. 尝试关闭旧的保活连接
        try {
            if (keepAliveConn != null && !keepAliveConn.isClosed()) {
                keepAliveConn.close();
            }
        } catch (SQLException ignored) {
        }

        // 2. 重新建立连接并配置环境
        try {
            connectAndConfigure();
            ServerLogger.info("Database connection reloaded.");
        } catch (Exception e) {
            ServerLogger.error("db.initFailed", e, "Reload failed");
        }
    }

    /**
     * [重构] 提取连接配置逻辑，供 init 和 reload 复用
     */
    private static void connectAndConfigure() throws SQLException {
        // 1. 建立连接
        keepAliveConn = DriverManager.getConnection(DB_URL);

        // 2. 配置核心性能参数
        try (Statement stmt = keepAliveConn.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");   // 开启 WAL，读写并发
            stmt.execute("PRAGMA synchronous = NORMAL;");  // 优化磁盘同步
            stmt.execute("PRAGMA busy_timeout = 5000;");   // 防止锁超时
            stmt.execute("PRAGMA temp_store = MEMORY;");   // 临时文件存内存
        }

        // 3. 确保表结构存在 (防止用户误删文件后 reload 报错)
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // SQLite 兼容性建表 (0/1 代替 Boolean)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sk (
                        name VARCHAR(50) PRIMARY KEY,
                        balance DOUBLE NOT NULL,
                        expireTime VARCHAR(50) NOT NULL,
                        port VARCHAR(50) NOT NULL, 
                        rate DOUBLE NOT NULL,
                        isEnable BOOLEAN DEFAULT 1 NOT NULL,
                        enableWebHTML BOOLEAN DEFAULT 0 NOT NULL
                    )
                    """);

            // 热更新字段 (兼容旧版本升级)
            safeAddColumn(stmt, "sk", "isEnable", "BOOLEAN DEFAULT 1");
            safeAddColumn(stmt, "sk", "enableWebHTML", "BOOLEAN DEFAULT 0");
        }
    }

    private static Connection getConnection() throws SQLException {
        // SQLite 连接创建极快
        return DriverManager.getConnection(DB_URL);
    }

    private static void safeAddColumn(Statement stmt, String table, String col, String def) {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + col + " " + def);
        } catch (SQLException ignored) {
            // 列已存在，忽略
        }
    }

    // ==================== 数据操作方法 (DAO) ====================

    public static SequenceKey getKey(String name, boolean onlyEnabled) {
        String sql = "SELECT * FROM sk WHERE name = ?" + (onlyEnabled ? " AND isEnable = 1" : "");
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
        } catch (Exception e) {
            Debugger.debugOperation(e);
        }
        return null;
    }

    public static boolean exists(String name) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM sk WHERE name = ? LIMIT 1")) {
            stmt.setString(1, name);
            return stmt.executeQuery().next();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 插入或更新 Key (Upsert)
     * SQLite 使用 REPLACE INTO 实现 Insert or Update
     */
    public static boolean saveKey(SequenceKey key) {
        // REPLACE INTO: 如果主键存在则删除旧记录插入新记录，适合全量更新
        String sql = "REPLACE INTO sk (name, balance, expireTime, port, rate, isEnable, enableWebHTML) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key.getName());
            stmt.setDouble(2, key.getBalanceNoLock());
            stmt.setString(3, key.getExpireTime());
            stmt.setString(4, key.getPortStr());
            stmt.setDouble(5, key.getRateNoLock());
            // 【修复】外层 saveToDB() 已持有 sequenceKey.lock，此处必须使用 NoLock 版本
            // 避免与 isEnable()/isHTMLEnabled() 的加锁版本产生 ReentrantLock 重入
            // 虽然重入在语义上是正确的，但统一使用 NoLock 可明确表达“调用方已持锁”的契约
            stmt.setBoolean(6, key.isEnableNoLock());
            stmt.setBoolean(7, key.isHTMLEnabledNoLock());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            Debugger.debugOperation(e);
            return false;
        }
    }

    public static boolean createKey(String name, double balance, String expireTime, String portStr, double rate) {
        String sql = "INSERT INTO sk (name, balance, expireTime, port, rate, isEnable, enableWebHTML) VALUES (?, ?, ?, ?, ?, 1, 0)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setDouble(2, balance);
            stmt.setString(3, expireTime);
            stmt.setString(4, portStr);
            stmt.setDouble(5, rate);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            Debugger.debugOperation(e);
            return false;
        }
    }

    public static boolean deleteKey(String name) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM sk WHERE name = ?")) {
            stmt.setString(1, name);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            Debugger.debugOperation(e);
            return false;
        }
    }

    public static void updateBalance(String name, double amountToDeduct) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE sk SET balance = balance - ? WHERE name = ?")) {
            stmt.setDouble(1, amountToDeduct);
            stmt.setString(2, name);
            stmt.executeUpdate();
        } catch (Exception e) {
            Debugger.debugOperation(e);
        }
    }

    public static boolean updateStatus(String name, boolean isEnable) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE sk SET isEnable = ? WHERE name = ?")) {
            stmt.setBoolean(1, isEnable);
            stmt.setString(2, name);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            Debugger.debugOperation(e);
            return false;
        }
    }

    public static List<SequenceKey> getAllKeys() {
        List<SequenceKey> list = new ArrayList<>();
        String sql = "SELECT * FROM sk ORDER BY name ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new SequenceKey(
                        rs.getString("name"),
                        rs.getDouble("balance"),
                        rs.getString("expireTime"),
                        rs.getString("port"),
                        rs.getDouble("rate"),
                        rs.getBoolean("isEnable"),
                        rs.getBoolean("enableWebHTML")
                ));
            }
        } catch (Exception e) {
            Debugger.debugOperation(e);
        }
        return list;
    }
}