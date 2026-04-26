package neoproxy.neoproxyserver.core.management;

import com.sun.management.OperatingSystemMXBean;
import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.Debugger;
import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.threads.UDPTransformer;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 性能诊断报告生成器
 *
 * <p>该类用于生成详细的系统性能诊断报告，帮助定位CPU、内存、线程等性能瓶颈。
 * 当系统出现性能问题时，可通过控制台执行 profile 命令生成诊断报告。</p>
 *
 * <p>报告内容包括：</p>
 * <ul>
 *   <li>系统概览：CPU、内存、磁盘使用情况</li>
 *   <li>JVM信息：堆内存、GC统计、线程概览</li>
 *   <li>线程分析：线程状态分布、线程组分布、CPU占用线程、阻塞线程</li>
 *   <li>网络连接状态：客户端连接数、流量统计</li>
 *   <li>密钥状态：活跃密钥及其流量使用情况</li>
 *   <li>性能瓶颈分析：自动识别问题并提供优化建议</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 * String reportPath = ProfileReporter.generateAndSaveReport();
 * if (reportPath != null) {
 *     System.out.println("报告已生成: " + reportPath);
 * }
 * </pre>
 *
 * @author NeoProxyServer Team
 * @version 1.0
 * @since 6.1.0
 */
public final class ProfileReporter {

    /**
     * OSHI系统信息对象，用于获取硬件和操作系统信息
     */
    private static final SystemInfo SYSTEM_INFO = new SystemInfo();

    /**
     * 硬件抽象层，用于访问CPU、内存等硬件信息
     */
    private static final HardwareAbstractionLayer HAL = SYSTEM_INFO.getHardware();

    /**
     * 操作系统对象，用于获取进程信息
     */
    private static final OperatingSystem OS = SYSTEM_INFO.getOperatingSystem();

    /**
     * Java运行时对象，用于获取JVM内存信息
     */
    private static final Runtime RUNTIME = Runtime.getRuntime();

    /**
     * 操作系统MXBean，用于获取系统CPU和进程CPU使用率
     */
    private static final OperatingSystemMXBean OS_BEAN =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    /**
     * 内存MXBean，用于获取JVM内存使用情况
     */
    private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();

    /**
     * 线程MXBean，用于获取线程信息和CPU时间
     */
    private static final ThreadMXBean THREAD_BEAN = ManagementFactory.getThreadMXBean();

    /**
     * GC MXBean列表，用于获取垃圾收集统计信息
     */
    private static final List<GarbageCollectorMXBean> GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();

    /**
     * 私有构造函数，防止实例化
     *
     * <p>该类仅提供静态方法，不应被实例化。</p>
     */
    private ProfileReporter() {
    }

    /**
     * 生成并保存性能诊断报告
     *
     * <p>该方法会收集系统、JVM、线程、网络连接等多维度信息，
     * 生成详细的诊断报告并保存到当前工作目录。</p>
     *
     * <p>报告文件名格式：profile_yyyyMMdd_HHmmss_SSS.txt</p>
     *
     * @return 报告文件的绝对路径；如果生成失败则返回null
     */
    public static String generateAndSaveReport() {
        Debugger.debugOperation("Starting profile report generation...");

        // 生成时间戳，精确到毫秒，确保文件名唯一性
        Calendar cal = Calendar.getInstance();
        String timestamp = String.format("%04d%02d%02d_%02d%02d%02d_%03d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND),
                cal.get(Calendar.MILLISECOND));

        // 构建报告文件路径
        String fileName = "profile_" + timestamp + ".txt";
        File reportFile = new File(NeoProxyServer.CURRENT_DIR_PATH, fileName);

        // 构建报告内容
        StringBuilder report = new StringBuilder();

        // 按章节生成报告内容
        report.append(generateHeader(timestamp));
        report.append(generateSystemOverview());
        report.append(generateJvmInfo());
        report.append(generateThreadAnalysis());
        report.append(generateNetworkConnections());
        report.append(generateKeyStatus());
        report.append(generatePerformanceBottlenecks());
        report.append(generateFooter());

        // 写入文件，使用UTF-8编码确保中文正确显示
        try (FileWriter writer = new FileWriter(reportFile, StandardCharsets.UTF_8)) {
            writer.write(report.toString());
            Debugger.debugOperation("Profile report saved to: " + reportFile.getAbsolutePath());
            return reportFile.getAbsolutePath();
        } catch (IOException e) {
            Debugger.debugOperation(e);
            ServerLogger.errorWithSource("ProfileReporter", "Failed to save profile report: " + e.getMessage());
            return null;
        }
    }

