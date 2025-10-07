package neoproject.neoproxy.core;

import plethora.utils.MyConsole;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static neoproject.neoproxy.NeoProxyServer.*;
import static neoproject.neoproxy.core.SequenceKey.*;

/**
 * 控制台管理器，负责注册和处理所有控制台命令。
 * <p>
 * 所有命令处理逻辑均会捕获内部异常，并通过 {@link neoproject.neoproxy.NeoProxyServer#debugOperation} 记录，
 * 不会因命令错误导致整个应用崩溃。
 */
public class ConsoleManager {

    private static final String TIME_FORMAT_PATTERN = "^(\\d{4})/(\\d{1,2})/(\\d{1,2})-(\\d{1,2}):(\\d{1,2})$";
    private static final Pattern TIME_PATTERN = Pattern.compile(TIME_FORMAT_PATTERN);

    /**
     * 初始化控制台并注册所有命令。
     */
    public static void init() {
        try {
            myConsole = new MyConsole("NeoProxyServer");
            initCommand();
            myConsole.start();
        } catch (Exception e) {
            // 在生产环境中，应记录异常并尝试优雅降级，而非直接退出
            debugOperation(e);
            // 此处可以选择让应用继续运行（无控制台）或以其他方式通知管理员
            // 为保持与原逻辑一致，此处仅记录，不退出
        }
    }

    private static void initCommand() {
        // 注册一个回显命令
        myConsole.registerCommand(null, "不存在的指令", (List<String> params) -> {
            myConsole.warn("Admin", "不存在的指令，输入 help 获取帮助。");
        });

        myConsole.registerCommand("key", "序列号管理", (List<String> params) -> {
            try {
                if (params.isEmpty()) {
                    printKeyUsage();
                    return;
                }

                String subCommand = params.getFirst();
                switch (subCommand) {
                    case "add" -> handleAddCommand(params);
                    case "del" -> handleDelCommand(params);
                    case "list" -> handleListCommand(params);
                    case "lp" -> handleLookupCommand(params);
                    case "set" -> handleSetCommand(params);
                    default -> printKeyUsage();
                }
            } catch (Exception e) {
                debugOperation(e);
                myConsole.error("Admin", "执行 key 命令时发生内部错误，请查看日志。");
            }
        });
    }

    // ==================== 命令处理方法 ====================

    private static void handleSetCommand(List<String> params) {
        if (params.size() != 6) {
            myConsole.warn("Admin", "Usage: key set <name> <balance> <expireTime> <port> <rate>");
            return;
        }

        String name = params.get(1);
        String balanceStr = params.get(2);
        String expireTimeInput = params.get(3);
        String portStr = params.get(4);
        String rateStr = params.get(5);

        // 1. 校验并格式化时间
        String expireTime = correctInputTime(expireTimeInput);
        if (expireTime == null) {
            myConsole.warn("Admin", "Illegal time input.");
            return;
        }
        if (isOutOfDate(expireTime)) {
            myConsole.warn("Admin", "The entered time cannot be later than the current time.");
            return;
        }

        // 2. 校验并解析数字
        Double balance = parseDoubleSafely(balanceStr, "balance");
        Integer port = parseIntegerSafely(portStr, "port");
        Double rate = parseDoubleSafely(rateStr, "rate");
        if (balance == null || port == null || rate == null) {
            return; // 错误信息已在解析方法中打印
        }

        // 3. 执行设置操作
        SequenceKey sequenceKey = null;
        for (HostClient hostClient : availableHostClient) {//优先从当前正在使用的 key 中寻找
            if (hostClient.getKey().getName().equals(name)) {
                sequenceKey = hostClient.getKey();
            }
        }
        if (sequenceKey == null) {//如果 key 本身还没被使用，就从数据库找
            sequenceKey = getKeyFromDB(name);
            if (sequenceKey == null) {//如果全都找不到
                myConsole.warn("Admin", "Could not find the key in database.");
                return;
            }
        }

        sequenceKey.balance = balance;
        sequenceKey.expireTime = expireTime;
        sequenceKey.port = port;
        sequenceKey.rate = rate;

        boolean isSuccess = saveToDB(sequenceKey);
        if (isSuccess) {
            myConsole.log("Admin", "Operation complete!");
        } else {
            myConsole.warn("Admin", "Fail to set the key.");
        }
    }

