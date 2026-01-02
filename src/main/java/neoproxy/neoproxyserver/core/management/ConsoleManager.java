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

import static neoproxy.neoproxyserver.NeoProxyServer.*;
import static neoproxy.neoproxyserver.core.management.IPChecker.*;
import static neoproxy.neoproxyserver.core.management.SequenceKey.*;

/**
 * ConsoleManager (Industrial Fix)
 * 适配最新的异常抛出机制，确保在 Key 无效/过期时仍能通过缓存查询信息。
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

            // 【修复 3】: web 命令中的 isKeyExistsByName 检查
            if (!isKeyExistsByName(keyName)) {
                // 如果本地没有，尝试从缓存查（针对远程模式）
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
                SequenceKey keyFromDB = getKeyFromDB(keyName);
                if (keyFromDB != null) {
                    keyFromDB.setHTMLEnabled(enable);
                    if (saveToDB(keyFromDB)) {
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
            } catch (OutDatedKeyException | UnRecognizedKeyException | NoMoreNetworkFlowException e) {
                // 【修复 4】: 远程模式下 Key 异常，无法更新
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyStateError.cannotUpdate", e.getMessage());
            } catch (PortOccupiedException | NoMorePortException e) {
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyError", e.getMessage());
            }
        });

        registerWrapper("reload", "Reload configurations from config.cfg", (List<String> params) -> {
            if (!params.isEmpty()) {
                myConsole.warn(COMMAND_SOURCE.get(), "Usage: reload");
                return;
            }
            handleReloadCommand();
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
        SequenceKey.reloadProvider();
        ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.configReloaded");
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

        // 修改表头逻辑：从 ServerLogger 获取原始表头，然后手动插入 "Port" 列
        String rawHeaders = ServerLogger.getMessage("consoleManager.headers.list");
        String[] originalHeaders = rawHeaders.split("\\|");

        // 创建新的表头列表
        List<String> headerList = new ArrayList<>(Arrays.asList(originalHeaders));
        // 假设原始顺序为：IP | AccessCode | Location | ISP | Clients
        // 我们在 HostClient 中是在第 4 位 (索引从0开始) 插入的 Port (在 ISP 之后)
        // Header 对应位置应该是 Index 4
        if (headerList.size() >= 4) {
            headerList.add(4, "Port");
        } else {
            headerList.add("Port"); // 兜底
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

    // 【核心修复 1】: handleLookupCommand (key lp)
    private static void handleLookupCommand(List<String> params) {
        if (params.size() != 2) {
            myConsole.warn(COMMAND_SOURCE.get(), "Usage: key lp <name>");
            return;
        }
        String name = params.get(1);
        try {
            // 尝试正常获取 (如果 Key 正常，这里会返回对象)
            SequenceKey sequenceKey = getKeyFromDB(name);
            if (sequenceKey != null) {
                outputSingleKeyAsTable(sequenceKey);
            } else {
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFound");
            }
        } catch (OutDatedKeyException | UnRecognizedKeyException | NoMoreNetworkFlowException e) {
            // 捕获异常：说明远程 NKM 拒绝了该 Key (409/403)
            // 尝试从 Sync 缓存中获取最后已知状态
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

    // 【核心修复 2】: handleSetCommand
    private static void handleSetCommand(List<String> params) {
        if (params.size() < 2) {
            myConsole.warn(COMMAND_SOURCE.get(), "Usage: key set <name> [b=<balance>] [r=<rate>] [p=<outPort>] [t=<expireTime>] [w=<webHTML>]");
            return;
        }
        String name = params.get(1);

        // 检查是否正在使用远程 Provider，且该 Key 是否存在于远程缓存中
        if (SequenceKey.PROVIDER instanceof RemoteKeyProvider) {
            Map<String, SequenceKey> remoteCache = SequenceKey.getKeyCacheSnapshot();
            if (remoteCache.containsKey(name)) {
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.error.remoteKeyModification");
                return;
            }
        }

        // isKeyExistsByName 只检查 Cache，对于远程无效 Key 可能会返回 true (Cache中) 或 false (未知)
        // 但这里为了安全，还是保留检查
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
        } catch (OutDatedKeyException | UnRecognizedKeyException | NoMoreNetworkFlowException e) {
            // 如果是在 set 命令中抛出，说明该 Key 已失效且当前无活跃连接
            // 且因为是远程 Provider (否则 Local Provider 不会抛异常)，所以我们本就不该修改它
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.error.keyInvalidForSet", e.getMessage());
            return;
        } catch (PortOccupiedException | NoMorePortException e) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyError", e.getMessage());
            return;
        }

        boolean hasUpdate = false;
        String newPortStr = null;

        for (int i = 2; i < params.size(); i++) {
            String param = params.get(i);
            if (param.startsWith("b=")) {
                String balanceStr = param.substring(2);
                Double balance = parseDoubleSafely(balanceStr, "balance");
                if (balance != null) {
                    for (SequenceKey key : inMemoryKeysToUpdate) key.setBalance(balance);
                    if (dbKeySnapshot != null) dbKeySnapshot.setBalance(balance);
                    hasUpdate = true;
                }
            } else if (param.startsWith("r=")) {
                String rateStr = param.substring(2);
                Double rate = parseDoubleSafely(rateStr, "rate");
                if (rate != null) {
                    for (SequenceKey key : inMemoryKeysToUpdate) key.setRate(rate);
                    if (dbKeySnapshot != null) dbKeySnapshot.setRate(rate);
                    for (HostClient hc : hostClientsToUpdate) {
                        if (hc.getGlobalRateLimiter() != null) hc.getGlobalRateLimiter().setMaxMbps(rate);
                    }
                    hasUpdate = true;
                }
            } else if (param.startsWith("p=")) {
                String portStr = param.substring(2);
                String validatedPortStr = validateAndFormatPortInput(portStr);
                if (validatedPortStr != null) {
                    for (SequenceKey key : inMemoryKeysToUpdate) key.setPort(validatedPortStr);
                    if (dbKeySnapshot != null) dbKeySnapshot.setPort(validatedPortStr);
                    hasUpdate = true;
                    newPortStr = validatedPortStr;
                } else {
                    ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.invalidPortValue", portStr);
                }
            } else if (param.startsWith("t=")) {
                String expireTimeInput = param.substring(2);
                String expireTime = correctInputTime(expireTimeInput);
                if (expireTime == null) {
                    ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.illegalTimeInput", expireTimeInput);
                } else if (isOutOfDate(expireTime)) {
                    ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.timeEarlierThanCurrent", expireTime);
                } else {
                    for (SequenceKey key : inMemoryKeysToUpdate) key.setExpireTime(expireTime);
                    if (dbKeySnapshot != null) dbKeySnapshot.setExpireTime(expireTime);
                    hasUpdate = true;
                }
            } else if (param.startsWith("w=")) {
                String webStr = param.substring(2);
                boolean enable = "true".equalsIgnoreCase(webStr) || "1".equals(webStr) || "on".equalsIgnoreCase(webStr);
                for (SequenceKey key : inMemoryKeysToUpdate) key.setHTMLEnabled(enable);
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
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.portPolicyChanged", newPortStr);
            int disconnectedCount = 0;
            for (HostClient client : hostClientsToUpdate) {
                int currentExternalPort = client.getOutPort();
                if (!isPortInRange(currentExternalPort, newPortStr)) {
                    ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.disconnectingClient", name, String.valueOf(currentExternalPort));
                    client.close();
                    disconnectedCount++;
                }
            }
            if (disconnectedCount > 0) {
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.clientsDisconnected", String.valueOf(disconnectedCount));
            }
        }

        SequenceKey keyToSave = (dbKeySnapshot != null) ? dbKeySnapshot : inMemoryKeysToUpdate.getFirst();
        boolean isSuccess = saveToDB(keyToSave);
        if (isSuccess) {
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.operationComplete");
        } else {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.setKeyFailed");
        }
    }

    private static void handleAddCommand(List<String> params) {
        if (params.size() != 6 && params.size() != 7) {
            myConsole.warn(COMMAND_SOURCE.get(), "Usage: key add <name> <balance> <expireTime> <port> <rate> [webHTML]");
            myConsole.warn(COMMAND_SOURCE.get(), "Note: <port> can be a single number (e.g., 8080) or a range (e.g., 3344-3350).");
            return;
        }
        String name = params.get(1);

        if (isKeyExistsByName(name)) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.error.keyAlreadyExists");
            return;
        }

        String balanceStr = params.get(2);
        String expireTimeInput = params.get(3);
        String portStr = params.get(4);
        String rateStr = params.get(5);

        boolean enableWebHTML = false;
        if (params.size() == 7) {
            String webStr = params.get(6);
            enableWebHTML = "true".equalsIgnoreCase(webStr) || "1".equals(webStr) || "on".equalsIgnoreCase(webStr);
        }

        String expireTime = correctInputTime(expireTimeInput);
        if (expireTime == null) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.illegalTimeInput");
            return;
        }
        if (isOutOfDate(expireTime)) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.timeEarlierThanCurrent", expireTime);
            return;
        }
        Double balance = parseDoubleSafely(balanceStr, "balance");
        Double rate = parseDoubleSafely(rateStr, "rate");
        if (balance == null || rate == null) {
            return;
        }
        String validatedPortStr = validateAndFormatPortInput(portStr);
        if (validatedPortStr == null) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.invalidPortValue", portStr);
            return;
        }

        boolean isCreated = createNewKey(name, balance, expireTime, validatedPortStr, rate);
        if (isCreated) {
            ServerLogger.infoWithSource(COMMAND_SOURCE.get(), "consoleManager.keyCreated", name);
            if (enableWebHTML) {
                myConsole.execute("web enable " + name);
            } else {
                myConsole.execute("web disable " + name);
            }
        } else {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyCreateFailed");
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyCreateHint");
        }
    }

    private static void listAllKeys() {
        Map<String, KeyListDTO> keyMap = new HashMap<>();

        String sql = "SELECT name, balance, expireTime, port, rate, isEnable, enableWebHTML FROM sk";
        try (var conn = getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("name");
                KeyListDTO dto = new KeyListDTO(
                        name,
                        rs.getDouble("balance"),
                        rs.getString("expireTime"),
                        rs.getString("port"),
                        rs.getDouble("rate"),
                        rs.getBoolean("isEnable"),
                        rs.getBoolean("enableWebHTML"),
                        " (L)"
                );
                keyMap.put(name, dto);
            }
        } catch (Exception e) {
            Debugger.debugOperation(e);
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.dbQueryFailed");
            return;
        }

        if (SequenceKey.PROVIDER instanceof RemoteKeyProvider) {
            Map<String, SequenceKey> cache = SequenceKey.getKeyCacheSnapshot();
            for (SequenceKey k : cache.values()) {
                keyMap.put(k.getName(), new KeyListDTO(k, " (R)"));
            }
        }

        if (keyMap.isEmpty()) {
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFound");
            return;
        }

        List<KeyListDTO> sortedList = new ArrayList<>(keyMap.values());
        sortedList.sort((k1, k2) -> {
            boolean isR1 = k1.sourceTag.contains("(R)");
            boolean isR2 = k2.sourceTag.contains("(R)");
            if (isR1 && !isR2) return -1;
            if (!isR1 && isR2) return 1;
            return k1.name.compareToIgnoreCase(k2.name);
        });

        List<String[]> rows = new ArrayList<>();
        for (KeyListDTO dto : sortedList) {
            int clientNum = findKeyClientNum(dto.name);
            String formattedRate = killDoubleEndZero(dto.rate);
            String enableStatus = dto.isEnable ? "✓" : "✗";
            String webHTMLStatus = dto.enableWebHTML ? "✓" : "✗";

            String displayName = dto.name + dto.sourceTag;

            rows.add(new String[]{
                    displayName,
                    String.format("%.2f", dto.balance),
                    dto.expireTime,
                    dto.port,
                    formattedRate + "mbps",
                    enableStatus,
                    webHTMLStatus,
                    String.valueOf(clientNum)
            });
        }

        String[] headers = ServerLogger.getMessage("consoleManager.headers.keyList").split("\\|");
        printAsciiTable(headers, rows);
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
        myConsole.warn(COMMAND_SOURCE.get(), ServerLogger.getMessage("consoleManager.printKeyUsage.portNote"));
    }

    private static String validateAndFormatPortInput(String portInput) {
        if (portInput == null || portInput.trim().isEmpty()) return null;
        Matcher matcher = PORT_INPUT_REGEX.matcher(portInput.trim());
        if (!matcher.matches()) return null;
        try {
            int startPort = Integer.parseInt(matcher.group(1));
            if (startPort < 1 || startPort > 65535) return null;
            if (matcher.group(2) == null) return String.valueOf(startPort);
            int endPort = Integer.parseInt(matcher.group(2));
            if (endPort < 1 || endPort > 65535 || endPort < startPort) return null;
            return startPort + "-" + endPort;
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
                if (!output.isEmpty()) output.setLength(output.length() - separator.length());
                ServerLogger.infoWithSource(COMMAND_SOURCE.get(), output.toString());
            } else {
                ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.keyNotFound");
            }
        } catch (Exception e) {
            Debugger.debugOperation(e);
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.dbQueryFailed");
        }
    }

    private static void outputSingleKeyAsTable(SequenceKey sequenceKey) {
        try {
            String name = sequenceKey.getName();
            double balance = sequenceKey.getBalance();
            String expireTime = sequenceKey.getExpireTime();
            String portStr = sequenceKey.getPort() == -1 ? "Dynamic" : (sequenceKey.getDyStart() != sequenceKey.getDyEnd() ? sequenceKey.getDyStart() + "-" + sequenceKey.getDyEnd() : String.valueOf(sequenceKey.getDyStart()));
            try {
                // 尝试访问受保护字段 (如果在同一包下)
            } catch (Exception ignored) {
            }

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
            Debugger.debugOperation(e);
            ServerLogger.errorWithSource(COMMAND_SOURCE.get(), "consoleManager.failedToFormatKey");
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
        if (!matcher.matches()) return null;
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

    private static class KeyListDTO {
        String name;
        double balance;
        String expireTime;
        String port;
        double rate;
        boolean isEnable;
        boolean enableWebHTML;
        String sourceTag;

        public KeyListDTO(String name, double balance, String expireTime, String port, double rate, boolean isEnable, boolean enableWebHTML, String sourceTag) {
            this.name = name;
            this.balance = balance;
            this.expireTime = expireTime;
            this.port = port;
            this.rate = rate;
            this.isEnable = isEnable;
            this.enableWebHTML = enableWebHTML;
            this.sourceTag = sourceTag;
        }

        public KeyListDTO(SequenceKey key, String sourceTag) {
            this.name = key.getName();
            this.balance = key.getBalance();
            this.expireTime = key.getExpireTime();
            this.rate = key.getRate();
            this.isEnable = key.isEnable();
            this.enableWebHTML = key.isHTMLEnabled();
            this.sourceTag = sourceTag;
            this.port = key.getPort() == -1 ? "Dynamic" : String.valueOf(key.getPort());
        }
    }
}