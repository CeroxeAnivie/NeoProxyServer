package neoproject.neoproxy.core.management;

import neoproject.neoproxy.NeoProxyServer;
import neoproject.neoproxy.core.HostClient;
import neoproject.neoproxy.core.InfoBox;
import plethora.utils.MyConsole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static neoproject.neoproxy.NeoProxyServer.*;
import static neoproject.neoproxy.core.management.IPChecker.*;
import static neoproject.neoproxy.core.management.SequenceKey.*;

/**
 * Console manager, responsible for registering and processing all console commands.
 * <p>
 * All command processing logic will catch internal exceptions and log them via {@link NeoProxyServer#debugOperation},
 * preventing the entire application from crashing due to command errors.
 */
public class ConsoleManager {

    private static final String TIME_FORMAT_PATTERN = "^(\\d{4})/(\\d{1,2})/(\\d{1,2})-(\\d{1,2}):(\\d{1,2})$";
    private static final Pattern TIME_PATTERN = Pattern.compile(TIME_FORMAT_PATTERN);
    private static final String PORT_INPUT_PATTERN = "^(\\d+)(?:-(\\d+))?$";
    private static final Pattern PORT_INPUT_REGEX = Pattern.compile(PORT_INPUT_PATTERN);

    /**
     * Initialize the console and register all commands.
     */
    public static void init() {
        try {
            myConsole = new MyConsole("NeoProxyServer");
            initCommand();
            myConsole.start();
        } catch (Exception e) {
            debugOperation(e);
        }
    }

    private static void initCommand() {
        myConsole.registerCommand(null, "Unknown command", (List<String> params) -> {
            myConsole.warn("Admin", "Unknown command, type 'help' for assistance.");
        });

        myConsole.registerCommand("alert", "Set whether to enable verbose console output", (List<String> params) -> {
            if (params.size() != 1) {
                myConsole.warn("Admin", "Usage: alert <enable|disable>");
                return;
            }
            String subCommand = params.getFirst();
            switch (subCommand) {
                case "enable" -> handleAlert(true);
                case "disable" -> handleAlert(false);
                default -> myConsole.warn("Admin", "Usage: alert <enable|disable>");
            }
        });

        myConsole.registerCommand("ban", "Ban a specific IP address", (List<String> params) -> {
            if (params.size() != 1) {
                myConsole.warn("Admin", "Usage: ban <ip_address>");
                return;
            }
            String ipToBan = params.getFirst();
            if (isValidIP(ipToBan)) {
                if (IPChecker.exec(ipToBan, IPChecker.DO_BAN)) {
                    myConsole.log("Admin", "IP address " + ipToBan + " has been banned.");
                } else {
                    myConsole.warn("Admin", "Failed to ban IP address " + ipToBan + " .");
                }
            } else {
                myConsole.warn("Admin", "Invalid IP address format: " + ipToBan);
            }
        });

        myConsole.registerCommand("unban", "Unban a specific IP address", (List<String> params) -> {
            if (params.size() != 1) {
                myConsole.warn("Admin", "Usage: unban <ip_address>");
                return;
            }
            String ipToUnban = params.getFirst();
            if (isValidIP(ipToUnban)) {
                if (IPChecker.exec(ipToUnban, UNBAN)) {
                    myConsole.log("Admin", "IP address " + ipToUnban + " has been unbanned.");
                } else {
                    myConsole.warn("Admin", "IP address " + ipToUnban + " was not found in the ban list or failed to remove.");
                }
            } else {
                myConsole.warn("Admin", "Invalid IP address format: " + ipToUnban);
            }
        });

        myConsole.registerCommand("listbans", "List all banned IP addresses", (List<String> params) -> {
            listBannedIPs();
        });

        myConsole.registerCommand("list", "List all currently active HostClients and their connection info", (List<String> params) -> {
            listActiveHostClients();
        });

        myConsole.registerCommand("key", "Serial key management", (List<String> params) -> {
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
                myConsole.error("Admin", "An internal error occurred while executing the key command, please check the logs.");
            }
        });

