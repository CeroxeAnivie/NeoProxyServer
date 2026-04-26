package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.core.ConfigOperator;
import neoproxy.neoproxyserver.core.Debugger;
import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.exceptions.*;
import neoproxy.neoproxyserver.core.management.provider.RemoteKeyProvider;
import neoproxy.neoproxyserver.core.webadmin.WebAdminManager;
import neoproxy.neoproxyserver.core.webadmin.WebConsole;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static neoproxy.neoproxyserver.NeoProxyServer.*;
import static neoproxy.neoproxyserver.core.management.IPChecker.*;
import static neoproxy.neoproxyserver.core.management.SequenceKey.*;

/**
 * ConsoleManager (Industrial SQLite Version)
 * 彻底解耦 JDBC 硬编码，由 Database 类驱动持久化。
 * 性能优化：利用 SQLite WAL 模式实现毫秒级查询。
 */
public class ConsoleManager {

    public static final ThreadLocal<String> COMMAND_SOURCE = ThreadLocal.withInitial(() -> "Admin");

    private static final String TIME_FORMAT_PATTERN = "^(\\d{4})/(\\d{1,2})/(\\d{1,2})-(\\d{1,2}):(\\d{1,2})$";
    private static final Pattern TIME_PATTERN = Pattern.compile(TIME_FORMAT_PATTERN);

    private static final String PORT_INPUT_PATTERN = "^(\\d+)(?:-(\\d+))?$";
    private static final Pattern PORT_INPUT_REGEX = Pattern.compile(PORT_INPUT_PATTERN);

    public static void init() {
        try {
            myConsole = new WebConsole("NeoProxyServer");
            initCommand();
            myConsole.start();
        } catch (Exception e) {
            Debugger.debugOperation(e);
        }
    }

    private static void registerWrapper(String name, String desc, Consumer<List<String>> executor) {
        myConsole.registerCommand(name, desc, (params) -> {
            StringBuilder cmdLine = new StringBuilder("> ").append(name);
            if (params != null && !params.isEmpty()) {
                cmdLine.append(" ").append(String.join(" ", params));
            }
            WebAdminManager.broadcastLog(cmdLine.toString());
            executor.accept(params);
        });
    }

    private static void initCommand() {
        myConsole.registerCommand(null, "Unknown command", (List<String> params) -> {
            if (!params.isEmpty()) {
                String rawCmd = String.join(" ", params);
                WebAdminManager.broadcastLog("> " + rawCmd);
            }
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.unknownCommand");
        });

        registerWrapper("alert", "Set whether to enable verbose console output", (List<String> params) -> {
            if (params.size() != 1) {
                myConsole.warn(COMMAND_SOURCE.get(), "Usage: alert <enable|disable>");
                return;
            }
            String subCommand = params.getFirst();
            switch (subCommand) {
                case "enable" -> handleAlert(true);
                case "disable" -> handleAlert(false);
                default -> myConsole.warn(COMMAND_SOURCE.get(), "Usage: alert <enable|disable>");
            }
        });

        registerWrapper("webadmin", "Generate a temporary web admin link", (List<String> params) -> {
            String url = neoproxy.neoproxyserver.core.webadmin.WebAdminManager.generateNewSessionUrl();
            ServerLogger.logRaw(COMMAND_SOURCE.get(), "==============================================");
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.webAdminGenTitle");
            ServerLogger.logRaw(COMMAND_SOURCE.get(), url);
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.webAdminGenNote");
            ServerLogger.logRaw(COMMAND_SOURCE.get(), "==============================================");
        });

        registerWrapper("ban", "Ban a specific IP address", (List<String> params) -> {
            if (params.size() != 1) {
                myConsole.warn(COMMAND_SOURCE.get(), "Usage: ban <ip_address>");
                return;
            }
            String ipToBan = params.getFirst();
            if (isValidIP(ipToBan)) {
                if (IPChecker.exec(ipToBan, IPChecker.DO_BAN)) {
                    ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.ipBanned", ipToBan);
                } else {
                    ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.banFailed", ipToBan);
                }
            } else {
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.invalidIPFormate", ipToBan);
            }
        });

        registerWrapper("unban", "Unban a specific IP address", (List<String> params) -> {
            if (params.size() != 1) {
                myConsole.warn(COMMAND_SOURCE.get(), "Usage: unban <ip_address>");
                return;
            }
            String ipToUnban = params.getFirst();
            if (isValidIP(ipToUnban)) {
                if (IPChecker.exec(ipToUnban, UNBAN)) {
                    ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.ipUnbanned", ipToUnban);
                } else {
                    ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.unbanFailed", ipToUnban);
                }
            } else {
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.invalidIPFormate", ipToUnban);
            }
        });

        registerWrapper("find", "Find the location and ISP of an IP address", (List<String> params) -> {
            if (params.size() != 1) {
                myConsole.warn(COMMAND_SOURCE.get(), "Usage: find <ip_address>");
                return;
            }
            String ipToFind = params.getFirst();
            if (isValidIP(ipToFind)) {
                IPGeolocationHelper.LocationInfo locationInfo = IPGeolocationHelper.getLocationInfo(ipToFind);
                if (locationInfo.success()) {
                    ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.ipLocationInfo",
                            ipToFind, locationInfo.location(), locationInfo.isp());
                } else {
                    ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.ipLocationQueryFailed", ipToFind);
                }
            } else {
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.invalidIPFormate", ipToFind);
            }
        });

