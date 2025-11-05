package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.ServerLogger;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static neoproxy.neoproxyserver.NeoProxyServer.*;

/**
 * IP 封禁管理器。
 * 所有操作以 banList.txt 为唯一真实源，内存集合 (bannedIPSet) 和 SecureServerSocket.ignoreIPs 均为其同步缓存。
 */
public class IPChecker {

    public static final int DO_BAN = 0;       // 封禁 IP
    public static final int UNBAN = 1;        // 解封 IP
    public static final int CHECK_IS_BAN = 2; // 检查是否被封禁

    private static final File BAN_LIST_FILE = new File(System.getProperty("user.dir"), "banList.txt");
    private static final Set<String> bannedIPSet = new HashSet<>(); // 内存缓存，用于快速查询
    public static volatile boolean ENABLE_BAN = true;

    /**
     * 从 banList.txt 重新加载并同步到内存和 SecureServerSocket。
     * 此方法是同步的，确保一致性。
     */
    public static synchronized void reloadFromBanListFile() {
        Set<String> newSet = new HashSet<>();
        if (BAN_LIST_FILE.exists()) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(BAN_LIST_FILE), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        newSet.add(line);
                    }
                }
            } catch (IOException e) {
                debugOperation(e);
                ServerLogger.errorWithSource("IPChecker", "ipChecker.failedToReload", e.getMessage());
                return; // 保持旧状态
            }
        }

        // 原子性更新内存集合
        bannedIPSet.clear();
        bannedIPSet.addAll(newSet);

        // 同步到 SecureServerSocket
        syncIgnoreIPs();
    }

    /**
     * 将当前 bannedIPSet 同步到 SecureServerSocket.ignoreIPs。
     * 若 hostServerHookServerSocket 未初始化，则跳过。
     */
    private static void syncIgnoreIPs() {
        if (hostServerHookServerSocket == null) return;
        CopyOnWriteArrayList<String> ignoreIPs = hostServerHookServerSocket.getIgnoreIPs();
        ignoreIPs.clear();
        ignoreIPs.addAll(bannedIPSet);
    }

    /**
     * 执行封禁/解封/检查操作。
     */
    public static synchronized boolean exec(String ip, int execMode) {
        if (ip == null || ip.isEmpty()) return false;

        switch (execMode) {
            case DO_BAN:
                if (!ENABLE_BAN) return false;
                if (bannedIPSet.contains(ip)) return true; // 已存在

                // 踢出相关客户端
                for (HostClient client : availableHostClient) {
                    String addr = client.getHostServerHook().getInetAddress().getHostAddress();
                    if (ip.equals(addr)) {
                        client.close();
                    }
                }

                // 1. 更新内存
                bannedIPSet.add(ip);
                // 2. 追加到文件
                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(BAN_LIST_FILE, true), StandardCharsets.UTF_8))) {
                    writer.write(ip);
                    writer.newLine();
                    writer.flush();
                } catch (IOException e) {
                    // 回滚内存
                    bannedIPSet.remove(ip);
                    syncIgnoreIPs();
                    debugOperation(e);
                    ServerLogger.errorWithSource("IPChecker", "ipChecker.failedToBan", ip);
                    return false;
                }
                // 3. 同步到 ignoreIPs
                syncIgnoreIPs();
                ServerLogger.infoWithSource("IPChecker", "ipChecker.ipBanned", ip);
                return true;

            case UNBAN:
                if (!bannedIPSet.contains(ip)) return true; // 不存在即成功

                // 1. 从内存移除
                bannedIPSet.remove(ip);
                // 2. 重写文件（过滤掉该 IP）
                try {
                    List<String> lines = Files.readAllLines(BAN_LIST_FILE.toPath(), StandardCharsets.UTF_8);
                    try (BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(new FileOutputStream(BAN_LIST_FILE), StandardCharsets.UTF_8))) {
                        for (String line : lines) {
                            if (!ip.equals(line.trim())) {
                                writer.write(line);
                                writer.newLine();
                            }
                        }
                    }
                } catch (IOException e) {
                    // 回滚内存
                    bannedIPSet.add(ip);
                    syncIgnoreIPs();
                    debugOperation(e);
                    ServerLogger.errorWithSource("IPChecker", "ipChecker.failedToUnban", ip);
                    return false;
                }
                // 3. 同步到 ignoreIPs
                syncIgnoreIPs();
                ServerLogger.infoWithSource("IPChecker", "ipChecker.ipUnbanned", ip);
                return true;

            case CHECK_IS_BAN:
                return ENABLE_BAN && bannedIPSet.contains(ip);

            default:
                return false;
        }
    }

    /**
     * 启动时加载 banList.txt 并初始化。
     */
    public static void loadBannedIPs() {
        if (!BAN_LIST_FILE.exists()) {
            try {
                BAN_LIST_FILE.createNewFile();
                ServerLogger.infoWithSource("IPChecker", "ipChecker.createdEmptyBanList");
            } catch (IOException e) {
                ServerLogger.warnWithSource("IPChecker", "ipChecker.failedToCreateFile");
            }
        }
        reloadFromBanListFile(); // 统一入口
    }

    /**
     * 获取当前封禁列表的不可变副本（用于展示）。
     */
    public static Set<String> getBannedIPs() {
        return new HashSet<>(bannedIPSet);
    }

    public static boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        try {
            // 尝试将字符串解析为 InetAddress
            InetAddress inetAddress = InetAddress.getByName(ip);

            // 关键检查：解析后的规范地址必须与原始输入相同。
            // 这可以过滤掉像 "www.google.com" 这样的主机名，
            // 因为 getHostAddress() 会返回其 IP 地址，而不是原始字符串。
            return ip.equals(inetAddress.getHostAddress());
        } catch (UnknownHostException e) {
            // 如果字符串既不是有效的 IP 地址，也不是可解析的主机名，则会抛出此异常。
            return false;
        }
    }

    /**
     * 控制台打印封禁列表（格式化表格，宽度自适应）。
     */
    public static void listBannedIPs() {
        if (!BAN_LIST_FILE.exists()) {
            ServerLogger.warnWithSource("Admin", "ipChecker.banListFileNotFound");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(BAN_LIST_FILE.toPath(), StandardCharsets.UTF_8);
            lines.removeIf(String::isEmpty);

            if (lines.isEmpty()) {
                ServerLogger.infoWithSource("Admin", "ipChecker.banListIsEmpty");
                return;
            }

            // 1. 计算内容的最大宽度
            String header = "Banned IP";
            // 使用 Stream API 找到最长的 IP 地址长度
            int maxIpLength = lines.stream()
                    .map(String::trim)
                    .mapToInt(String::length)
                    .max()
                    .orElse(0);

            // 表格内容的宽度 = 表头长度 和 最长IP长度 中的较大者
            int contentWidth = Math.max(header.length(), maxIpLength);

            // 2. 根据最大宽度创建可复用的表格组件
            // 水平线的长度 = 内容宽度 + 两侧的填充空格
            String horizontalLine = "─".repeat(contentWidth + 2);
            // 行格式化字符串，例如 "│ %-20s │\n"
            String rowFormat = "│ %-" + contentWidth + "s │\n";

            // 3. 使用动态组件构建表格
            StringBuilder sb = new StringBuilder();
            sb.append("\n┌").append(horizontalLine).append("┐\n");
            sb.append(String.format(rowFormat, header)); // 表头
            sb.append("├").append(horizontalLine).append("┤\n");
            for (String ip : lines) {
                sb.append(String.format(rowFormat, ip.trim())); // IP 数据行
            }
            sb.append("└").append(horizontalLine).append("┘");

            ServerLogger.logRaw("Admin", sb.toString());

        } catch (IOException e) {
            debugOperation(e);
            ServerLogger.errorWithSource("Admin", "ipChecker.failedToReadBanList");
        }
    }
}