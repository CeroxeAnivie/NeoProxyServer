package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.ServerLogger;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static neoproxy.neoproxyserver.NeoProxyServer.availableHostClient;
import static neoproxy.neoproxyserver.NeoProxyServer.hostServerHookServerSocket;

/**
 * IP 封禁管理器 (JSON版 - 格式化存储 - 无时间戳)
 */
public class IPChecker {

    public static final int DO_BAN = 0;
    public static final int UNBAN = 1;
    public static final int CHECK_IS_BAN = 2;

    private static final File BAN_LIST_JSON = new File(System.getProperty("user.dir"), "banList.json");
    private static final File BAN_LIST_TXT_OLD = new File(System.getProperty("user.dir"), "banList.txt");

    private static final Map<String, BanInfo> bannedIPMap = new ConcurrentHashMap<>();

    private static final String IPV4_REGEX =
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    private static final Pattern IPV4_PATTERN = Pattern.compile(IPV4_REGEX);
    public static volatile boolean ENABLE_BAN = true;

    public static synchronized void loadBannedIPs() {
        if (BAN_LIST_TXT_OLD.exists() && !BAN_LIST_JSON.exists()) {
            ServerLogger.infoWithSource("IPChecker", "ipChecker.migrating", "banList.txt -> banList.json");
            migrateTxtToJson();
        }
        reloadFromJson();
    }