        registerWrapper("listbans", "List all banned IP addresses", (List<String> params) -> {
            listBannedIPs();
        });

        registerWrapper("list", "List all currently active HostClients and their connection info", (List<String> params) -> {
            listActiveHostClients();
        });

        registerWrapper("key", "Serial key management", (List<String> params) -> {
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
                Debugger.debugOperation(e);
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.internalKeyError");
            }
        });

        registerWrapper("debug", "Enable or disable debug mode", (List<String> params) -> {
            if (params.size() != 1) {
                myConsole.warn(COMMAND_SOURCE.get(), "Usage: debug <enable|disable>");
                return;
            }
            String action = params.getFirst();
            if (!action.equals("enable") && !action.equals("disable")) {
                myConsole.warn(COMMAND_SOURCE.get(), "Usage: debug <enable|disable>");
                return;
            }
            if (action.equals("enable")) {
                IS_DEBUG_MODE = true;
                ServerLogger.warnWithSource(COMMAND_SOURCE.get(), "consoleManager.debugEnabled");
            } else {
                IS_DEBUG_MODE = false;
                ServerLogger.warnWithSource(COMMAND_SOURCE.get(), "consoleManager.debugDisabled");
            }
        });

        registerWrapper("ver", "Print version info", (List<String> params) -> {
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.currentServerVersion", VERSION, EXPECTED_CLIENT_VERSION);
        });

        registerWrapper("web", "Enable or disable web HTML for a key", (List<String> params) -> {
            if (params.size() != 2) {
                myConsole.warn(COMMAND_SOURCE.get(), "Usage: web <enable|disable> <key>");
                return;
            }
            String action = params.get(0);
            String keyName = params.get(1);
            if (!action.equals("enable") && !action.equals("disable")) {
                myConsole.warn(COMMAND_SOURCE.get(), "Usage: web <enable|disable> <key>");
                return;
            }

            if (!isKeyExistsByName(keyName)) {
                if (!(SequenceKey.PROVIDER instanceof RemoteKeyProvider) || !SequenceKey.getKeyCacheSnapshot().containsKey(keyName)) {
                    myConsole.warn(COMMAND_SOURCE.get(), "Key not found: " + keyName);
                    return;
                }
            }

            boolean enable = action.equals("enable");
            boolean foundInMemory = false;
            for (HostClient hostClient : availableHostClient) {
                if (hostClient.getKey() != null && hostClient.getKey().getName().equals(keyName)) {
                    hostClient.getKey().setHTMLEnabled(enable);
                    foundInMemory = true;
                }
            }

            try {
                SequenceKey keyFromDB = Database.getKey(keyName, false);
                if (keyFromDB != null) {
                    keyFromDB.setHTMLEnabled(enable);
                    if (Database.saveKey(keyFromDB)) {
                        String logKey = enable ? "consoleManager.webHtmlEnabled" : "consoleManager.webHtmlDisabled";
                        ServerLogger.infoWithSource(COMMAND_SOURCE.get(), logKey, keyName);
                        if (!foundInMemory) {
                            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.webHtmlNote", keyName);
                        }
                    } else {
                        ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.dbUpdateFailed", keyName);
                    }
                } else {
                    ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.criticalDbError", keyName);
                }
            } catch (Exception e) {
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyStateError.cannotUpdate", e.getMessage());
            }
        });

        registerWrapper("reload", "Reload configurations from config.cfg", (List<String> params) -> {
            if (!params.isEmpty()) {
                myConsole.warn(COMMAND_SOURCE.get(), "Usage: reload");
                return;
            }
            handleReloadCommand();
        });

        registerWrapper("profile", "Generate a detailed performance diagnostic report", (List<String> params) -> {
            if (!params.isEmpty()) {
                myConsole.warn(COMMAND_SOURCE.get(), "Usage: profile");
                return;
            }
            handleProfileCommand();
        });
    }

    private static void handleAlert(boolean b) {
        ServerLogger.alert = b;
        if (b) {
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.alertEnabled");
        } else {
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.alertDisabled");
        }
    }

    private static void handleReloadCommand() {
        ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.reloadingConfig");

        ConfigOperator.readAndSetValue();

        Database.reload();

        SequenceKey.reloadProvider();

        ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.configReloaded");

        // 【修复】与启动时 applyMainSettings() 的警告逻辑统一：
        // 只要 permToken 不为空就发出安全警告，而非基于文件修改时间判断
        String permToken = neoproxy.neoproxyserver.core.webadmin.WebAdminManager.getPermanentToken();
        if (permToken != null && !permToken.isEmpty()) {
            ServerLogger.warnWithSource(COMMAND_SOURCE.get(), "configOperator.permTokenWarning");
        }
    }

    private static void handleProfileCommand() {
        ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.profileGenerating");

        String reportPath = ProfileReporter.generateAndSaveReport();

        if (reportPath != null) {
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.profileGenerated", reportPath);
        } else {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.profileGenerationFailed");
        }
    }

    private static void printAsciiTable(String[] headers, List<String[]> data) {
        if (data == null || data.isEmpty()) {
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.noDataToDisplay");
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
        ServerLogger.logRaw(COMMAND_SOURCE.get(), sb.toString());
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

    private static void listActiveHostClients() {
        if (availableHostClient.isEmpty()) {
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.noActiveHostClients");
            return;
        }

        Map<String, List<HostClient>> ipGroupedClients = new HashMap<>();

        for (HostClient hostClient : availableHostClient) {
            String accessCode = hostClient.getKey() != null ? hostClient.getKey().getName() : "Unknown";
            String ip = hostClient.getIP();
            String compositeKey = accessCode + "::" + ip;
            ipGroupedClients.computeIfAbsent(compositeKey, k -> new ArrayList<>()).add(hostClient);
        }

        for (HostClient hostClient : availableHostClient) {
            if (hostClient.getCachedLocation() == null || hostClient.getCachedISP() == null) {
                String ip = hostClient.getHostServerHook().getInetAddress().getHostAddress();
                IPGeolocationHelper.LocationInfo locInfo = IPGeolocationHelper.getLocationInfo(ip);
                hostClient.setCachedLocation(locInfo.location());
                hostClient.setCachedISP(locInfo.isp());
            }
        }

        String rawHeaders = ServerLogger.getMessage("consoleManager.headers.list");
        String[] originalHeaders = rawHeaders.split("\\|");
        List<String> headerList = new ArrayList<>(Arrays.asList(originalHeaders));
        if (headerList.size() >= 4) {
            headerList.add(4, "Port");
        } else {
            headerList.add("Port");
        }
        String[] headers = headerList.toArray(new String[0]);

        List<String[]> rows = new ArrayList<>();
        for (List<HostClient> group : ipGroupedClients.values()) {
            if (group.isEmpty()) continue;
            HostClient representative = group.get(0);
            int countInThisIp = group.size();
            rows.add(representative.formatAsTableRow(countInThisIp, true, group));
        }

        rows.sort(Comparator.comparing(r -> r[1]));
        printAsciiTable(headers, rows);
    }

    private static void handleEnableCommand(List<String> params) {
        if (params.size() != 2) {
            myConsole.warn(COMMAND_SOURCE.get(), "Usage: key enable <name>");
            return;
        }
        String name = params.get(1);
        if (!isKeyExistsByName(name)) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFound");
            return;
        }
        if (enableKey(name)) {
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.keyEnabled", name);
        } else {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.enableKeyFailed");
        }
    }

    private static void handleDisableCommand(List<String> params) {
        if (params.size() != 2) {
            myConsole.warn(COMMAND_SOURCE.get(), "Usage: key disable <name>");
            return;
        }
        String name = params.get(1);
        if (!isKeyExistsByName(name)) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFound");
            return;
        }
        if (disableKey(name)) {
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.keyDisabled", name);
        } else {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.disableKeyFailed");
        }
    }

    private static boolean isPortInRange(int port, String portRange) {
        if (portRange == null || portRange.isEmpty()) return false;
        String[] parts = portRange.split("-", -1);
        if (parts.length == 1) {
            try {
                return port == Integer.parseInt(parts[0].trim());
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
        }
        return false;
    }

    private static void handleLookupCommand(List<String> params) {
        if (params.size() != 2) {
            myConsole.warn(COMMAND_SOURCE.get(), "Usage: key lp <name>");
            return;
        }
        String name = params.get(1);
        try {
            SequenceKey sequenceKey = getKeyFromDB(name);
            if (sequenceKey != null) {
                outputSingleKeyAsTable(sequenceKey);
            } else {
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFound");
            }
        } catch (OutDatedKeyException | UnRecognizedKeyException | NoMoreNetworkFlowException e) {
            SequenceKey cached = SequenceKey.getKeyCacheSnapshot().get(name);
            if (cached != null) {
                outputSingleKeyAsTable(cached);
                ServerLogger.warnWithSource(COMMAND_SOURCE.get(), "consoleManager.keyInfo.cachedDisplay", e.getMessage());
            } else {
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyInfo.queryFailed", e.getMessage());
            }
        } catch (PortOccupiedException | NoMorePortException e) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyError", e.getMessage());
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
                        myConsole.warn(COMMAND_SOURCE.get(), "Usage: key list | key list <name | balance | rate | expire-time | enable>");
            }
        } else {
            myConsole.warn(COMMAND_SOURCE.get(), "Usage: key list | key list <name | balance | rate | expire-time | enable>");
        }
    }

    private static void handleDelCommand(List<String> params) {
        if (params.size() != 2) {
            myConsole.warn(COMMAND_SOURCE.get(), "Usage: key del <name>");
            return;
        }
        String name = params.get(1);
        if (removeKey(name)) {
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.keyDeleted", name);
        } else {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyDeleteFailed");
        }
    }

    private static void handleSetCommand(List<String> params) {
        if (params.size() < 2) {
            myConsole.warn(COMMAND_SOURCE.get(), "Usage: key set <name> [b=<balance>] [r=<rate>] [p=<outPort>] [t=<expireTime>] [w=<webHTML>]");
            return;
        }
        String name = params.get(1);

        if (SequenceKey.PROVIDER instanceof RemoteKeyProvider) {
            if (SequenceKey.getKeyCacheSnapshot().containsKey(name)) {
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.error.remoteKeyModification");
                return;
            }
        }

        if (!isKeyExistsByName(name)) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFoundSpecific", name);
            return;
        }

        List<HostClient> hostClientsToUpdate = new ArrayList<>();
        List<SequenceKey> inMemoryKeysToUpdate = new ArrayList<>();
        SequenceKey dbKeySnapshot = null;

        for (HostClient hostClient : availableHostClient) {
            if (hostClient != null && hostClient.getKey() != null && name.equals(hostClient.getKey().getName())) {
                hostClientsToUpdate.add(hostClient);
                inMemoryKeysToUpdate.add(hostClient.getKey());
            }
        }

        try {
            if (inMemoryKeysToUpdate.isEmpty()) {
                dbKeySnapshot = getKeyFromDB(name);
                if (dbKeySnapshot == null) {
                    ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFound");
                    return;
                }
            }
        } catch (Exception e) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.error.keyInvalidForSet", e.getMessage());
            return;
        }

        boolean hasUpdate = false;
        String newPortStr = null;

        for (int i = 2; i < params.size(); i++) {
            String param = params.get(i);
            if (param.startsWith("b=")) {
                Double val = parseDoubleSafely(param.substring(2), "balance");
                if (val != null) {
                    for (SequenceKey k : inMemoryKeysToUpdate) k.setBalance(val);
                    if (dbKeySnapshot != null) dbKeySnapshot.setBalance(val);
                    hasUpdate = true;
                }
            } else if (param.startsWith("r=")) {
                Double val = parseDoubleSafely(param.substring(2), "rate");
                if (val != null) {
                    for (SequenceKey k : inMemoryKeysToUpdate) k.setRate(val);
                    if (dbKeySnapshot != null) dbKeySnapshot.setRate(val);
                    for (HostClient hc : hostClientsToUpdate) {
                        if (hc.getGlobalRateLimiter() != null) hc.getGlobalRateLimiter().setMaxMbps(val);
                    }
                    hasUpdate = true;
                }
            } else if (param.startsWith("p=")) {
                String val = validateAndFormatPortInput(param.substring(2));
                if (val != null) {
                    for (SequenceKey k : inMemoryKeysToUpdate) k.setPort(val);
                    if (dbKeySnapshot != null) dbKeySnapshot.setPort(val);
                    newPortStr = val;
                    hasUpdate = true;
                } else {
                    ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.invalidPortValue", param.substring(2));
                }
            } else if (param.startsWith("t=")) {
                String val = correctInputTime(param.substring(2));
                if (val == null) {
                    ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.illegalTimeInput", param.substring(2));
                } else if (isOutOfDate(val)) {
                    ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.timeEarlierThanCurrent", val);
                } else {
                    for (SequenceKey k : inMemoryKeysToUpdate) k.setExpireTime(val);
                    if (dbKeySnapshot != null) dbKeySnapshot.setExpireTime(val);
                    hasUpdate = true;
                }
            } else if (param.startsWith("w=")) {
                boolean enable = "true".equalsIgnoreCase(param.substring(2)) || "1".equals(param.substring(2)) || "on".equalsIgnoreCase(param.substring(2));
                for (SequenceKey k : inMemoryKeysToUpdate) k.setHTMLEnabled(enable);
                if (dbKeySnapshot != null) dbKeySnapshot.setHTMLEnabled(enable);
                hasUpdate = true;
            } else {
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.unknownParameter", param);
            }
        }

        if (!hasUpdate) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.noValidParams");
            return;
        }

        if (newPortStr != null) {
            int disconnectedCount = 0;
            for (HostClient client : hostClientsToUpdate) {
                if (!isPortInRange(client.getOutPort(), newPortStr)) {
                    ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.disconnectingClient", name, String.valueOf(client.getOutPort()));
                    client.close();
                    disconnectedCount++;
                }
            }
            if (disconnectedCount > 0)
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.clientsDisconnected", String.valueOf(disconnectedCount));
        }

        SequenceKey keyToSave = (dbKeySnapshot != null) ? dbKeySnapshot : inMemoryKeysToUpdate.getFirst();
        if (saveToDB(keyToSave)) {
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.operationComplete");
        } else {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.setKeyFailed");
        }
    }

    private static void handleAddCommand(List<String> params) {
        if (params.size() != 6 && params.size() != 7) {
            myConsole.warn(COMMAND_SOURCE.get(), "Usage: key add <name> <balance> <expireTime> <port> <rate> [webHTML]");
            return;
        }
        String name = params.get(1);
        if (isKeyExistsByName(name)) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.error.keyAlreadyExists");
            return;
        }

        String expireTime = correctInputTime(params.get(3));
        if (expireTime == null) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.illegalTimeInput");
            return;
        }
        if (isOutOfDate(expireTime)) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.timeEarlierThanCurrent", expireTime);
            return;
        }

        Double balance = parseDoubleSafely(params.get(2), "balance");
        Double rate = parseDoubleSafely(params.get(5), "rate");
        String validatedPortStr = validateAndFormatPortInput(params.get(4));

        if (balance == null || rate == null || validatedPortStr == null) {
            if (validatedPortStr == null)
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.invalidPortValue", params.get(4));
            return;
        }

        if (createNewKey(name, balance, expireTime, validatedPortStr, rate)) {
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.keyCreated", name);
            boolean web = params.size() == 7 && ("true".equalsIgnoreCase(params.get(6)) || "1".equals(params.get(6)) || "on".equalsIgnoreCase(params.get(6)));
            myConsole.execute("web " + (web ? "enable" : "disable") + " " + name);
        } else {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyCreateFailed");
        }
    }

    private static void listAllKeys() {
        Map<String, KeyListDTO> keyMap = new HashMap<>();
        List<SequenceKey> localKeys = Database.getAllKeys();
        for (SequenceKey k : localKeys) {
            keyMap.put(k.getName(), new KeyListDTO(k.getName(), k.getBalanceNoLock(), k.getExpireTime(), k.getPortStr(), k.getRateNoLock(), k.isEnable(), k.isHTMLEnabled(), " (L)"));
        }
        if (SequenceKey.PROVIDER instanceof RemoteKeyProvider) {
            for (SequenceKey k : SequenceKey.getKeyCacheSnapshot().values()) {
                keyMap.put(k.getName(), new KeyListDTO(k, " (R)"));
            }
        }
        if (keyMap.isEmpty()) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFound");
            return;
        }

        List<KeyListDTO> sortedList = keyMap.values().stream().sorted((k1, k2) -> {
            boolean isR1 = k1.sourceTag.contains("(R)"), isR2 = k2.sourceTag.contains("(R)");
            if (isR1 && !isR2) return -1;
            if (!isR1 && isR2) return 1;
            return k1.name.compareToIgnoreCase(k2.name);
        }).toList();

        List<String[]> rows = sortedList.stream().map(dto -> new String[]{
                dto.name + dto.sourceTag, String.format("%.2f", dto.balance), dto.expireTime, dto.port, killDoubleEndZero(dto.rate) + "mbps",
                dto.isEnable ? "✓" : "✗", dto.enableWebHTML ? "✓" : "✗", String.valueOf(findKeyClientNum(dto.name))
        }).collect(Collectors.toList());

        printAsciiTable(ServerLogger.getMessage("consoleManager.headers.keyList").split("\\|"), rows);
    }

    private static void listKeyNames() {
        List<SequenceKey> keys = Database.getAllKeys();
        if (keys.isEmpty()) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFound");
            return;
        }
        String result = keys.stream().map(k -> String.format("%s%s(%d)", k.getName(), k.isEnable() ? "" : "(disabled)", findKeyClientNum(k.getName()))).collect(Collectors.joining(" "));
        ServerLogger.infoWithSource(COMMAND_SOURCE.get(), result);
    }

    private static void listKeyBalances() {
        List<SequenceKey> keys = Database.getAllKeys();
        if (keys.isEmpty()) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFound");
            return;
        }
        String result = keys.stream().map(k -> String.format("%s%s(%.2f)", k.getName(), k.isEnable() ? "" : "(disabled)", k.getBalanceNoLock())).collect(Collectors.joining("\n"));
        ServerLogger.infoWithSource(COMMAND_SOURCE.get(), result);
    }

    private static void listKeyRates() {
        List<SequenceKey> keys = Database.getAllKeys();
        if (keys.isEmpty()) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFound");
            return;
        }
        String result = keys.stream().map(k -> String.format("%s%s(%smbps)", k.getName(), k.isEnable() ? "" : "(disabled)", killDoubleEndZero(k.getRateNoLock()))).collect(Collectors.joining(" "));
        ServerLogger.infoWithSource(COMMAND_SOURCE.get(), result);
    }

    private static void listKeyExpireTimes() {
        List<SequenceKey> keys = Database.getAllKeys();
        if (keys.isEmpty()) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFound");
            return;
        }
        String result = keys.stream().map(k -> String.format("%s%s( %s )", k.getName(), k.isEnable() ? "" : "(disabled)", k.getExpireTime())).collect(Collectors.joining("\n"));
        ServerLogger.infoWithSource(COMMAND_SOURCE.get(), result);
    }

    private static void listKeyEnableStatus() {
        List<SequenceKey> keys = Database.getAllKeys();
        if (keys.isEmpty()) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFound");
            return;
        }
        String result = keys.stream().map(k -> String.format("%s: %s", k.getName(), k.isEnable() ? "enabled" : "disabled")).collect(Collectors.joining("\n"));
        ServerLogger.infoWithSource(COMMAND_SOURCE.get(), result);
    }

    private static void printKeyUsage() {
        myConsole.warn(COMMAND_SOURCE.get(), "Usage:");
        myConsole.warn(COMMAND_SOURCE.get(), "  key add <name> <balance> <expireTime> <port> <rate> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.add"));
        myConsole.warn(COMMAND_SOURCE.get(), "  key set <name> [b=<balance>] [r=<rate>] [p=<port>] [t=<expireTime>] [w=<webHTML>] -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.set"));
        myConsole.warn(COMMAND_SOURCE.get(), "  key del <name> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.del"));
        myConsole.warn(COMMAND_SOURCE.get(), "  key enable <name> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.enable"));
        myConsole.warn(COMMAND_SOURCE.get(), "  key disable <name> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.disable"));
        myConsole.warn(COMMAND_SOURCE.get(), "  key list -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.list"));
        myConsole.warn(COMMAND_SOURCE.get(), "  key lp <name> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.lp"));
        myConsole.warn(COMMAND_SOURCE.get(), "  web <enable|disable> <key> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.web"));
        myConsole.warn(COMMAND_SOURCE.get(), "  list -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.listCmd"));
        myConsole.warn(COMMAND_SOURCE.get(), "  ban <ip_address> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.banCmd"));
        myConsole.warn(COMMAND_SOURCE.get(), "  unban <ip_address> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.unbanCmd"));
        myConsole.warn(COMMAND_SOURCE.get(), "  listbans -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.listbansCmd"));
        myConsole.warn(COMMAND_SOURCE.get(), "  find <ip_address> -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.findCmd"));
        myConsole.warn(COMMAND_SOURCE.get(), "  reload -- " + ServerLogger.getMessage("consoleManager.printKeyUsage.reloadCmd"));
    }

    private static String validateAndFormatPortInput(String portInput) {
        if (portInput == null || portInput.trim().isEmpty()) return null;
        Matcher m = PORT_INPUT_REGEX.matcher(portInput.trim());
        if (!m.matches()) return null;
        try {
            int s = Integer.parseInt(m.group(1));
            if (s < 1 || s > 65535) return null;
            if (m.group(2) == null) return String.valueOf(s);
            int e = Integer.parseInt(m.group(2));
            if (e < 1 || e > 65535 || e < s) return null;
            return s + "-" + e;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDoubleSafely(String str, String fieldName) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.invalidValueForField", fieldName, str);
            return null;
        }
    }

    private static void outputSingleKeyAsTable(SequenceKey sequenceKey) {
        try {
            String[] headers = ServerLogger.getMessage("consoleManager.headers.keyList").split("\\|");
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{
                    sequenceKey.getName(), String.format("%.2f", sequenceKey.getBalance()), sequenceKey.getExpireTime(), sequenceKey.getPortStr(),
                    killDoubleEndZero(sequenceKey.getRate()) + "mbps", sequenceKey.isEnable() ? "✓" : "✗", sequenceKey.isHTMLEnabled() ? "✓" : "✗", String.valueOf(findKeyClientNum(sequenceKey.getName()))
            });
            printAsciiTable(headers, rows);
        } catch (Exception e) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.failedToFormatKey");
        }
    }

    private static int findKeyClientNum(String name) {
        return (int) availableHostClient.stream().filter(h -> h != null && h.getKey() != null && name.equals(h.getKey().getName())).count();
    }

    private static String killDoubleEndZero(double d) {
        return d == (long) d ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String correctInputTime(String time) {
        if (time == null) return null;
        Matcher m = TIME_PATTERN.matcher(time);
        if (!m.matches()) return null;
        try {
            int y = Integer.parseInt(m.group(1)), mo = Integer.parseInt(m.group(2)), d = Integer.parseInt(m.group(3)), h = Integer.parseInt(m.group(4)), mi = Integer.parseInt(m.group(5));
            if (mo < 1 || mo > 12 || d < 1 || d > 31 || h < 0 || h > 23 || mi < 0 || mi > 59) return null;
            return String.format("%04d/%02d/%02d-%02d:%02d", y, mo, d, h, mi);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class KeyListDTO {
        String name, expireTime, port, sourceTag;
        double balance, rate;
        boolean isEnable, enableWebHTML;

        public KeyListDTO(String n, double b, String et, String p, double r, boolean ie, boolean ew, String st) {
            this.name = n;
            this.balance = b;
            this.expireTime = et;
            this.port = p;
            this.rate = r;
            this.isEnable = ie;
            this.enableWebHTML = ew;
            this.sourceTag = st;
        }

        public KeyListDTO(SequenceKey k, String st) {
            this(k.getName(), k.getBalanceNoLock(), k.getExpireTime(), k.getPortStr(), k.getRateNoLock(), k.isEnable(), k.isHTMLEnabled(), st);
        }
    }
}