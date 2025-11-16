package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.core.ConfigOperator; // 导入 ConfigOperator
import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.ServerLogger;
import plethora.print.log.Loggist;
import plethora.utils.MyConsole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static neoproxy.neoproxyserver.NeoProxyServer.*;
import static neoproxy.neoproxyserver.core.management.IPChecker.*;
import static neoproxy.neoproxyserver.core.management.SequenceKey.*;


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
            ServerLogger.infoWithSource("Admin", "consoleManager.unknownCommand");
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
//        myConsole.registerCommand("webadmin", "Generate a temporary URL for the web admin console", (List<String> params) -> {
//            String url = WebAdminManager.getInstance().generateNewSessionUrl();
//            myConsole.log("Admin", "==============================================");
//            myConsole.log("Admin", "临时网页控制台链接已生成，5分钟内有效:");
//            myConsole.log("Admin", url);
//            myConsole.log("Admin", "==============================================");
//        });
        myConsole.registerCommand("ban", "Ban a specific IP address", (List<String> params) -> {
            if (params.size() != 1) {
                myConsole.warn("Admin", "Usage: ban <ip_address>");
                return;
            }
            String ipToBan = params.getFirst();
            if (isValidIP(ipToBan)) {
                if (IPChecker.exec(ipToBan, IPChecker.DO_BAN)) {
                    ServerLogger.infoWithSource("Admin", "consoleManager.ipBanned", ipToBan);
                } else {
                    ServerLogger.errorWithSource("Admin", "consoleManager.banFailed", ipToBan);
                }
            } else {
                ServerLogger.errorWithSource("Admin", "consoleManager.invalidIPFormate", ipToBan);
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
                    ServerLogger.infoWithSource("Admin", "consoleManager.ipUnbanned", ipToUnban);
                } else {
                    ServerLogger.errorWithSource("Admin", "consoleManager.unbanFailed", ipToUnban);
                }
            } else {
                ServerLogger.errorWithSource("Admin", "consoleManager.invalidIPFormate", ipToUnban);
            }
        });
        myConsole.registerCommand("find", "Find the location and ISP of an IP address", (List<String> params) -> {
            if (params.size() != 1) {
                myConsole.warn("Admin", "Usage: find <ip_address>");
                return;
            }
            String ipToFind = params.getFirst();
            if (isValidIP(ipToFind)) {
                IPGeolocationHelper.LocationInfo locationInfo = IPGeolocationHelper.getLocationInfo(ipToFind);
                if (locationInfo.success()) {
                    ServerLogger.infoWithSource("Admin", "consoleManager.ipLocationInfo",
                            ipToFind, locationInfo.location(), locationInfo.isp());
                } else {
                    ServerLogger.errorWithSource("Admin", "consoleManager.ipLocationQueryFailed", ipToFind);
                }
            } else {
                ServerLogger.errorWithSource("Admin", "consoleManager.invalidIPFormate", ipToFind);
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
                ServerLogger.errorWithSource("Admin", "consoleManager.internalKeyError");
            }
        });

        myConsole.registerCommand("debug", "Enable or disable debug mode", (List<String> params) -> {
            if (params.size() != 1) {
                myConsole.warn("Admin", "Usage: debug <enable|disable>");
                return;
            }

            String action = params.getFirst();

            if (!action.equals("enable") && !action.equals("disable")) {
                myConsole.warn("Admin", "Usage: web <enable|disable> <key>");
                return;
            }

            if (action.equals("enable")) {
                IS_DEBUG_MODE = true;
                ServerLogger.warnWithSource("Admin", "consoleManager.debugEnabled");
            } else {
                IS_DEBUG_MODE = false;
                ServerLogger.warnWithSource("Admin", "consoleManager.debugDisabled");
            }
        });

        myConsole.registerCommand("ver", "Print version info", (List<String> params) -> {
            ServerLogger.infoWithSource("Admin", "consoleManager.currentServerVersion", VERSION, EXPECTED_CLIENT_VERSION);
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

            boolean foundInMemory = false;
            for (HostClient hostClient : availableHostClient) {
                if (hostClient.getKey() != null && hostClient.getKey().getName().equals(keyName)) {
                    hostClient.getKey().setHTMLEnabled(enable);
                    foundInMemory = true;
                }
            }

            SequenceKey keyFromDB = getKeyFromDB(keyName);
            if (keyFromDB != null) {
                keyFromDB.setHTMLEnabled(enable);
                if (saveToDB(keyFromDB)) {
                    String logKey = enable ? "consoleManager.webHtmlEnabled" : "consoleManager.webHtmlDisabled";
                    ServerLogger.infoWithSource("Admin", logKey, keyName);
                    if (!foundInMemory) {
                        ServerLogger.infoWithSource("Admin", "consoleManager.webHtmlNote", keyName);
                    }
                } else {
                    ServerLogger.errorWithSource("Admin", "consoleManager.dbUpdateFailed", keyName);
                }
            } else {
                ServerLogger.errorWithSource("Admin", "consoleManager.criticalDbError", keyName);
            }
        });

        // 添加新的 reload 命令
        myConsole.registerCommand("reload", "Reload configurations from config.cfg", (List<String> params) -> {
            if (!params.isEmpty()) {
                myConsole.warn("Admin", "Usage: reload");
                return;
            }
            handleReloadCommand();
        });
    }

    private static void handleAlert(boolean b) {
        ServerLogger.alert = b;
        if (b) {
            ServerLogger.infoWithSource("Admin", "consoleManager.alertEnabled");
        } else {
            ServerLogger.infoWithSource("Admin", "consoleManager.alertDisabled");
        }
    }

    // 新增的 reload 命令处理方法
    private static void handleReloadCommand() {
        ServerLogger.infoWithSource("Admin", "consoleManager.reloadingConfig");
        ConfigOperator.readAndSetValue();
        ServerLogger.infoWithSource("Admin", "consoleManager.configReloaded");
    }

    // ==================== Manual Table Printing Method ====================
    private static void printAsciiTable(String[] headers, List<String[]> data) {
        if (data == null || data.isEmpty()) {
            ServerLogger.infoWithSource("Admin", "consoleManager.noDataToDisplay");
            return;
        }

        int[] colWidths = new int[headers.length];
        int[] maxLines = new int[headers.length];

        for (int i = 0; i < headers.length; i++) {
            colWidths[i] = getDisplayWidth(headers[i]);
            maxLines[i] = 1;
        }

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

        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        writeTopBorder(sb, colWidths);
        writeRow(sb, headers, colWidths, maxLines);
        writeMiddleBorder(sb, colWidths);

        for (int i = 0; i < data.size(); i++) {
            writeRow(sb, data.get(i), colWidths, maxLines);
            if (i < data.size() - 1) {
                writeMiddleBorder(sb, colWidths);
            }
        }

        writeBottomBorder(sb, colWidths);
        ServerLogger.logRaw("Admin", sb.toString());
    }

    private static void writeRow(StringBuilder sb, String[] row, int[] widths, int[] maxLines) {
        String[][] lines = new String[row.length][];
        for (int i = 0; i < row.length; i++) {
            lines[i] = row[i].split("\n");
        }

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

    private static int getMaxLineCount(int[] maxLines) {
        int max = 0;
        for (int lines : maxLines) {
            max = Math.max(max, lines);
        }
        return max;
    }

    private static void writeTopBorder(StringBuilder sb, int[] widths) {
        sb.append('┌');
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) sb.append('┬');
        }
        sb.append("┐\n");
    }

    private static void writeMiddleBorder(StringBuilder sb, int[] widths) {
        sb.append('├');
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) sb.append('┼');
        }
        sb.append("┤\n");
    }

    private static void writeBottomBorder(StringBuilder sb, int[] widths) {
        sb.append('└');
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) sb.append('┴');
        }
        sb.append("┘\n");
    }

    private static int getDisplayWidth(String str) {
        if (str == null) return 0;
        int width = 0;
        for (char c : str.toCharArray()) {
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
            ServerLogger.infoWithSource("Admin", "consoleManager.noActiveHostClients");
            return;
        }

        Map<String, Integer> accessCodeCounts = new HashMap<>();
        Map<String, HostClient> accessCodeRepresentatives = new HashMap<>();
        Map<String, List<HostClient>> accessCodeToHostClients = new HashMap<>();

        for (HostClient hostClient : availableHostClient) {
            String accessCode = hostClient.getKey() != null ? hostClient.getKey().getName() : "Unknown";
            accessCodeCounts.put(accessCode, accessCodeCounts.getOrDefault(accessCode, 0) + 1);
            if (!accessCodeRepresentatives.containsKey(accessCode)) {
                accessCodeRepresentatives.put(accessCode, hostClient);
            }
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

        String[] headers = ServerLogger.getMessage("consoleManager.headers.list").split("\\|");
        List<String[]> rows = new ArrayList<>();

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
        String[] headers = ServerLogger.getMessage("consoleManager.headers.registration").split("\\|");
        String[] data = {accessCode, ip, location, isp};
        List<String[]> rows = new ArrayList<>();
        rows.add(data);
        printAsciiTable(headers, rows);
    }

    // ==================== Key Command Implementations ====================

    private static void handleEnableCommand(List<String> params) {
        if (params.size() != 2) {
            myConsole.warn("Admin", "Usage: key enable <name>");
            return;
        }

        String name = params.get(1);
        if (!isKeyExistsByName(name)) {
            ServerLogger.errorWithSource("Admin", "consoleManager.keyNotFound");
            return;
        }

        if (enableKey(name)) {
            ServerLogger.infoWithSource("Admin", "consoleManager.keyEnabled", name);
        } else {
            ServerLogger.errorWithSource("Admin", "consoleManager.enableKeyFailed");
        }
    }

    private static void handleDisableCommand(List<String> params) {
        if (params.size() != 2) {
            myConsole.warn("Admin", "Usage: key disable <name>");
            return;
        }

        String name = params.get(1);
        if (!isKeyExistsByName(name)) {
            ServerLogger.errorWithSource("Admin", "consoleManager.keyNotFound");
            return;
        }

        if (disableKey(name)) {
            ServerLogger.infoWithSource("Admin", "consoleManager.keyDisabled", name);
        } else {
            ServerLogger.errorWithSource("Admin", "consoleManager.disableKeyFailed");
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
            ServerLogger.errorWithSource("Admin", "consoleManager.keyNotFoundSpecific", name);
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
                ServerLogger.errorWithSource("Admin", "consoleManager.keyNotFound");
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
                    ServerLogger.errorWithSource("Admin", "consoleManager.invalidPortValue", portStr);
                }
            } else if (param.startsWith("t=")) {
                String expireTimeInput = param.substring(2);
                String expireTime = correctInputTime(expireTimeInput);
                if (expireTime == null) {
                    ServerLogger.errorWithSource("Admin", "consoleManager.illegalTimeInput", expireTimeInput);
                } else if (isOutOfDate(expireTime)) {
                    ServerLogger.errorWithSource("Admin", "consoleManager.timeEarlierThanCurrent", expireTime);
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
                ServerLogger.errorWithSource("Admin", "consoleManager.unknownParameter", param);
            }
        }

        if (!hasUpdate) {
            ServerLogger.errorWithSource("Admin", "consoleManager.noValidParams");
            return;
        }

        if (newPortStr != null) {
            ServerLogger.infoWithSource("Admin", "consoleManager.portPolicyChanged", newPortStr);
            int disconnectedCount = 0;
            for (HostClient client : hostClientsToUpdate) {
                int currentExternalPort = client.getOutPort();
                if (!isPortInRange(currentExternalPort, newPortStr)) {
                    ServerLogger.infoWithSource("Admin", "consoleManager.disconnectingClient", name, String.valueOf(currentExternalPort));
                    client.close();
                    disconnectedCount++;
                }
            }
            if (disconnectedCount > 0) {
                ServerLogger.errorWithSource("Admin", "consoleManager.clientsDisconnected", String.valueOf(disconnectedCount));
            }
        }

        SequenceKey keyToSave = (dbKeySnapshot != null) ? dbKeySnapshot : inMemoryKeysToUpdate.getFirst();
        boolean isSuccess = saveToDB(keyToSave);
        if (isSuccess) {
            ServerLogger.infoWithSource("Admin", "consoleManager.operationComplete");
        } else {
            ServerLogger.errorWithSource("Admin", "consoleManager.setKeyFailed");
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
            ServerLogger.errorWithSource("Admin", "consoleManager.keyNotFound");
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
            ServerLogger.infoWithSource("Admin", "consoleManager.keyDeleted", name);
        } else {
            ServerLogger.errorWithSource("Admin", "consoleManager.keyDeleteFailed");
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
            ServerLogger.errorWithSource("Admin", "consoleManager.illegalTimeInput");
            return;
        }
        if (isOutOfDate(expireTime)) {
            ServerLogger.errorWithSource("Admin", "consoleManager.timeEarlierThanCurrent", expireTime);
            return;
        }

        Double balance = parseDoubleSafely(balanceStr, "balance");
        Double rate = parseDoubleSafely(rateStr, "rate");
        if (balance == null || rate == null) {
            return;
        }

        String validatedPortStr = validateAndFormatPortInput(portStr);
        if (validatedPortStr == null) {
            ServerLogger.errorWithSource("Admin", "consoleManager.invalidPortValue", portStr);
            return;
        }

        boolean isCreated = createNewKey(name, balance, expireTime, validatedPortStr, rate);
        if (isCreated) {
            ServerLogger.infoWithSource("Admin", "consoleManager.keyCreated", name);
        } else {
            ServerLogger.errorWithSource("Admin", "consoleManager.keyCreateFailed");
            ServerLogger.errorWithSource("Admin", "consoleManager.keyCreateHint");
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
                ServerLogger.errorWithSource("Admin", "consoleManager.keyNotFound");
                return;
            }

            String[] headers = ServerLogger.getMessage("consoleManager.headers.keyList").split("\\|");
            printAsciiTable(headers, rows);
        } catch (Exception e) {
            debugOperation(e);
            ServerLogger.errorWithSource("Admin", "consoleManager.dbQueryFailed");
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
        myConsole.warn("Admin", "Usage:");
        myConsole.warn("Admin", "  key add <name> <balance> <expireTime> <port> <rate> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.add"));
        myConsole.warn("Admin", "  key set <name> [b=<balance>] [r=<rate>] [p=<port>] [t=<expireTime>] -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.set"));
        myConsole.warn("Admin", "  key del <name> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.del"));
        myConsole.warn("Admin", "  key enable <name> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.enable"));
        myConsole.warn("Admin", "  key disable <name> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.disable"));
        myConsole.warn("Admin", "  key list -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.list"));
        myConsole.warn("Admin", "  key lp <name> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.lp"));
        myConsole.warn("Admin", "  web <enable|disable> <key> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.web"));
        myConsole.warn("Admin", "  list -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.listCmd"));
        myConsole.warn("Admin", "  ban <ip_address> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.banCmd"));
        myConsole.warn("Admin", "  unban <ip_address> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.unbanCmd"));
        myConsole.warn("Admin", "  listbans -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.listbansCmd"));
        myConsole.warn("Admin", "  find <ip_address> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.findCmd"));
        myConsole.warn("Admin", "  reload -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.reloadCmd")); // 添加 reload 命令说明
        myConsole.warn("Admin", ServerLogger.getMessage("consoleManager.printKeyUsage.portNote"));
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
            ServerLogger.errorWithSource("Admin", "consoleManager.invalidValueForField", fieldName, str);
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
                ServerLogger.infoWithSource("Admin", output.toString());
            } else {
                ServerLogger.errorWithSource("Admin", "consoleManager.keyNotFound");
            }
        } catch (Exception e) {
            debugOperation(e);
            ServerLogger.errorWithSource("Admin", "consoleManager.dbQueryFailed");
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

            String[] headers = ServerLogger.getMessage("consoleManager.headers.keyList").split("\\|");
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
            ServerLogger.errorWithSource("Admin", "consoleManager.failedToFormatKey");
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