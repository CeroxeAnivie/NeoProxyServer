package neoproxy.neoproxyserver.core.webadmin;

import neoproxy.neoproxyserver.NeoProxyServer;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.InternetProtocolStats;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SystemInfoHelper {

    private static final SystemInfo si = new SystemInfo();
    private static final HardwareAbstractionLayer hal = si.getHardware();
    private static final String cpuModelName = hal.getProcessor().getProcessorIdentifier().getName().trim();
    private static final OperatingSystem os = si.getOperatingSystem();
    private static final String osVersionStr = os.toString();

    public static String getSystemSnapshotJson() {
        CentralProcessor processor = hal.getProcessor();
        GlobalMemory memory = hal.getMemory();

        // 结合 JVM 方法获取实时负载以保证 0.5s 刷新率的性能
        com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();

        double systemCpu = Math.max(0, osBean.getCpuLoad());
        double processCpu = Math.max(0, osBean.getProcessCpuLoad());

        long totalMem = memory.getTotal();
        long availableMem = memory.getAvailable();
        long usedMem = totalMem - availableMem;

        // 【核心修改】
        // 优先使用oshi库获取当前Java进程的物理内存(RSS)，这更接近操作系统报告的值。
        // 如果获取失败，则回退到JVM堆内存计算，以确保兼容性。
        long usedJvm;
        try {
            OSProcess process = os.getProcess(os.getProcessId());
            usedJvm = process.getResidentSetSize();
        } catch (Exception e) {
            // 回退方案：使用JVM堆内存
            long totalJvm = Runtime.getRuntime().totalMemory();
            long freeJvm = Runtime.getRuntime().freeMemory();
            usedJvm = totalJvm - freeJvm;
        }

        File root = new File(NeoProxyServer.CURRENT_DIR_PATH);
        long totalSpace = root.getTotalSpace();
        long freeSpace = root.getUsableSpace();
        long usedSpace = totalSpace - freeSpace;

        return String.format(Locale.US,
                "{" +
                        "\"cpu\":{\"sys\":%.4f,\"proc\":%.4f,\"cores\":%d,\"model\":\"%s\"}," +
                        "\"mem\":{\"total\":%d,\"used\":%d,\"jvm\":%d}," +
                        "\"disk\":{\"total\":%d,\"used\":%d}," +
                        "\"os\":\"%s\"" +
                        "}",
                systemCpu, processCpu, processor.getLogicalProcessorCount(), escapeJson(cpuModelName),
                totalMem, usedMem, usedJvm,
                totalSpace, usedSpace,
                escapeJson(osVersionStr)
        );
    }

    public static String getPortUsageJson() {
        List<String> tcp = new ArrayList<>();
        List<String> udp = new ArrayList<>();

        InternetProtocolStats ips = os.getInternetProtocolStats();
        for (InternetProtocolStats.IPConnection conn : ips.getConnections()) {
            String type = conn.getType();
            String local = getIpString(conn.getLocalAddress()) + ":" + conn.getLocalPort();

            if ("tcp4".equalsIgnoreCase(type) || "tcp6".equalsIgnoreCase(type)) {
                if ("LISTEN".equalsIgnoreCase(conn.getState().toString())) tcp.add(local);
            } else if ("udp4".equalsIgnoreCase(type) || "udp6".equalsIgnoreCase(type)) {
                udp.add(local);
            }
        }
        return "{\"tcp\":" + listToJson(tcp) + ",\"udp\":" + listToJson(udp) + "}";
    }

    private static String getIpString(byte[] addr) {
        try {
            return java.net.InetAddress.getByAddress(addr).getHostAddress();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static String listToJson(List<String> l) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < l.size(); i++) {
            sb.append("\"").append(escapeJson(l.get(i))).append("\"");
            if (i < l.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}