        myConsole.registerCommand("web", "Enable or disable web HTML for a key", (List<String> params) -> {
            if (params.size() != 2) {
                myConsole.warn("Admin", "Usage: web <enable|disable> <key>");
                return;
            }

            String action = params.get(0);
            String keyName = params.get(1);

            if (!action.equals("enable") && !action.equals("disable")) {
                myConsole.warn("Admin", "Usage: web <enable|disable> <key>");
                return;
            }

            if (!isKeyExistsByName(keyName)) {
                myConsole.warn("Admin", "Key not found: " + keyName);
                return;
            }

            boolean enable = action.equals("enable");

            // 1. 先更新内存中的对象（如果存在）
            boolean foundInMemory = false;
            for (HostClient hostClient : availableHostClient) {
                if (hostClient.getKey() != null && hostClient.getKey().getName().equals(keyName)) {
                    hostClient.getKey().setHTMLEnabled(enable);
                    foundInMemory = true;
                }
            }

            // 2. 【关键修正】无论内存中是否存在，都必须从数据库加载、修改并保存
            // 这确保了数据库总是被更新，并且是唯一的真实来源
            SequenceKey keyFromDB = getKeyFromDB(keyName);
            if (keyFromDB != null) {
                keyFromDB.setHTMLEnabled(enable);
                if (saveToDB(keyFromDB)) {
                    myConsole.log("Admin", "Web HTML " + (enable ? "enabled" : "disabled") + " for key: " + keyName);
                    // 如果内存中没有找到，说明这个密钥当前没有活跃连接，仅更新数据库即可
                    if (!foundInMemory) {
                        myConsole.log("Admin", "Note: Key '" + keyName + "' is not currently active. Database has been updated.");
                    }
                } else {
                    myConsole.error("Admin", "Failed to update the database for key: " + keyName);
                }
            } else {
                // 这种情况理论上不应该发生，因为前面已经检查过 isKeyExistsByName
                myConsole.error("Admin", "Critical error: Could not load key '" + keyName + "' from database after confirming its existence.");
            }
        });
    }

    private static void handleAlert(boolean b) {
        InfoBox.alert = b;
        if (b) {
            myConsole.log("Admin", "Alert Enabled !");
        } else {
            myConsole.log("Admin", "Alert disabled !");
        }
    }

    // ==================== Manual Table Printing Method (修正边框) ====================

    /**
     * Manually build an aligned table using StringBuilder.
     * This is the most reliable and controllable method.
     *
     * @param headers Table headers
     * @param data    Data list
     */
    private static void printAsciiTable(String[] headers, List<String[]> data) {
        if (data == null || data.isEmpty()) {
            myConsole.log("Admin", "No data to display.");
            return;
        }

        // 1. 计算每列的最大显示宽度和最大行数
        int[] colWidths = new int[headers.length];
        int[] maxLines = new int[headers.length]; // 记录每列的最大行数

        // 初始化表头宽度和行数
        for (int i = 0; i < headers.length; i++) {
            colWidths[i] = getDisplayWidth(headers[i]);
            maxLines[i] = 1;
        }

        // 计算数据单元格的宽度和行数
        for (String[] row : data) {
            for (int i = 0; i < row.length; i++) {
                String[] lines = row[i].split("\n");
                int maxWidth = 0;
                for (String line : lines) {
                    maxWidth = Math.max(maxWidth, getDisplayWidth(line));
                }
                colWidths[i] = Math.max(colWidths[i], maxWidth);
                maxLines[i] = Math.max(maxLines[i], lines.length);
            }
        }

        // 2. 使用StringBuilder构建表格字符串
        StringBuilder sb = new StringBuilder();
        sb.append('\n');

        // 绘制上边框
        writeTopBorder(sb, colWidths);

        // 绘制表头
        writeRow(sb, headers, colWidths, maxLines);
        writeMiddleBorder(sb, colWidths);

        // 绘制数据行
        for (int i = 0; i < data.size(); i++) {
            writeRow(sb, data.get(i), colWidths, maxLines);
            if (i < data.size() - 1) {
                writeMiddleBorder(sb, colWidths);
            }
        }

        writeBottomBorder(sb, colWidths);
        myConsole.log("Admin", sb.toString());
    }

    // 修改writeRow方法以支持多行
    private static void writeRow(StringBuilder sb, String[] row, int[] widths, int[] maxLines) {
        // 将每列内容按换行符分割
        String[][] lines = new String[row.length][];
        for (int i = 0; i < row.length; i++) {
            lines[i] = row[i].split("\n");
        }

        // 逐行绘制
        for (int line = 0; line < getMaxLineCount(maxLines); line++) {
            sb.append('│');
            for (int col = 0; col < row.length; col++) {
                sb.append(' ');
                String cellLine = line < lines[col].length ? lines[col][line] : "";
                sb.append(cellLine);
                int padding = widths[col] - getDisplayWidth(cellLine);
                sb.append(" ".repeat(padding + 1));
                sb.append('│');
            }
            sb.append('\n');
        }
    }

    // 新增方法：获取所有列中的最大行数
    private static int getMaxLineCount(int[] maxLines) {
        int max = 0;
        for (int lines : maxLines) {
            max = Math.max(max, lines);
        }
        return max;
    }

    // 修改writeTopBorder方法
    private static void writeTopBorder(StringBuilder sb, int[] widths) {
        sb.append('┌');
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) sb.append('┬');
        }
        sb.append("┐\n");
    }

    // 修改writeMiddleBorder方法
    private static void writeMiddleBorder(StringBuilder sb, int[] widths) {
        sb.append('├');
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) sb.append('┼');
        }
        sb.append("┤\n");
    }

    // 修改writeBottomBorder方法
    private static void writeBottomBorder(StringBuilder sb, int[] widths) {
        sb.append('└');
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) sb.append('┴');
        }
        sb.append("┘\n");
    }

    /**
     * Calculate the display width of a string (CJK characters count as 2, others as 1).
     */
    private static int getDisplayWidth(String str) {
        if (str == null) return 0;
        int width = 0;
        for (char c : str.toCharArray()) {
            // Use a more precise Unicode range check
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                    Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                    Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
                width += 2;
            } else {
                width += 1;
            }
        }
        return width;
    }


    // ==================== list Command Implementation ====================

    private static void listActiveHostClients() {
        if (availableHostClient.isEmpty()) {
            myConsole.log("Admin", "No active HostClient connections.");
            return;
        }

        // 【修正】统计每个Access Code的数量，并确定代表实例
        Map<String, Integer> accessCodeCounts = new HashMap<>();
        Map<String, HostClient> accessCodeRepresentatives = new HashMap<>();
        Map<String, List<HostClient>> accessCodeToHostClients = new HashMap<>();

        // 首先统计每个Access Code的数量，并选择第一个作为代表
        for (HostClient hostClient : availableHostClient) {
            String accessCode = hostClient.getKey() != null ? hostClient.getKey().getName() : "Unknown";
            accessCodeCounts.put(accessCode, accessCodeCounts.getOrDefault(accessCode, 0) + 1);

            // 如果是第一次遇到这个Access Code，将其设为代表
            if (!accessCodeRepresentatives.containsKey(accessCode)) {
                accessCodeRepresentatives.put(accessCode, hostClient);
            }

            // 收集该Access Code的所有HostClient实例
            accessCodeToHostClients.computeIfAbsent(accessCode, k -> new ArrayList<>()).add(hostClient);
        }

        for (HostClient hostClient : availableHostClient) {
            if (hostClient.getCachedLocation() == null || hostClient.getCachedISP() == null) {
                String ip = hostClient.getHostServerHook().getInetAddress().getHostAddress();
                IPGeolocationHelper.LocationInfo locInfo = IPGeolocationHelper.getLocationInfo(ip);
                hostClient.setCachedLocation(locInfo.location());
                hostClient.setCachedISP(locInfo.isp());
            }
        }

        // 【修正】去掉序列号列
        String[] headers = {"HostClient IP", "Access Code", "Location", "ISP", "External Clients"};
        List<String[]> rows = new ArrayList<>();

        // 【修正】只添加代表实例，并传递该Access Code的所有实例
        for (Map.Entry<String, HostClient> entry : accessCodeRepresentatives.entrySet()) {
            HostClient hostClient = entry.getValue();
            String accessCode = entry.getKey();
            boolean isRepresentative = true;
            List<HostClient> allHostClientsForAccessCode = accessCodeToHostClients.get(accessCode);
            rows.add(hostClient.formatAsTableRow(accessCodeCounts, isRepresentative, allHostClientsForAccessCode));
        }

        printAsciiTable(headers, rows);
    }

    public static void printClientRegistrationTable(String accessCode, String ip, String location, String isp) {
        // 【修正】去掉序列号列
        String[] headers = {"Access Code", "IP Address", "Location", "ISP"};
        // 【修正】去掉序列号数据
        String[] data = {accessCode, ip, location, isp};

        List<String[]> rows = new ArrayList<>();
        rows.add(data);

        printAsciiTable(headers, rows);
    }

    // ==================== Existing Code, Unchanged ====================
    // (handleEnableCommand, handleDisableCommand, handleSetCommand, etc.)
    // ... For brevity, the unchanged parts are omitted here, but should be kept in the actual code ...

    private static void handleEnableCommand(List<String> params) {
        if (params.size() != 2) {
            myConsole.warn("Admin", "Usage: key enable <name>");
            return;
        }

        String name = params.get(1);
        if (!isKeyExistsByName(name)) {
            myConsole.warn("Admin", "Key not found...");
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
            myConsole.warn("Admin", "Key not found...");
            return;
        }

        if (disableKey(name)) {
            myConsole.log("Admin", "Key " + name + " has been disabled!");
        } else {
            myConsole.warn("Admin", "Failed to disable key.");
        }
    }

    private static void handleSetCommand(List<String> params) {
        if (params.size() < 2) {
            myConsole.warn("Admin", "Usage: key set <name> [b=<balance>] [r=<rate>] [p=<outPort>] [t=<expireTime>]");
            myConsole.warn("Admin", "Example: key set mykey b=1000 r=15 p=8080 t=2025/12/31-23:59");
            myConsole.warn("Admin", "Example (Dynamic Port): key set mykey p=3344-3350");
            return;
        }

        String name = params.get(1);
        if (!isKeyExistsByName(name)) {
            myConsole.warn("Admin", "No such key: " + name);
            return;
        }

        List<HostClient> hostClientsToUpdate = new ArrayList<>();
        List<SequenceKey> inMemoryKeysToUpdate = new ArrayList<>();
        SequenceKey dbKeySnapshot = null;
        for (HostClient hostClient : availableHostClient) {
            if (hostClient != null && hostClient.getKey() != null &&
                    name.equals(hostClient.getKey().getName())) {
                hostClientsToUpdate.add(hostClient);
                inMemoryKeysToUpdate.add(hostClient.getKey());
            }
        }
        if (inMemoryKeysToUpdate.isEmpty()) {
            dbKeySnapshot = getKeyFromDB(name);
            if (dbKeySnapshot == null) {
                myConsole.warn("Admin", "Could not find the key in database.");
                return;
            }
        }

        boolean hasUpdate = false;
        String newPortStr = null;
        for (int i = 2; i < params.size(); i++) {
            String param = params.get(i);
            if (param.startsWith("b=")) {
                String balanceStr = param.substring(2);
                Double balance = parseDoubleSafely(balanceStr, "balance");
                if (balance != null) {
                    for (SequenceKey key : inMemoryKeysToUpdate) {
                        key.balance = balance;
                    }
                    if (dbKeySnapshot != null) {
                        dbKeySnapshot.balance = balance;
                    }
                    hasUpdate = true;
                }
            } else if (param.startsWith("r=")) {
                String rateStr = param.substring(2);
                Double rate = parseDoubleSafely(rateStr, "rate");
                if (rate != null) {
                    for (SequenceKey key : inMemoryKeysToUpdate) {
                        key.rate = rate;
                    }
                    if (dbKeySnapshot != null) {
                        dbKeySnapshot.rate = rate;
                    }
                    hasUpdate = true;
                }
            } else if (param.startsWith("p=")) {
                String portStr = param.substring(2);
                String validatedPortStr = validateAndFormatPortInput(portStr);
                if (validatedPortStr != null) {
                    for (SequenceKey key : inMemoryKeysToUpdate) {
                        key.port = validatedPortStr;
                    }
                    if (dbKeySnapshot != null) {
                        dbKeySnapshot.port = validatedPortStr;
                    }
                    hasUpdate = true;
                    newPortStr = validatedPortStr;
                } else {
                    myConsole.warn("Admin", "Invalid outPort value: '" + portStr + "'. Must be a number (1-65535) or a range (e.g., 3344-3350).");
                }
            } else if (param.startsWith("t=")) {
                String expireTimeInput = param.substring(2);
                String expireTime = correctInputTime(expireTimeInput);
                if (expireTime == null) {
                    myConsole.warn("Admin", "Illegal time input: " + expireTimeInput);
                } else if (isOutOfDate(expireTime)) {
                    myConsole.warn("Admin", "The entered time cannot be earlier than the current time: " + expireTime);
                } else {
                    for (SequenceKey key : inMemoryKeysToUpdate) {
                        key.expireTime = expireTime;
                    }
                    if (dbKeySnapshot != null) {
                        dbKeySnapshot.expireTime = expireTime;
                    }
                    hasUpdate = true;
                }
            } else {
                myConsole.warn("Admin", "Unknown parameter: " + param + " (use b=, r=, p=, t=)");
            }
        }

        if (!hasUpdate) {
            myConsole.warn("Admin", "No valid parameters provided. Use b=<balance>, r=<rate>, p=<outPort>, t=<expireTime>");
            return;
        }

        if (newPortStr != null) {
            myConsole.log("Admin", "Port policy changed to: '" + newPortStr + "'. Validating active connections...");
            int disconnectedCount = 0;
            for (HostClient client : hostClientsToUpdate) {
                int currentExternalPort = client.getOutPort();
                if (!isPortInRange(currentExternalPort, newPortStr)) {
                    myConsole.log("Admin", "Disconnecting client (Key: " + name +
                            ", Current Port: " + currentExternalPort +
                            ") as it no longer complies with the new outPort policy.");
                    client.close();
                    disconnectedCount++;
                }
            }
            if (disconnectedCount > 0) {
                myConsole.warn("Admin", "Disconnected " + disconnectedCount + " client(s) due to outPort policy change.");
            }
        }

        SequenceKey keyToSave = (dbKeySnapshot != null) ? dbKeySnapshot : inMemoryKeysToUpdate.getFirst();
        boolean isSuccess = saveToDB(keyToSave);
        if (isSuccess) {
            myConsole.log("Admin", "Operation complete!");
        } else {
            myConsole.warn("Admin", "Fail to set the key.");
        }
    }

    private static boolean isPortInRange(int port, String portRange) {
        if (portRange == null || portRange.isEmpty()) {
            return false;
        }
        String[] parts = portRange.split("-", -1);
        if (parts.length == 1) {
            try {
                int singlePort = Integer.parseInt(parts[0].trim());
                return port == singlePort;
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (parts.length == 2) {
            try {
                int start = Integer.parseInt(parts[0].trim());
                int end = Integer.parseInt(parts[1].trim());
                return port >= start && port <= end;
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            return false;
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
            outputSingleKeyAsTable(sequenceKey);
        } else {
            myConsole.warn("Admin", "Key not found...");
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
            myConsole.warn("Admin", "Key not found or failed to delete.");
        }
    }

    private static void handleAddCommand(List<String> params) {
        if (params.size() != 6) {
            myConsole.warn("Admin", "Usage: key add <name> <balance> <expireTime> <port> <rate>");
            myConsole.warn("Admin", "Note: <port> can be a single number (e.g., 8080) or a range (e.g., 3344-3350).");
            return;
        }

        String name = params.get(1);
        String balanceStr = params.get(2);
        String expireTimeInput = params.get(3);
        String portStr = params.get(4);
        String rateStr = params.get(5);

        String expireTime = correctInputTime(expireTimeInput);
        if (expireTime == null) {
            myConsole.warn("Admin", "Illegal time input.");
            return;
        }
        if (isOutOfDate(expireTime)) {
            myConsole.warn("Admin", "The entered time cannot be later than the current time.");
            return;
        }

        Double balance = parseDoubleSafely(balanceStr, "balance");
        Double rate = parseDoubleSafely(rateStr, "rate");
        if (balance == null || rate == null) {
            return;
        }

        String validatedPortStr = validateAndFormatPortInput(portStr);
        if (validatedPortStr == null) {
            myConsole.warn("Admin", "Invalid port value: '" + portStr + "'. Must be a number (1-65535) or a range (e.g., 3344-3350).");
            return;
        }

        boolean isCreated = createNewKey(name, balance, expireTime, validatedPortStr, rate);
        if (isCreated) {
            myConsole.log("Admin", "Key " + name + " has been created!");
        } else {
            myConsole.error("Admin", "Failed to create key.");
            myConsole.warn("Admin", "The key might already exist or an internal error occurred.");
        }
    }

    private static void listAllKeys() {
        String sql = "SELECT name, balance, expireTime, port, rate, isEnable, enableWebHTML FROM sk";
        try (var conn = getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {
            List<String[]> rows = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString("name");
                double balance = rs.getDouble("balance");
                String expireTime = rs.getString("expireTime");
                String portStr = rs.getString("port");
                double rate = rs.getDouble("rate");
                boolean isEnable = rs.getBoolean("isEnable");
                boolean enableWebHTML = rs.getBoolean("enableWebHTML");
                int clientNum = findKeyClientNum(name);
                String formattedRate = killDoubleEndZero(rate);
                String enableStatus = isEnable ? "✓" : "✗";
                String webHTMLStatus = enableWebHTML ? "✓" : "✗";

                rows.add(new String[]{
                        name,
                        String.format("%.2f", balance),
                        expireTime,
                        portStr,
                        formattedRate + "mbps",
                        enableStatus,
                        webHTMLStatus,
                        String.valueOf(clientNum)
                });
            }
            if (rows.isEmpty()) {
                myConsole.warn("Admin", "No key found.");
                return;
            }

            String[] headers = {"Name", "Balance", "ExpireTime", "Port", "Rate", "Enable", "WebHTML", "Clients"};
            printAsciiTable(headers, rows);
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
                key lp <name>
                web <enable|disable> <key>
                list
                ban <ip_address>
                unban <ip_address>
                listbans
                Note: <port> can be a single number (e.g., 8080) or a range (e.g., 3344-3350).""");
    }

    private static String validateAndFormatPortInput(String portInput) {
        if (portInput == null || portInput.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = PORT_INPUT_REGEX.matcher(portInput.trim());
        if (!matcher.matches()) {
            return null;
        }

        try {
            String startPortStr = matcher.group(1);
            String endPortStr = matcher.group(2);
            int startPort = Integer.parseInt(startPortStr);
            if (startPort < 1 || startPort > 65535) {
                return null;
            }

            if (endPortStr == null) {
                return String.valueOf(startPort);
            } else {
                int endPort = Integer.parseInt(endPortStr);
                if (endPort < 1 || endPort > 65535 || endPort < startPort) {
                    return null;
                }
                return startPort + "-" + endPort;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDoubleSafely(String str, String fieldName) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            myConsole.warn("Admin", "Invalid value for " + fieldName + ": '" + str + "'");
            return null;
        }
    }

    private static void executeQueryAndPrint(String sql, RowProcessor rowProcessor) {
        executeQueryAndPrint(sql, rowProcessor, "\n");
    }

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

    private static void outputSingleKeyAsTable(SequenceKey sequenceKey) {
        try {
            String name = sequenceKey.getName();
            double balance = sequenceKey.getBalance();
            String expireTime = sequenceKey.getExpireTime();
            String portStr = sequenceKey.port;
            double rate = sequenceKey.getRate();
            boolean isEnable = sequenceKey.isEnable();
            boolean enableWebHTML = sequenceKey.isHTMLEnabled();
            int clientNum = findKeyClientNum(name);
            String formattedRate = killDoubleEndZero(rate);
            String enableStatus = isEnable ? "✓" : "✗";
            String webHTMLStatus = enableWebHTML ? "✓" : "✗";

            String[] headers = {"Name", "Balance", "ExpireTime", "Port", "Rate", "Enable", "WebHTML", "Clients"};
            String[] data = {
                    name,
                    String.format("%.2f", balance),
                    expireTime,
                    portStr,
                    formattedRate + "mbps",
                    enableStatus,
                    webHTMLStatus,
                    String.valueOf(clientNum)
            };

            List<String[]> rows = new ArrayList<>();
            rows.add(data);

            printAsciiTable(headers, rows);
        } catch (Exception e) {
            debugOperation(e);
            myConsole.error("Admin", "Failed to format key information.");
        }
    }

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

    @FunctionalInterface
    private interface RowProcessor {
        String process(java.sql.ResultSet rs) throws java.sql.SQLException;
    }
}