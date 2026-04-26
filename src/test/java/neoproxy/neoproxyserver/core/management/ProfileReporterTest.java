package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.NeoProxyServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProfileReporter 测试")
class ProfileReporterTest {

    @Test
    @DisplayName("测试报告生成并保存到正确位置")
    void testGenerateAndSaveReportToCorrectLocation() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();

        assertNotNull(reportPath, "报告路径不应为空");
        assertTrue(reportPath.contains(NeoProxyServer.CURRENT_DIR_PATH), "报告应在当前工作目录中");

        File reportFile = new File(reportPath);
        assertTrue(reportFile.exists(), "报告文件应存在");
        assertTrue(reportFile.length() > 0, "报告文件不应为空");

        reportFile.delete();
    }

    @Test
    @DisplayName("测试报告文件名格式正确")
    void testReportFileNameFormat() {
        String reportPath = ProfileReporter.generateAndSaveReport();
        assertNotNull(reportPath, "报告路径不应为空");

        File reportFile = new File(reportPath);
        String fileName = reportFile.getName();

        assertTrue(fileName.startsWith("profile_"), "文件名应以'profile_'开头，实际文件名: " + fileName);
        assertTrue(fileName.endsWith(".txt"), "文件名应以'.txt'结尾，实际文件名: " + fileName);

        String timestamp = fileName.substring(8, fileName.length() - 4);
        assertTrue(timestamp.matches("\\d{8}_\\d{6}_\\d{3}"),
                "时间戳格式应为yyyyMMdd_HHmmss_SSS，实际时间戳: " + timestamp);

        reportFile.delete();
    }

    @Test
    @DisplayName("测试报告包含完整的章节")
    void testReportContainsAllSections() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("【系统概览】"), "应包含系统概览章节");
        assertTrue(content.contains("【JVM 信息】"), "应包含JVM信息章节");
        assertTrue(content.contains("【线程分析】"), "应包含线程分析章节");
        assertTrue(content.contains("【网络连接状态】"), "应包含网络连接状态章节");
        assertTrue(content.contains("【密钥状态】"), "应包含密钥状态章节");
        assertTrue(content.contains("【性能瓶颈分析】"), "应包含性能瓶颈分析章节");
        assertTrue(content.contains("【报告结束】"), "应包含报告结束标记");

        new File(reportPath).delete();
    }

    @Test
    @DisplayName("测试报告包含CPU信息")
    void testReportContainsCpuInfo() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("CPU 型号"), "应包含CPU型号");
        assertTrue(content.contains("逻辑核心数"), "应包含逻辑核心数");
        assertTrue(content.contains("系统CPU使用率"), "应包含系统CPU使用率");
        assertTrue(content.contains("进程CPU使用率"), "应包含进程CPU使用率");

        new File(reportPath).delete();
    }

    @Test
    @DisplayName("测试报告包含内存信息")
    void testReportContainsMemoryInfo() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("系统总内存"), "应包含系统总内存");
        assertTrue(content.contains("系统已用内存"), "应包含系统已用内存");
        assertTrue(content.contains("系统可用内存"), "应包含系统可用内存");
        assertTrue(content.contains("JVM堆内存(已用)"), "应包含JVM堆内存已用");
        assertTrue(content.contains("JVM堆内存(已提交)"), "应包含JVM堆内存已提交");
        assertTrue(content.contains("JVM堆内存(最大)"), "应包含JVM堆内存最大");
        assertTrue(content.contains("JVM物理内存(RSS)"), "应包含JVM物理内存RSS");

        new File(reportPath).delete();
    }

    @Test
    @DisplayName("测试报告包含磁盘信息")
    void testReportContainsDiskInfo() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("磁盘总容量"), "应包含磁盘总容量");
        assertTrue(content.contains("磁盘已用"), "应包含磁盘已用");
        assertTrue(content.contains("磁盘可用"), "应包含磁盘可用");

        new File(reportPath).delete();
    }

    @Test
    @DisplayName("测试报告包含GC统计")
    void testReportContainsGcStats() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("GC 统计"), "应包含GC统计章节");
        assertTrue(content.contains("收集次数"), "应包含收集次数");
        assertTrue(content.contains("总耗时"), "应包含总耗时");

        new File(reportPath).delete();
    }

    @Test
    @DisplayName("测试报告包含线程状态分布")
    void testReportContainsThreadStateDistribution() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("线程状态分布"), "应包含线程状态分布");
        assertTrue(content.contains("RUNNABLE") || content.contains("BLOCKED") || content.contains("WAITING"),
                "应包含至少一种线程状态");

        new File(reportPath).delete();
    }

    @Test
    @DisplayName("测试报告包含线程组分布")
    void testReportContainsThreadGroupDistribution() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("线程组分布"), "应包含线程组分布");

        new File(reportPath).delete();
    }

    @Test
    @DisplayName("测试报告包含连接概览")
    void testReportContainsConnectionOverview() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("活跃客户端数"), "应包含活跃客户端数");
        assertTrue(content.contains("TCP活跃连接数"), "应包含TCP活跃连接数");
        assertTrue(content.contains("UDP监听端口数"), "应包含UDP监听端口数");
        assertTrue(content.contains("UDP活跃会话数"), "应包含UDP活跃会话数");
        assertTrue(content.contains("客户端连接详情"), "应包含客户端连接详情");

        new File(reportPath).delete();
    }

    @Test
    @DisplayName("测试报告包含性能瓶颈分析")
    void testReportContainsPerformanceBottleneckAnalysis() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("严重问题 (CRITICAL)"), "应包含严重问题章节");
        assertTrue(content.contains("警告信息 (WARNING)"), "应包含警告信息章节");
        assertTrue(content.contains("优化建议"), "应包含优化建议章节");

        new File(reportPath).delete();
    }

    @Test
    @DisplayName("测试报告使用UTF-8编码")
    void testReportUsesUtf8Encoding() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        byte[] bytes = Files.readAllBytes(new File(reportPath).toPath());
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(content.contains("性能诊断报告"), "UTF-8编码应正确显示中文");
        assertTrue(content.contains("系统概览"), "UTF-8编码应正确显示中文");

        new File(reportPath).delete();
    }

    @Test
    @DisplayName("测试报告包含服务器版本信息")
    void testReportContainsServerVersion() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("服务器版本:"), "应包含服务器版本");

        new File(reportPath).delete();
    }

    @Test
    @DisplayName("测试报告包含工作目录信息")
    void testReportContainsWorkingDirectory() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("工作目录:"), "应包含工作目录");

        new File(reportPath).delete();
    }

    @Test
    @DisplayName("测试多次生成报告不会覆盖")
    void testMultipleReportsDoNotOverwrite() {
        String reportPath1 = ProfileReporter.generateAndSaveReport();
        String reportPath2 = ProfileReporter.generateAndSaveReport();

        assertNotEquals(reportPath1, reportPath2, "每次生成的报告应有不同的文件名");

        File file1 = new File(reportPath1);
        File file2 = new File(reportPath2);
        assertTrue(file1.exists(), "第一个报告文件应存在");
        assertTrue(file2.exists(), "第二个报告文件应存在");

        file1.delete();
        file2.delete();
    }

    @Test
    @DisplayName("测试报告格式美观 - 包含表格边框")
    void testReportContainsTableBorders() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("┌"), "应包含表格左上角边框");
        assertTrue(content.contains("┐"), "应包含表格右上角边框");
        assertTrue(content.contains("└"), "应包含表格左下角边框");
        assertTrue(content.contains("┘"), "应包含表格右下角边框");
        assertTrue(content.contains("│"), "应包含表格竖线边框");
        assertTrue(content.contains("─"), "应包含表格横线边框");

        new File(reportPath).delete();
    }

    @Test
    @DisplayName("测试报告包含字节单位转换")
    void testReportContainsByteUnitConversion() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("B") || content.contains("KB") || content.contains("MB") || content.contains("GB"),
                "应包含字节单位(B/KB/MB/GB)");

        new File(reportPath).delete();
    }
}