    /**
     * 生成报告头部
     *
     * <p>包含报告标题、生成时间、服务器版本、工作目录等基本信息。</p>
     *
     * @param timestamp 时间戳字符串
     * @return 报告头部内容
     */
    private static String generateHeader(String timestamp) {
        StringBuilder sb = new StringBuilder();

        // 报告标题，使用Unicode边框字符美化显示
        sb.append("╔══════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                        NeoProxyServer 性能诊断报告                            ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════════════════════╝\n");
        sb.append("\n");

        // 基本信息
        sb.append("生成时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        sb.append("服务器版本: ").append(NeoProxyServer.VERSION).append("\n");
        sb.append("工作目录: ").append(NeoProxyServer.CURRENT_DIR_PATH).append("\n");
        sb.append("\n");

        return sb.toString();
    }

    /**
     * 生成系统概览章节
     *
     * <p>包含CPU信息、内存信息、磁盘信息三个部分。</p>
     *
     * @return 系统概览章节内容
     */
    private static String generateSystemOverview() {
        StringBuilder sb = new StringBuilder();

        // 章节标题
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("                              【系统概览】                                      \n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // 获取CPU和内存硬件信息
        CentralProcessor processor = HAL.getProcessor();
        GlobalMemory memory = HAL.getMemory();

        // 计算CPU使用率，确保非负值
        double systemCpu = Math.max(0, OS_BEAN.getCpuLoad() * 100);
        double processCpu = Math.max(0, OS_BEAN.getProcessCpuLoad() * 100);
        int availableProcessors = processor.getLogicalProcessorCount();
        String cpuModel = processor.getProcessorIdentifier().getName().trim();

        // CPU信息部分
        sb.append("┌─ CPU 信息 ─────────────────────────────────────────────────────────────────┐\n");
        sb.append(String.format("│  CPU 型号: %-64s│\n", truncate(cpuModel, 64)));
        sb.append(String.format("│  逻辑核心数: %-61d│\n", availableProcessors));
        sb.append(String.format("│  系统CPU使用率: %-57.2f%%│\n", systemCpu));
        sb.append(String.format("│  进程CPU使用率: %-57.2f%%│\n", processCpu));
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        // 计算系统内存使用情况
        long totalMem = memory.getTotal();
        long availableMem = memory.getAvailable();
        long usedMem = totalMem - availableMem;
        double memUsagePercent = (double) usedMem / totalMem * 100;

        // 获取JVM堆内存信息
        long jvmHeapTotal = RUNTIME.totalMemory();
        long jvmHeapFree = RUNTIME.freeMemory();
        long jvmHeapUsed = jvmHeapTotal - jvmHeapFree;
        long jvmHeapMax = RUNTIME.maxMemory();

        // 获取JVM实际物理内存占用(RSS)
        long jvmRss = getJvmRss();

        // 内存信息部分
        sb.append("┌─ 内存信息 ─────────────────────────────────────────────────────────────────┐\n");
        sb.append(String.format("│  系统总内存: %-60s│\n", formatBytes(totalMem)));
        sb.append(String.format("│  系统已用内存: %-57s (%.1f%%)│\n", formatBytes(usedMem), memUsagePercent));
        sb.append(String.format("│  系统可用内存: %-60s│\n", formatBytes(availableMem)));
        sb.append("│                                                                            │\n");
        sb.append(String.format("│  JVM堆内存(已用): %-54s│\n", formatBytes(jvmHeapUsed)));
        sb.append(String.format("│  JVM堆内存(已提交): %-52s│\n", formatBytes(jvmHeapTotal)));
        sb.append(String.format("│  JVM堆内存(最大): %-54s│\n", formatBytes(jvmHeapMax)));
        sb.append(String.format("│  JVM物理内存(RSS): %-53s│\n", formatBytes(jvmRss)));
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        // 获取磁盘使用情况
        File root = new File(NeoProxyServer.CURRENT_DIR_PATH);
        long totalSpace = root.getTotalSpace();
        long freeSpace = root.getUsableSpace();
        long usedSpace = totalSpace - freeSpace;
        double diskUsagePercent = (double) usedSpace / totalSpace * 100;

        // 磁盘信息部分
        sb.append("┌─ 磁盘信息 ─────────────────────────────────────────────────────────────────┐\n");
        sb.append(String.format("│  磁盘总容量: %-61s│\n", formatBytes(totalSpace)));
        sb.append(String.format("│  磁盘已用: %-61s (%.1f%%)│\n", formatBytes(usedSpace), diskUsagePercent));
        sb.append(String.format("│  磁盘可用: %-63s│\n", formatBytes(freeSpace)));
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        return sb.toString();
    }

    /**
     * 生成JVM信息章节
     *
     * <p>包含内存池、GC统计、线程概览三个部分。</p>
     *
     * @return JVM信息章节内容
     */
    private static String generateJvmInfo() {
        StringBuilder sb = new StringBuilder();

        // 章节标题
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("                              【JVM 信息】                                      \n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // 获取堆内存和非堆内存使用情况
        MemoryUsage heapUsage = MEMORY_BEAN.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = MEMORY_BEAN.getNonHeapMemoryUsage();

        // 内存池信息
        sb.append("┌─ 内存池 ───────────────────────────────────────────────────────────────────┐\n");
        sb.append(String.format("│  堆内存(已用): %-58s│\n", formatBytes(heapUsage.getUsed())));
        sb.append(String.format("│  堆内存(已提交): %-56s│\n", formatBytes(heapUsage.getCommitted())));
        sb.append(String.format("│  堆内存(最大): %-60s│\n", formatBytes(heapUsage.getMax())));
        sb.append("│                                                                            │\n");
        sb.append(String.format("│  非堆内存(已用): %-56s│\n", formatBytes(nonHeapUsage.getUsed())));
        sb.append(String.format("│  非堆内存(已提交): %-54s│\n", formatBytes(nonHeapUsage.getCommitted())));
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        // GC统计信息
        sb.append("┌─ GC 统计 ──────────────────────────────────────────────────────────────────┐\n");
        for (GarbageCollectorMXBean gcBean : GC_BEANS) {
            String gcName = gcBean.getName();
            long gcCount = gcBean.getCollectionCount();
            long gcTime = gcBean.getCollectionTime();
            sb.append(String.format("│  %-20s: 收集次数=%-10d 总耗时=%-10dms│\n",
                    gcName, gcCount, gcTime));
        }
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        // 线程概览信息
        int threadCount = THREAD_BEAN.getThreadCount();
        int peakThreadCount = THREAD_BEAN.getPeakThreadCount();
        int daemonThreadCount = THREAD_BEAN.getDaemonThreadCount();
        long totalStartedThreads = THREAD_BEAN.getTotalStartedThreadCount();

        sb.append("┌─ 线程概览 ─────────────────────────────────────────────────────────────────┐\n");
        sb.append(String.format("│  活跃线程数: %-61d│\n", threadCount));
        sb.append(String.format("│  峰值线程数: %-61d│\n", peakThreadCount));
        sb.append(String.format("│  守护线程数: %-61d│\n", daemonThreadCount));
        sb.append(String.format("│  累计启动线程数: %-56d│\n", totalStartedThreads));
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        return sb.toString();
    }

    /**
     * 生成线程分析章节
     *
     * <p>包含线程状态分布、线程组分布、CPU占用最高的线程、阻塞线程四个部分。</p>
     *
     * @return 线程分析章节内容
     */
    private static String generateThreadAnalysis() {
        StringBuilder sb = new StringBuilder();

        // 章节标题
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("                            【线程分析】                                        \n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // 初始化线程状态计数器
        Map<Thread.State, Integer> stateCount = new EnumMap<>(Thread.State.class);
        for (Thread.State state : Thread.State.values()) {
            stateCount.put(state, 0);
        }

        // 线程组计数器
        Map<String, Integer> threadGroupCount = new HashMap<>();

        // 获取所有线程信息，栈深度为8
        int threadCount = THREAD_BEAN.getThreadCount();
        long[] threadIds = THREAD_BEAN.getAllThreadIds();
        ThreadInfo[] threadInfos = THREAD_BEAN.getThreadInfo(threadIds, 8);

        // 统计线程状态和线程组
        for (ThreadInfo info : threadInfos) {
            if (info != null) {
                Thread.State state = info.getThreadState();
                stateCount.put(state, stateCount.get(state) + 1);

                String threadName = info.getThreadName();
                String groupName = extractThreadGroup(threadName);
                threadGroupCount.merge(groupName, 1, Integer::sum);
            }
        }

        // 线程状态分布
        sb.append("┌─ 线程状态分布 ─────────────────────────────────────────────────────────────┐\n");
        for (Map.Entry<Thread.State, Integer> entry : stateCount.entrySet()) {
            if (entry.getValue() > 0) {
                sb.append(String.format("│  %-25s: %5d 个线程                          │\n",
                        entry.getKey(), entry.getValue()));
            }
        }
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        // 线程组分布（显示Top 10）
        sb.append("┌─ 线程组分布 (Top 10) ──────────────────────────────────────────────────────┐\n");
        threadGroupCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    sb.append(String.format("│  %-50s: %5d│\n",
                            truncate(entry.getKey(), 50), entry.getValue()));
                });
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        // CPU占用最高的线程（Top 10）
        sb.append("┌─ CPU占用最高的线程 (Top 10) ───────────────────────────────────────────────┐\n");

        // 筛选非阻塞线程作为CPU密集型线程候选
        List<ThreadInfo> cpuIntensiveThreads = new ArrayList<>();
        for (ThreadInfo info : threadInfos) {
            if (info != null && info.getThreadState() != Thread.State.BLOCKED
                    && info.getThreadState() != Thread.State.WAITING
                    && info.getThreadState() != Thread.State.TIMED_WAITING) {
                cpuIntensiveThreads.add(info);
            }
        }

        // 显示CPU密集型线程信息
        cpuIntensiveThreads.stream()
                .limit(10)
                .forEach(info -> {
                    sb.append(String.format("│  线程ID: %-8d 状态: %-20s│\n",
                            info.getThreadId(), info.getThreadState()));
                    sb.append(String.format("│  名称: %-68s│\n",
                            truncate(info.getThreadName(), 68)));

                    // 显示栈顶元素，帮助定位问题
                    StackTraceElement[] stack = info.getStackTrace();
                    if (stack.length > 0) {
                        sb.append(String.format("│    → %-66s│\n",
                                truncate(stack[0].toString(), 66)));
                    }
                    sb.append("│                                                                            │\n");
                });
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        // 阻塞线程（最多显示5个）
        sb.append("┌─ 阻塞线程 (最多显示5个) ───────────────────────────────────────────────────┐\n");
        int blockedCount = 0;
        for (ThreadInfo info : threadInfos) {
            if (info != null && (info.getThreadState() == Thread.State.BLOCKED
                    || info.getThreadState() == Thread.State.WAITING)) {
                if (blockedCount++ >= 5) break;

                sb.append(String.format("│  线程ID: %-8d 状态: %-20s│\n",
                        info.getThreadId(), info.getThreadState()));
                sb.append(String.format("│  名称: %-68s│\n",
                        truncate(info.getThreadName(), 68)));

                // 显示等待的锁信息
                if (info.getLockName() != null) {
                    sb.append(String.format("│  等待锁: %-65s│\n",
                            truncate(info.getLockName(), 65)));
                }
                sb.append("│                                                                            │\n");
            }
        }
        if (blockedCount == 0) {
            sb.append("│  无阻塞线程                                                                │\n");
        }
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        return sb.toString();
    }

    /**
     * 生成网络连接状态章节
     *
     * <p>包含连接概览、连接详情两个部分。</p>
     *
     * @return 网络连接状态章节内容
     */
    private static String generateNetworkConnections() {
        StringBuilder sb = new StringBuilder();

        // 章节标题
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("                          【网络连接状态】                                      \n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // 统计连接信息
        int totalHostClients = NeoProxyServer.availableHostClient.size();
        int tcpConnectionCount = 0;
        int udpConnectionCount = 0;

        // 遍历所有客户端统计连接数
        for (HostClient client : NeoProxyServer.availableHostClient) {
            // 统计TCP连接数
            tcpConnectionCount += client.getActiveTcpSockets().size();

            // 统计UDP连接数
            if (client.getClientDatagramSocket() != null) {
                udpConnectionCount++;
            }
        }

        // 统计UDP活跃连接数
        int activeUdpTransformers = UDPTransformer.udpClientConnections.size();

        // 连接概览
        sb.append("┌─ 连接概览 ─────────────────────────────────────────────────────────────────┐\n");
        sb.append(String.format("│  活跃客户端数: %-58d│\n", totalHostClients));
        sb.append(String.format("│  TCP活跃连接数: %-57d│\n", tcpConnectionCount));
        sb.append(String.format("│  UDP监听端口数: %-57d│\n", udpConnectionCount));
        sb.append(String.format("│  UDP活跃会话数: %-57d│\n", activeUdpTransformers));
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        // 客户端连接详情（Top 10）
        sb.append("┌─ 客户端连接详情 (Top 10) ──────────────────────────────────────────────────┐\n");

        // 按TCP连接数排序客户端
        List<HostClient> sortedClients = new ArrayList<>(NeoProxyServer.availableHostClient);
        sortedClients.sort((a, b) -> {
            int tcpA = a.getActiveTcpSockets().size();
            int tcpB = b.getActiveTcpSockets().size();
            return Integer.compare(tcpB, tcpA);
        });

        // 显示Top 10客户端连接详情
        int count = 0;
        for (HostClient client : sortedClients) {
            if (count++ >= 10) break;

            String clientIP = client.getIP();
            int tcpCount = client.getActiveTcpSockets().size();
            boolean hasUDP = client.getClientDatagramSocket() != null;
            String keyName = client.getKey() != null ? client.getKey().getName() : "N/A";

            sb.append(String.format("│  %-15s TCP:%-4d UDP:%-3s Key:%-24s│\n",
                    truncate(clientIP, 15), tcpCount, hasUDP ? "Yes" : "No", truncate(keyName, 24)));
        }

        if (sortedClients.isEmpty()) {
            sb.append("│  无活跃客户端                                                              │\n");
        }
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        return sb.toString();
    }

    /**
     * 生成密钥状态章节
     *
     * <p>包含活跃密钥概览和密钥详情两个部分。</p>
     *
     * @return 密钥状态章节内容
     */
    private static String generateKeyStatus() {
        StringBuilder sb = new StringBuilder();

        // 章节标题
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("                            【密钥状态】                                        \n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // 统计密钥使用情况
        Map<String, KeyInfo> keyInfoMap = new HashMap<>();

        // 统计每个密钥对应的客户端数量和流量
        for (HostClient client : NeoProxyServer.availableHostClient) {
            SequenceKey key = client.getKey();
            if (key != null) {
                String keyName = key.getName();
                KeyInfo info = keyInfoMap.computeIfAbsent(keyName, k -> new KeyInfo());
                info.clientCount++;
                info.balance = key.getBalance();
            }
        }

        // 活跃密钥概览
        sb.append("┌─ 活跃密钥概览 ─────────────────────────────────────────────────────────────┐\n");
        sb.append(String.format("│  活跃密钥总数: %-58d│\n", keyInfoMap.size()));
        sb.append(String.format("│  有客户端连接的密钥数: %-50d│\n",
                keyInfoMap.values().stream().filter(i -> i.clientCount > 0).count()));
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        // 密钥详情（显示Top 10）
        sb.append("┌─ 密钥详情 (Top 10) ────────────────────────────────────────────────────────┐\n");

        keyInfoMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().balance, a.getValue().balance))
                .limit(10)
                .forEach(entry -> {
                    String keyName = entry.getKey();
                    KeyInfo info = entry.getValue();

                    sb.append(String.format("│  密钥: %-50s│\n", truncate(keyName, 50)));
                    sb.append(String.format("│    剩余流量: %-57.2f MiB│\n", info.balance));
                    sb.append(String.format("│    活跃客户端数: %-54d│\n", info.clientCount));
                    sb.append("│                                                                            │\n");
                });

        if (keyInfoMap.isEmpty()) {
            sb.append("│  无活跃密钥                                                                │\n");
        }
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        return sb.toString();
    }

    /**
     * 生成性能瓶颈分析章节
     *
     * <p>自动检测系统性能问题，包括：</p>
     * <ul>
     *   <li>CPU使用率过高</li>
     *   <li>内存使用率过高</li>
     *   <li>JVM堆内存不足</li>
     *   <li>GC频繁</li>
     *   <li>线程数量过多</li>
     *   <li>阻塞线程过多</li>
     *   <li>磁盘空间不足</li>
     * </ul>
     *
     * <p>并提供相应的优化建议。</p>
     *
     * @return 性能瓶颈分析章节内容
     */
    private static String generatePerformanceBottlenecks() {
        StringBuilder sb = new StringBuilder();

        // 章节标题
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("                        【性能瓶颈分析】                                        \n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // 问题列表
        List<String> warnings = new ArrayList<>();
        List<String> criticals = new ArrayList<>();

        // 检测CPU使用率
        double systemCpu = OS_BEAN.getCpuLoad() * 100;
        double processCpu = OS_BEAN.getProcessCpuLoad() * 100;

        if (systemCpu > 80) {
            criticals.add(String.format("系统CPU使用率过高 (%.1f%%)，可能影响整体性能", systemCpu));
        } else if (systemCpu > 60) {
            warnings.add(String.format("系统CPU使用率较高 (%.1f%%)，建议关注", systemCpu));
        }

        if (processCpu > 80) {
            criticals.add(String.format("进程CPU使用率过高 (%.1f%%)，可能存在性能瓶颈", processCpu));
        } else if (processCpu > 60) {
            warnings.add(String.format("进程CPU使用率较高 (%.1f%%)，建议检查业务逻辑", processCpu));
        }

        // 检测系统内存使用率
        GlobalMemory memory = HAL.getMemory();
        long totalMem = memory.getTotal();
        long availableMem = memory.getAvailable();
        double memUsagePercent = (double) (totalMem - availableMem) / totalMem * 100;

        if (memUsagePercent > 90) {
            criticals.add(String.format("系统内存使用率过高 (%.1f%%)，可能导致系统卡顿", memUsagePercent));
        } else if (memUsagePercent > 80) {
            warnings.add(String.format("系统内存使用率较高 (%.1f%%)，建议关注", memUsagePercent));
        }

        // 检测JVM堆内存使用率
        long jvmHeapUsed = RUNTIME.totalMemory() - RUNTIME.freeMemory();
        long jvmHeapMax = RUNTIME.maxMemory();
        double heapUsagePercent = (double) jvmHeapUsed / jvmHeapMax * 100;

        if (heapUsagePercent > 90) {
            criticals.add(String.format("JVM堆内存使用率过高 (%.1f%%)，可能导致频繁GC", heapUsagePercent));
        } else if (heapUsagePercent > 80) {
            warnings.add(String.format("JVM堆内存使用率较高 (%.1f%%)，建议调整堆大小", heapUsagePercent));
        }

        // 检测GC耗时
        long totalGcTime = 0;
        for (GarbageCollectorMXBean gcBean : GC_BEANS) {
            totalGcTime += gcBean.getCollectionTime();
        }
        if (totalGcTime > 10000) {
            warnings.add(String.format("GC累计耗时较长 (%dms)，可能影响响应速度", totalGcTime));
        }

        // 检测线程数量
        int threadCount = THREAD_BEAN.getThreadCount();
        if (threadCount > 500) {
            criticals.add(String.format("线程数量过多 (%d)，可能导致线程调度开销过大", threadCount));
        } else if (threadCount > 300) {
            warnings.add(String.format("线程数量较多 (%d)，建议检查线程池配置", threadCount));
        }

        // 检测阻塞线程数量
        int blockedCount = 0;
        long[] threadIds = THREAD_BEAN.getAllThreadIds();
        ThreadInfo[] threadInfos = THREAD_BEAN.getThreadInfo(threadIds, 0);
        for (ThreadInfo info : threadInfos) {
            if (info != null && (info.getThreadState() == Thread.State.BLOCKED
                    || info.getThreadState() == Thread.State.WAITING)) {
                blockedCount++;
            }
        }

        if (blockedCount > 50) {
            warnings.add(String.format("阻塞/等待线程较多 (%d)，可能存在锁竞争", blockedCount));
        }

        // 检测客户端数量
        int totalHostClients = NeoProxyServer.availableHostClient.size();
        if (totalHostClients > 100) {
            warnings.add(String.format("活跃客户端数量较多 (%d)，建议监控资源使用", totalHostClients));
        }

        // 检测磁盘空间
        File root = new File(NeoProxyServer.CURRENT_DIR_PATH);
        long freeSpace = root.getUsableSpace();
        long totalSpace = root.getTotalSpace();
        double diskUsagePercent = (double) (totalSpace - freeSpace) / totalSpace * 100;

        if (diskUsagePercent > 95) {
            criticals.add(String.format("磁盘使用率过高 (%.1f%%)，可能导致写入失败", diskUsagePercent));
        } else if (diskUsagePercent > 90) {
            warnings.add(String.format("磁盘使用率较高 (%.1f%%)，建议清理磁盘空间", diskUsagePercent));
        }

        // 严重问题部分
        sb.append("┌─ 严重问题 (CRITICAL) ──────────────────────────────────────────────────────┐\n");
        if (criticals.isEmpty()) {
            sb.append("│  ✓ 未发现严重问题                                                          │\n");
        } else {
            for (int i = 0; i < criticals.size(); i++) {
                sb.append(String.format("│  [%d] %-68s│\n", i + 1, truncate(criticals.get(i), 68)));
            }
        }
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        // 警告信息部分
        sb.append("┌─ 警告信息 (WARNING) ───────────────────────────────────────────────────────┐\n");
        if (warnings.isEmpty()) {
            sb.append("│  ✓ 未发现警告                                                              │\n");
        } else {
            for (int i = 0; i < warnings.size(); i++) {
                sb.append(String.format("│  [%d] %-68s│\n", i + 1, truncate(warnings.get(i), 68)));
            }
        }
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        // 优化建议部分
        sb.append("┌─ 优化建议 ─────────────────────────────────────────────────────────────────┐\n");
        if (!criticals.isEmpty() || !warnings.isEmpty()) {
            // 根据具体问题提供针对性建议
            if (heapUsagePercent > 80) {
                sb.append("│  • 增加JVM堆内存: 使用 -Xmx 参数调整最大堆大小                              │\n");
            }
            if (processCpu > 60) {
                sb.append("│  • 检查业务逻辑: 查看是否有死循环或高CPU计算任务                            │\n");
                sb.append("│  • 优化线程池: 合理配置线程池大小，避免过多线程                             │\n");
            }
            if (blockedCount > 30) {
                sb.append("│  • 检查锁竞争: 分析线程堆栈，优化同步代码块                                 │\n");
            }
            if (totalGcTime > 5000) {
                sb.append("│  • 优化GC参数: 考虑使用G1GC或调整GC策略                                     │\n");
            }
            if (diskUsagePercent > 85) {
                sb.append("│  • 清理磁盘: 删除不必要的日志文件和临时文件                                 │\n");
            }
        } else {
            sb.append("│  ✓ 系统运行状态良好，无需优化                                              │\n");
        }
        sb.append("└────────────────────────────────────────────────────────────────────────────┘\n\n");

        return sb.toString();
    }

    /**
     * 生成报告尾部
     *
     * @return 报告尾部内容
     */
    private static String generateFooter() {
        StringBuilder sb = new StringBuilder();

        // 报告结束标记
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("                            【报告结束】                                        \n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // 使用提示
        sb.append("提示: 此报告用于诊断性能问题，如需进一步分析，请将此报告发送给技术支持团队。\n");

        return sb.toString();
    }

    /**
     * 获取JVM进程的物理内存占用(RSS)
     *
     * <p>使用OSHI库获取进程的实际物理内存占用。
     * 如果获取失败，则返回JVM堆内存使用量作为近似值。</p>
     *
     * @return JVM进程的物理内存占用（字节）
     */
    private static long getJvmRss() {
        try {
            OSProcess process = OS.getProcess(OS.getProcessId());
            return process.getResidentSetSize();
        } catch (Exception e) {
            // 如果获取失败，返回JVM堆内存使用量作为近似值
            return RUNTIME.totalMemory() - RUNTIME.freeMemory();
        }
    }

    /**
     * 格式化字节数为人类可读格式
     *
     * <p>自动选择合适的单位（B、KB、MB、GB、TB、PB、EB），
     * 并保留两位小数。</p>
     *
     * @param bytes 字节数
     * @return 格式化后的字符串，例如 "1.23 GB"
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), unit);
    }

    /**
     * 截断字符串到指定长度
     *
     * <p>如果字符串超过指定长度，则截断并添加"..."后缀。</p>
     *
     * @param str       原始字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串
     */
    private static String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * 从线程名称提取线程组
     *
     * <p>根据线程名称的模式，提取线程所属的组别，
     * 用于统计不同类型线程的数量。</p>
     *
     * <p>支持的线程组类型：</p>
     * <ul>
     *   <li>Pool-X: 线程池线程</li>
     *   <li>Thread: 普通线程</li>
     *   <li>ForkJoin: ForkJoin线程</li>
     *   <li>NIO: NIO相关线程</li>
     *   <li>HTTP: HTTP相关线程</li>
     *   <li>WebSocket: WebSocket线程</li>
     *   <li>Timer: 定时器线程</li>
     *   <li>Scheduler: 调度器线程</li>
     *   <li>Database: 数据库线程</li>
     *   <li>Other: 其他线程</li>
     * </ul>
     *
     * @param threadName 线程名称
     * @return 线程组名称
     */
    private static String extractThreadGroup(String threadName) {
        if (threadName == null || threadName.isEmpty()) return "Unknown";

        // 提取线程池编号，例如 pool-1-thread-1 -> Pool-1
        if (threadName.contains("pool-")) {
            int start = threadName.indexOf("pool-");
            int end = threadName.indexOf("-", start + 5);
            if (end > start) {
                return "Pool-" + threadName.substring(start + 5, end);
            }
            return "Pool";
        }

        // 根据线程名称特征判断线程组
        if (threadName.contains("Thread-")) return "Thread";
        if (threadName.contains("ForkJoinPool")) return "ForkJoin";
        if (threadName.contains("nio")) return "NIO";
        if (threadName.contains("http")) return "HTTP";
        if (threadName.contains("WebSocket")) return "WebSocket";
        if (threadName.contains("Timer")) return "Timer";
        if (threadName.contains("scheduler")) return "Scheduler";
        if (threadName.contains("DB-")) return "Database";

        return "Other";
    }

    /**
     * 密钥信息内部类
     *
     * <p>用于临时存储密钥的统计信息。</p>
     */
    private static class KeyInfo {
        int clientCount = 0;
        double balance = 0;
    }

}
