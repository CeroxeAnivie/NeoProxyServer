package neoproject.neoproxy.core.management;

import neoproject.neoproxy.core.HostClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static neoproject.neoproxy.NeoProxyServer.*;

/**
 * IP 检查工具类，用于管理 IP 封禁列表。
 * 重构以提高代码清晰度和可维护性。
 */
public class IPChecker {

    public static final int DO_BAN = 0;       // 执行封禁操作
    public static final int UNBAN = 1; // 检查 IP 是否被封禁
    public static final int CHECK_IS_BAN = 2; // 检查 IP 是否被封禁


    // 封禁列表文件路径
    private static final File bannedIPList = new File(System.getProperty("user.dir") + File.separator + "banList.txt");

    // 内存中的封禁 IP 集合，用于快速查询
    private static final Set<String> bannedIPSet = new HashSet<>();

    // 启用封禁功能的开关
    public static boolean ENABLE_BAN = true;

    /**
     * 执行 IP 检查操作（封禁或检查）。
     *
     * @param ip       要操作的 IP 地址
     * @param execMode 操作模式：DO_BAN 或 CHECK_IS_BAN
     * @return 对于 CHECK_IS_BAN，返回 true 表示 IP 已被封禁；对于 DO_BAN，返回 false（操作本身不返回 true）。
     */
    public static synchronized boolean exec(String ip, int execMode) {
        if (execMode == DO_BAN) {
            if (ENABLE_BAN) {

                // 将 IP 添加到内存集合
                bannedIPSet.add(ip);
                for (HostClient hostClient : availableHostClient) {//先踢出所有跟这个 ip 相关的 host client
                    String hostAddress=hostClient.getHostServerHook().getInetAddress().getHostAddress();
                    if (hostAddress.equals(ip)){
                        hostClient.close();
                    }
                }

                // 将 IP 追加写入文件
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(bannedIPList, StandardCharsets.UTF_8, true))) {
                    writer.write(ip);
                    writer.newLine();
                    writer.flush();
                    myConsole.log("IPChecker", "IP " + ip + " has been banned.");
                    return true;
                } catch (IOException e) {
                    debugOperation(e);
                    myConsole.error("IPChecker", "Failed to write IP " + ip + " to banList.txt: ");
                    bannedIPSet.remove(ip);
                }
            }
            return false;
        } else if (execMode == UNBAN) {
            if (ip == null || ip.isEmpty()) return false;
            String banListPath = System.getProperty("user.dir") + java.io.File.separator + "banList.txt";
            File banFile = new File(banListPath);

            if (!banFile.exists()) {
                bannedIPSet.remove(ip);
                return true; // 文件不存在，认为 IP 未被封禁
            }

            try {
                List<String> lines = Files.readAllLines(Paths.get(banListPath));
                boolean removed = lines.removeIf(line -> line.equals(ip));

                if (removed) {
                    // 写回文件
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(banFile))) {
                        for (String line : lines) {
                            writer.write(line);
                            writer.newLine();
                        }
                    }
                    bannedIPSet.remove(ip);
                }
                return removed;
            } catch (IOException e) {
                debugOperation(e);
                return false;
            }
        } else if (execMode == CHECK_IS_BAN) {
            if (!ENABLE_BAN) {
                return false; // 封禁功能未启用，直接返回 false
            }
            // 检查 IP 是否存在于内存集合中
            return bannedIPSet.contains(ip);
        }
        return false; // 默认返回 false
    }

    /**
     * 从文件加载封禁 IP 列表到内存集合中。
     */
    public static void loadBannedIPs() {
        try {
            if (!bannedIPList.exists()) {
                // 如果文件不存在，创建一个空文件
                boolean created = bannedIPList.createNewFile();
                if (!created) {
                    if (myConsole != null) {
                        myConsole.warn("IPChecker", "Failed to create banList.txt file.");
                    }
                }
                return; // 文件不存在或创建失败，集合保持为空
            }

            // 读取文件内容并填充集合
            try (BufferedReader reader = new BufferedReader(new FileReader(bannedIPList, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim(); // 去除首尾空白
                    if (!line.isEmpty()) {
                        bannedIPSet.add(line);
                    }
                }
                if (myConsole != null) {
                    myConsole.log("IPChecker", "Loaded " + bannedIPSet.size() + " banned IPs from " + bannedIPList.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            debugOperation(e);
            if (myConsole != null) {
                myConsole.error("IPChecker", "Failed to load banList.txt: " + e.getMessage());
            }
            System.exit(-1); // 文件读取失败是严重错误
        }
    }

    /**
     * 获取当前内存中的封禁 IP 集合的副本。
     * 用于控制台指令展示列表。
     *
     * @return 封禁 IP 集合的副本
     */
    public static Set<String> getBannedIPs() {
        return new HashSet<>(bannedIPSet);
    }

    // ==================== IP 封禁相关辅助方法 ====================

    /**
     * 验证 IP 地址格式是否有效 (IPv4)
     */
    public static boolean isValidIP(String ip) {
        if (ip == null) return false;
        String regex = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$";
        return ip.matches(regex);
    }

    /**
     * 列出所有被封禁的 IP 地址。
     */
    public static void listBannedIPs() {
        String banListPath = System.getProperty("user.dir") + java.io.File.separator + "banList.txt";
        File banFile = new File(banListPath);

        if (!banFile.exists()) {
            myConsole.warn("Admin", "Ban list file does not exist.");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(Paths.get(banListPath));
            if (lines.isEmpty()) {
                myConsole.log("Admin", "Ban list is empty.");
                return;
            }

            StringBuilder output = new StringBuilder();
            output.append("\n"); // Use \n
            output.append("┌").append("─".repeat(22)).append("┐\n"); // Use \n
            output.append("│ ").append(String.format("%-20s", "Banned IP")).append(" │\n"); // Use \n
            output.append("├").append("─".repeat(22)).append("┤\n"); // Use \n
            for (String ip : lines) {
                if (!ip.trim().isEmpty()) { // 过滤空行
                    output.append("│ ").append(String.format("%-20s", ip.trim())).append(" │\n"); // Use \n
                }
            }
            output.append("└").append("─".repeat(22)).append("┘"); // No \n at the very end if not desired

            myConsole.log("Admin", output.toString());
        } catch (IOException e) {
            debugOperation(e);
            myConsole.error("Admin", "Failed to read ban list file.");
        }
    }
}