    private static void handleLookupCommand(List<String> params) {
        if (params.size() != 2) {
            myConsole.warn("Admin", "Usage: key lp <name>");
            return;
        }

        String name = params.get(1);
        SequenceKey sequenceKey = getKeyFromDB(name);
        if (sequenceKey != null) {
            int clientNum = findKeyClientNum(name);
            String formattedRate = killDoubleEndZero(sequenceKey.getRate());
            myConsole.log("Admin", String.format(
                    "\nname: %s balance: %s \nexpireTime: %s port: %d\nrate= %s mbps %d HostClient Active",
                    sequenceKey.getName(),
                    sequenceKey.getBalance(),
                    sequenceKey.getExpireTime(),
                    sequenceKey.getPort(),
                    formattedRate,
                    clientNum
            ));
        } else {
            myConsole.warn("Admin", "No such key...");
        }
    }

    private static void handleListCommand(List<String> params) {
        if (params.size() == 1) {
            listAllKeys();
        } else if (params.size() == 2) {
            String listType = params.get(1);
            switch (listType) {
                case "name" -> listKeyNames();
                case "balance" -> listKeyBalances();
                case "rate" -> listKeyRates();
                case "expire-time" -> listKeyExpireTimes();
                default -> myConsole.warn("Admin", "Usage: key list | key list <name | balance | rate | expire-time>");
            }
        } else {
            myConsole.warn("Admin", "Usage: key list | key list <name | balance | rate | expire-time>");
        }
    }

    private static void handleDelCommand(List<String> params) {
        if (params.size() != 2) {
            myConsole.warn("Admin", "Usage: key del <name>");
            return;
        }

        String name = params.get(1);
        if (removeKey(name)) {
            myConsole.log("Admin", "Key " + name + " has been deleted!");
        } else {
            myConsole.warn("Admin", "No such key or failed to delete.");
        }
    }

    private static void handleAddCommand(List<String> params) {
        if (params.size() != 6) {
            myConsole.warn("Admin", "Usage: key add <name> <balance> <expireTime> <port> <rate>");
            return;
        }

        String name = params.get(1);
        String balanceStr = params.get(2);
        String expireTimeInput = params.get(3);
        String portStr = params.get(4);
        String rateStr = params.get(5);

        // 1. 校验并格式化时间
        String expireTime = correctInputTime(expireTimeInput);
        if (expireTime == null) {
            myConsole.warn("Admin", "Illegal time input.");
            return;
        }
        if (isOutOfDate(expireTime)) {
            myConsole.warn("Admin", "The entered time cannot be later than the current time.");
            return;
        }

        // 2. 校验并解析数字
        Double balance = parseDoubleSafely(balanceStr, "balance");
        Integer port = parseIntegerSafely(portStr, "port");
        Double rate = parseDoubleSafely(rateStr, "rate");
        if (balance == null || port == null || rate == null) {
            return; // 错误信息已在解析方法中打印
        }

        // 3. 执行创建操作
        boolean isCreated = createNewKey(name, balance, expireTime, port, rate);
        if (isCreated) {
            myConsole.log("Admin", "Key " + name + " has been created!");
        } else {
            myConsole.error("Admin", "Failed to create key.");
            myConsole.warn("Admin", "The key might already exist or an internal error occurred.");
        }
    }

    // ==================== 列表查询的具体实现 ====================

    private static void listAllKeys() {
        String sql = "SELECT name, balance, expireTime, port, rate FROM sk";
        executeQueryAndPrint(sql, rs -> {
            String name = rs.getString("name");
            double balance = rs.getDouble("balance");
            String expireTime = rs.getString("expireTime");
            int port = rs.getInt("port");
            double rate = rs.getDouble("rate");
            int clientNum = findKeyClientNum(name);
            String formattedRate = killDoubleEndZero(rate);
            return String.format(
                    "\nname: %s balance: %s \nexpireTime: %s port: %d\nrate= %s mbps %d HostClient Active",
                    name, balance, expireTime, port, formattedRate, clientNum
            );
        });
    }

    private static void listKeyNames() {
        executeQueryAndPrint("SELECT name FROM sk", rs -> {
            String name = rs.getString("name");
            int clientNum = findKeyClientNum(name);
            return String.format("%s(%d)", name, clientNum);
        }, " ");
    }