    private static void migrateTxtToJson() {
        List<BanInfo> tempList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(BAN_LIST_TXT_OLD), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String ip = line.trim();
                if (!ip.isEmpty() && isValidIP(ip)) {
                    IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.getLocationInfo(ip);
                    // 不再记录时间
                    tempList.add(new BanInfo(ip, info.location(), info.isp()));
                }
            }
        } catch (IOException e) {
            ServerLogger.errorWithSource("IPChecker", "ipChecker.migrationFailed", e.getMessage());
            return;
        }

        bannedIPMap.clear();
        for (BanInfo info : tempList) {
            bannedIPMap.put(info.ip, info);
        }
        saveToJson();

        try {
            Files.move(BAN_LIST_TXT_OLD.toPath(),
                    new File(System.getProperty("user.dir"), "banList.txt.bak").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            ServerLogger.warnWithSource("IPChecker", "ipChecker.failedToRenameOldTxt");
        }
    }

    private static void reloadFromJson() {
        if (!BAN_LIST_JSON.exists()) {
            try {
                BAN_LIST_JSON.createNewFile();
                saveToJson();
            } catch (IOException e) {
                ServerLogger.warnWithSource("IPChecker", "ipChecker.failedToCreateJson");
            }
            return;
        }

        try {
            String content = Files.readString(BAN_LIST_JSON.toPath(), StandardCharsets.UTF_8);
            if (content == null || content.isBlank()) return;

            // 简单的正则匹配：只提取 ip, location, isp
            Pattern pattern = Pattern.compile("\"ip\":\\s*\"(.*?)\"[\\s\\S]*?\"location\":\\s*\"(.*?)\"[\\s\\S]*?\"isp\":\\s*\"(.*?)\"");
            Matcher matcher = pattern.matcher(content);

            Map<String, BanInfo> newMap = new ConcurrentHashMap<>();
            while (matcher.find()) {
                String ip = matcher.group(1);
                String loc = matcher.group(2);
                String isp = matcher.group(3);
                newMap.put(ip, new BanInfo(ip, loc, isp));
            }

            bannedIPMap.clear();
            bannedIPMap.putAll(newMap);
            syncIgnoreIPs();

        } catch (IOException e) {
            ServerLogger.errorWithSource("IPChecker", "ipChecker.failedToReadJson", e.getMessage());
        }
    }

    /**
     * 保存格式化的 JSON (Pretty Print)
     */
    private static synchronized boolean saveToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        Iterator<BanInfo> iterator = bannedIPMap.values().iterator();
        while (iterator.hasNext()) {
            BanInfo info = iterator.next();
            sb.append("  {\n");
            sb.append(String.format("    \"ip\": \"%s\",\n", info.ip));
            sb.append(String.format("    \"location\": \"%s\",\n", escapeJson(info.location)));
            sb.append(String.format("    \"isp\": \"%s\"\n", escapeJson(info.isp))); // 最后一项不带逗号
            sb.append("  }");

            if (iterator.hasNext()) {
                sb.append(",\n");
            } else {
                sb.append("\n");
            }
        }
        sb.append("]");

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(BAN_LIST_JSON), StandardCharsets.UTF_8))) {
            writer.write(sb.toString());
            writer.flush();
            return true;
        } catch (IOException e) {
            ServerLogger.errorWithSource("IPChecker", "ipChecker.failedToWriteJson", e.getMessage());
            return false;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void syncIgnoreIPs() {
        if (hostServerHookServerSocket == null) return;
        CopyOnWriteArrayList<String> ignoreIPs = hostServerHookServerSocket.getIgnoreIPs();
        ignoreIPs.clear();
        ignoreIPs.addAll(bannedIPMap.keySet());
    }

    public static synchronized boolean exec(String ip, int execMode) {
        if (ip == null || ip.isEmpty()) return false;

        switch (execMode) {
            case DO_BAN:
                if (!ENABLE_BAN) return false;
                if (bannedIPMap.containsKey(ip)) return true;

                for (HostClient client : availableHostClient) {
                    String addr = client.getHostServerHook().getInetAddress().getHostAddress();
                    if (ip.equals(addr)) {
                        client.close();
                    }
                }

                IPGeolocationHelper.LocationInfo locInfo = IPGeolocationHelper.getLocationInfo(ip);
                // 不记录时间
                BanInfo info = new BanInfo(ip, locInfo.location(), locInfo.isp());

                bannedIPMap.put(ip, info);

                if (saveToJson()) {
                    syncIgnoreIPs();
                    ServerLogger.infoWithSource("IPChecker", "ipChecker.ipBanned", ip + " (" + info.location + ")");
                    return true;
                } else {
                    bannedIPMap.remove(ip);
                    return false;
                }

            case UNBAN:
                if (!bannedIPMap.containsKey(ip)) return true;

                bannedIPMap.remove(ip);
                if (saveToJson()) {
                    syncIgnoreIPs();
                    ServerLogger.infoWithSource("IPChecker", "ipChecker.ipUnbanned", ip);
                    return true;
                } else {
                    reloadFromJson();
                    return false;
                }

            case CHECK_IS_BAN:
                return ENABLE_BAN && bannedIPMap.containsKey(ip);

            default:
                return false;
        }
    }

    public static Set<String> getBannedIPs() {
        return new HashSet<>(bannedIPMap.keySet());
    }

    public static boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        if (!IPV4_PATTERN.matcher(ip).matches()) return false;
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static void listBannedIPs() {
        if (bannedIPMap.isEmpty()) {
            ServerLogger.infoWithSource("Admin", "ipChecker.banListIsEmpty");
            return;
        }

        int wIp = 15;
        int wLoc = 10;
        int wIsp = 10;

        for (BanInfo i : bannedIPMap.values()) {
            wIp = Math.max(wIp, getDisplayWidth(i.ip));
            wLoc = Math.max(wLoc, getDisplayWidth(i.location));
            wIsp = Math.max(wIsp, getDisplayWidth(i.isp));
        }

        // 不再显示时间列
        String format = "│ %-" + wIp + "s │ %-" + wLoc + "s │ %-" + wIsp + "s │\n";
        String border = "─".repeat(wIp + 2) + "┬" + "─".repeat(wLoc + 2) + "┬" + "─".repeat(wIsp + 2);

        StringBuilder sb = new StringBuilder();
        sb.append("\n┌").append(border.replace("┬", "─")).append("┐\n");
        sb.append(String.format(format, "IP Address", "Location", "ISP"));
        sb.append("├").append(border).append("┤\n");

        for (BanInfo i : bannedIPMap.values()) {
            sb.append(String.format(format, i.ip, i.location, i.isp));
        }
        sb.append("└").append(border.replace("┬", "─")).append("┘");

        ServerLogger.logRaw("Admin", sb.toString());
    }

    private static int getDisplayWidth(String s) {
        if (s == null) return 0;
        int len = 0;
        for (char c : s.toCharArray()) {
            len += (c > 127) ? 2 : 1;
        }
        return len;
    }

    // 数据结构：仅保留 IP、位置、运营商
    public static class BanInfo {
        String ip;
        String location;
        String isp;

        public BanInfo(String ip, String location, String isp) {
            this.ip = ip;
            this.location = location;
            this.isp = isp;
        }
    }
}