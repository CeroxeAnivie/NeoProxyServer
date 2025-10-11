package neoproject.neoproxy.core;

import plethora.utils.MyConsole;

import java.util.ArrayList;
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
        myConsole.registerCommand(null, "不存在的指令", (List<String> params) -> {
            myConsole.warn("Admin", "不存在的指令，输入 help 获取帮助。");
        });

        myConsole.registerCommand("alert", "设置是否开启积极的控制台输出", (List<String> params) -> {
            if (params.size() != 1) {
                myConsole.warn("Admin", "Usage : alert <enable|disable>");
                return;
            }
            String subCommand = params.getFirst();
            switch (subCommand) {
                case "enable" -> InfoBox.alert = true;
                case "disable" -> InfoBox.alert = false;
                default -> myConsole.warn("Admin", "Usage : alert <enable|disable>");
            }
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
                    case "enable" -> handleEnableCommand(params);
                    case "disable" -> handleDisableCommand(params);
                    default -> printKeyUsage();
                }
            } catch (Exception e) {
                debugOperation(e);
                myConsole.error("Admin", "执行 key 命令时发生内部错误，请查看日志。");
            }
        });
    }

    // ==================== 新增的启用/禁用命令 ====================

    private static void handleEnableCommand(List<String> params) {
        if (params.size() != 2) {
            myConsole.warn("Admin", "Usage: key enable <name>");
            return;
        }

        String name = params.get(1);
        if (!isKeyExistsByName(name)) {
            myConsole.warn("Admin", "No such key...");
            return;
        }

        if (enableKey(name)) {
            myConsole.log("Admin", "Key " + name + " has been enabled!");
        } else {
            myConsole.warn("Admin", "Failed to enable key.");
        }
    }

    private static void handleDisableCommand(List<String> params) {
        if (params.size() != 2) {
            myConsole.warn("Admin", "Usage: key disable <name>");
            return;
        }

        String name = params.get(1);
        if (!isKeyExistsByName(name)) {
            myConsole.warn("Admin", "No such key...");
            return;
        }

        if (disableKey(name)) {
            myConsole.log("Admin", "Key " + name + " has been disabled!");
        } else {
            myConsole.warn("Admin", "Failed to disable key.");
        }
    }

    // ==================== 命令处理方法 ====================

    private static void handleSetCommand(List<String> params) {
        if (params.size() < 2) {
            myConsole.warn("Admin", "Usage: key set <name> [b=<balance>] [r=<rate>] [p=<port>] [t=<expireTime>]");
            myConsole.warn("Admin", "Example: key set mykey b=1000 r=15 p=8080 t=2025/12/31-23:59");
            return;
        }

        String name = params.get(1);

        // 检查密钥是否存在
        if (!isKeyExistsByName(name)) {
            myConsole.warn("Admin", "No such key: " + name);
            return;
        }

        // 获取当前密钥对象（优先从内存，再从数据库）
        SequenceKey sequenceKey = null;
        for (HostClient hostClient : availableHostClient) {
            if (hostClient != null && hostClient.getKey() != null &&
                    name.equals(hostClient.getKey().getName())) {
                sequenceKey = hostClient.getKey();
                break;
            }
        }
        if (sequenceKey == null) {
            sequenceKey = getKeyFromDB(name);
            if (sequenceKey == null) {
                myConsole.warn("Admin", "Could not find the key in database.");
                return;
            }
        }

        // 解析参数并更新对应字段
        boolean hasUpdate = false;
        for (int i = 2; i < params.size(); i++) {
            String param = params.get(i);
            if (param.startsWith("b=")) {
                String balanceStr = param.substring(2);
                Double balance = parseDoubleSafely(balanceStr, "balance");
                if (balance != null) {
                    sequenceKey.balance = balance;
                    hasUpdate = true;
                }
            } else if (param.startsWith("r=")) {
                String rateStr = param.substring(2);
                Double rate = parseDoubleSafely(rateStr, "rate");
                if (rate != null) {
                    sequenceKey.rate = rate;
                    hasUpdate = true;
                }
            } else if (param.startsWith("p=")) {
                String portStr = param.substring(2);
                Integer port = parseIntegerSafely(portStr, "port");
                if (port != null && port > 0 && port <= 65535) {
                    sequenceKey.port = port;
                    hasUpdate = true;
                } else if (port != null) {
                    myConsole.warn("Admin", "Invalid port value: " + portStr + " (must be 1-65535)");
                }
            } else if (param.startsWith("t=")) {
                String expireTimeInput = param.substring(2);
                String expireTime = correctInputTime(expireTimeInput);
                if (expireTime == null) {
                    myConsole.warn("Admin", "Illegal time input: " + expireTimeInput);
                } else if (isOutOfDate(expireTime)) {
                    myConsole.warn("Admin", "The entered time cannot be earlier than the current time: " + expireTime);
                } else {
                    sequenceKey.expireTime = expireTime;
                    hasUpdate = true;
                }
            } else {
                myConsole.warn("Admin", "Unknown parameter: " + param + " (use b=, r=, p=, t=)");
            }
        }

        if (!hasUpdate) {
            myConsole.warn("Admin", "No valid parameters provided. Use b=<balance>, r=<rate>, p=<port>, t=<expireTime>");
            return;
        }

        // 保存到数据库
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
            // 使用表格格式输出单个密钥信息
            outputSingleKeyAsTable(sequenceKey);
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
                case "enable" -> listKeyEnableStatus();
                default ->
                        myConsole.warn("Admin", "Usage: key list | key list <name | balance | rate | expire-time | enable>");
            }
        } else {
            myConsole.warn("Admin", "Usage: key list | key list <name | balance | rate | expire-time | enable>");
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
        String sql = "SELECT name, balance, expireTime, port, rate, isEnable FROM sk";
        try (var conn = getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {

            // 收集所有数据
            List<String[]> rows = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString("name");
                double balance = rs.getDouble("balance");
                String expireTime = rs.getString("expireTime");
                int port = rs.getInt("port");
                double rate = rs.getDouble("rate");
                boolean isEnable = rs.getBoolean("isEnable");
                int clientNum = findKeyClientNum(name);
                String formattedRate = killDoubleEndZero(rate);
                String enableStatus = isEnable ? "✓" : "✗";
                rows.add(new String[]{
                        name,
                        String.format("%.2f", balance),
                        expireTime,
                        String.valueOf(port),
                        formattedRate + "mbps",
                        enableStatus,
                        String.valueOf(clientNum)
                });
            }

            if (rows.isEmpty()) {
                myConsole.warn("Admin", "No key found.");
                return;
            }

            // 定义表头
            String[] headers = {"Name", "Balance", "ExpireTime", "Port", "Rate", "Enable", "Clients"};

            // 计算每列的最大宽度
            int[] widths = new int[headers.length];
            for (int i = 0; i < headers.length; i++) {
                widths[i] = headers[i].length();
            }

            for (String[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    widths[i] = Math.max(widths[i], row[i].length());
                }
            }

            // 构建输出
            StringBuilder output = new StringBuilder();
            output.append("\n");

            // 添加表头分隔线
            output.append("┌");
            for (int i = 0; i < widths.length; i++) {
                output.append("─".repeat(widths[i] + 2));
                if (i < widths.length - 1) output.append("┬");
            }
            output.append("┐\n");

            // 添加表头
            output.append("│");
            for (int i = 0; i < headers.length; i++) {
                output.append(" ").append(String.format("%-" + widths[i] + "s", headers[i])).append(" ");
                output.append("│");
            }
            output.append("\n");

            // 添加表头和数据分隔线
            output.append("├");
            for (int i = 0; i < widths.length; i++) {
                output.append("─".repeat(widths[i] + 2));
                if (i < widths.length - 1) output.append("┼");
            }
            output.append("┤\n");

            // 添加数据行
            for (String[] row : rows) {
                output.append("│");
                for (int i = 0; i < row.length; i++) {
                    output.append(" ").append(String.format("%-" + widths[i] + "s", row[i])).append(" ");
                    output.append("│");
                }
                output.append("\n");
            }

            // 添加底部边框
            output.append("└");
            for (int i = 0; i < widths.length; i++) {
                output.append("─".repeat(widths[i] + 2));
                if (i < widths.length - 1) output.append("┴");
            }
            output.append("┘");

            myConsole.log("Admin", output.toString());
        } catch (Exception e) {
            debugOperation(e);
            myConsole.error("Admin", "Failed to query the database.");
        }
    }

    private static void listKeyNames() {
        executeQueryAndPrint("SELECT name, isEnable FROM sk", rs -> {
            String name = rs.getString("name");
            boolean isEnable = rs.getBoolean("isEnable");
            int clientNum = findKeyClientNum(name);
            String status = isEnable ? "" : "(disabled)";
            return String.format("%s%s(%d)", name, status, clientNum);
        }, " ");
    }

    private static void listKeyBalances() {
        executeQueryAndPrint("SELECT name, balance, isEnable FROM sk", rs -> {
            String name = rs.getString("name");
            double balance = rs.getDouble("balance");
            boolean isEnable = rs.getBoolean("isEnable");
            String status = isEnable ? "" : "(disabled)";
            return String.format("%s%s(%.2f)", name, status, balance);
        }, "\n");
    }

    private static void listKeyRates() {
        executeQueryAndPrint("SELECT name, rate, isEnable FROM sk", rs -> {
            String name = rs.getString("name");
            double rate = rs.getDouble("rate");
            boolean isEnable = rs.getBoolean("isEnable");
            String formattedRate = killDoubleEndZero(rate);
            String status = isEnable ? "" : "(disabled)";
            return String.format("%s%s(%smbps)", name, status, formattedRate);
        }, " ");
    }

    private static void listKeyExpireTimes() {
        executeQueryAndPrint("SELECT name, expireTime, isEnable FROM sk", rs -> {
            String name = rs.getString("name");
            String expireTime = rs.getString("expireTime");
            boolean isEnable = rs.getBoolean("isEnable");
            String status = isEnable ? "" : "(disabled)";
            return String.format("%s%s( %s )", name, status, expireTime);
        }, "\n");
    }

    private static void listKeyEnableStatus() {
        executeQueryAndPrint("SELECT name, isEnable FROM sk", rs -> {
            String name = rs.getString("name");
            boolean isEnable = rs.getBoolean("isEnable");
            return String.format("%s: %s", name, isEnable ? "enabled" : "disabled");
        }, "\n");
    }

    // ==================== 辅助方法 ====================

    private static void printKeyUsage() {
        myConsole.warn("Admin", """
                Usage:
                key add <name> <balance> <expireTime> <port> <rate>
                key set <name> [b=<balance>] [r=<rate>] [p=<port>] [t=<expireTime>]
                key del <name>
                key enable <name>
                key disable <name>
                key list
                key list <name | balance | rate | expire-time | enable>
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
                if (!output.isEmpty()) {
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

    // 新增方法：以表格格式输出单个密钥
    private static void outputSingleKeyAsTable(SequenceKey sequenceKey) {
        try {
            String name = sequenceKey.getName();
            double balance = sequenceKey.getBalance();
            String expireTime = sequenceKey.getExpireTime();
            int port = sequenceKey.getPort();
            double rate = sequenceKey.getRate();
            boolean isEnable = sequenceKey.isEnable();
            int clientNum = findKeyClientNum(name);
            String formattedRate = killDoubleEndZero(rate);
            String enableStatus = isEnable ? "✓" : "✗";

            // 定义表头和数据
            String[] headers = {"Name", "Balance", "ExpireTime", "Port", "Rate", "Enable", "Clients"};
            String[] data = {
                    name,
                    String.format("%.2f", balance),
                    expireTime,
                    String.valueOf(port),
                    formattedRate + "mbps",
                    enableStatus,
                    String.valueOf(clientNum)
            };

            // 计算每列的最大宽度
            int[] widths = new int[headers.length];
            for (int i = 0; i < headers.length; i++) {
                widths[i] = Math.max(headers[i].length(), data[i].length());
            }

            // 构建输出
            StringBuilder output = new StringBuilder();
            output.append("\n");

            // 添加表头分隔线
            output.append("┌");
            for (int i = 0; i < widths.length; i++) {
                output.append("─".repeat(widths[i] + 2));
                if (i < widths.length - 1) output.append("┬");
            }
            output.append("┐\n");

            // 添加表头
            output.append("│");
            for (int i = 0; i < headers.length; i++) {
                output.append(" ").append(String.format("%-" + widths[i] + "s", headers[i])).append(" ");
                output.append("│");
            }
            output.append("\n");

            // 添加数据分隔线
            output.append("├");
            for (int i = 0; i < widths.length; i++) {
                output.append("─".repeat(widths[i] + 2));
                if (i < widths.length - 1) output.append("┼");
            }
            output.append("┤\n");

            // 添加数据行
            output.append("│");
            for (int i = 0; i < data.length; i++) {
                output.append(" ").append(String.format("%-" + widths[i] + "s", data[i])).append(" ");
                output.append("│");
            }
            output.append("\n");

            // 添加底部边框
            output.append("└");
            for (int i = 0; i < widths.length; i++) {
                output.append("─".repeat(widths[i] + 2));
                if (i < widths.length - 1) output.append("┴");
            }
            output.append("┘");

            myConsole.log("Admin", output.toString());
        } catch (Exception e) {
            debugOperation(e);
            myConsole.error("Admin", "Failed to format key information.");
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