    private static void listKeyBalances() {
        executeQueryAndPrint("SELECT name, balance FROM sk", rs ->
                String.format("%s(%.2f)", rs.getString("name"), rs.getDouble("balance")), "\n");
    }

    private static void listKeyRates() {
        executeQueryAndPrint("SELECT name, rate FROM sk", rs -> {
            String name = rs.getString("name");
            double rate = rs.getDouble("rate");
            String formattedRate = killDoubleEndZero(rate);
            return String.format("%s(%s)", name, formattedRate);
        }, " ");
    }

    private static void listKeyExpireTimes() {
        executeQueryAndPrint("SELECT name, expireTime FROM sk", rs ->
                String.format("%s( %s )", rs.getString("name"), rs.getString("expireTime")), "\n");
    }

    // ==================== 辅助方法 ====================

    private static void printKeyUsage() {
        myConsole.warn("Admin", """
                Usage:
                key add <name> <balance> <expireTime> <port> <rate>
                key set <name> <balance> <expireTime> <port> <rate>
                key del <name>
                key list
                key list <name | balance | rate | expire-time>
                key lp <name>""");
    }

    /**
     * 安全地解析 Double，捕获 NumberFormatException 并输出友好错误。
     */
    private static Double parseDoubleSafely(String str, String fieldName) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            myConsole.warn("Admin", "Invalid value for " + fieldName + ": '" + str + "'");
            return null;
        }
    }

    /**
     * 安全地解析 Integer，捕获 NumberFormatException 并输出友好错误。
     */
    private static Integer parseIntegerSafely(String str, String fieldName) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            myConsole.warn("Admin", "Invalid value for " + fieldName + ": '" + str + "'");
            return null;
        }
    }

    /**
     * 执行查询并根据处理器打印结果。
     *
     * @param sql          SQL 查询语句
     * @param rowProcessor 处理每一行数据的函数
     */
    private static void executeQueryAndPrint(String sql, RowProcessor rowProcessor) {
        executeQueryAndPrint(sql, rowProcessor, "\n");
    }

    /**
     * 执行查询并根据处理器打印结果。
     *
     * @param sql          SQL 查询语句
     * @param rowProcessor 处理每一行数据的函数
     * @param separator    行之间的分隔符
     */
    private static void executeQueryAndPrint(String sql, RowProcessor rowProcessor, String separator) {
        try (var conn = getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {

            StringBuilder output = new StringBuilder();
            boolean hasData = false;
            while (rs.next()) {
                hasData = true;
                output.append(rowProcessor.process(rs)).append(separator);
            }

            if (hasData) {
                // 移除末尾多余的分隔符
                if (output.length() > 0) {
                    output.setLength(output.length() - separator.length());
                }
                myConsole.log("Admin", output.toString());
            } else {
                myConsole.warn("Admin", "No key found.");
            }
        } catch (Exception e) {
            debugOperation(e);
            myConsole.error("Admin", "Failed to query the database.");
        }
    }

    @FunctionalInterface
    private interface RowProcessor {
        String process(java.sql.ResultSet rs) throws java.sql.SQLException;
    }

    // ==================== 以下方法直接委托给 SequenceKey，保持一致性 ====================

    private static int findKeyClientNum(String name) {
        int num = 0;
        for (HostClient hostClient : availableHostClient) {
            if (hostClient != null && hostClient.getKey() != null &&
                    name.equals(hostClient.getKey().getName())) {
                num++;
            }
        }
        return num;
    }

    private static String killDoubleEndZero(double d) {
        if (d == (long) d) {
            return String.valueOf((long) d);
        } else {
            return String.valueOf(d);
        }
    }

    private static String correctInputTime(String time) {
        if (time == null) return null;

        Matcher matcher = TIME_PATTERN.matcher(time);
        if (!matcher.matches()) {
            return null;
        }

        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            int hour = Integer.parseInt(matcher.group(4));
            int minute = Integer.parseInt(matcher.group(5));

            if (month < 1 || month > 12 || day < 1 || day > 31 ||
                    hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }

            return String.format("%04d/%02d/%02d-%02d:%02d", year, month, day, hour, minute);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}