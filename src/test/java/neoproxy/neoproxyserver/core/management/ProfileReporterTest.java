package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.NeoProxyServer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class ProfileReporterTest {

    @Test
    void testGenerateAndSaveReport() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();

        assertNotNull(reportPath, "Report path should not be null");

        File reportFile = new File(reportPath);
        assertTrue(reportFile.exists(), "Report file should exist");
        assertTrue(reportFile.getName().startsWith("profile_"), "Report filename should start with 'profile_'");
        assertTrue(reportFile.getName().endsWith(".txt"), "Report filename should end with '.txt'");

        String content = Files.readString(reportFile.toPath());
        assertTrue(content.contains("NeoProxyServer 性能诊断报告"), "Report should contain title");
        assertTrue(content.contains("系统概览"), "Report should contain system overview");
        assertTrue(content.contains("JVM 信息"), "Report should contain JVM info");
        assertTrue(content.contains("线程分析"), "Report should contain thread analysis");
        assertTrue(content.contains("网络连接状态"), "Report should contain network connections");
        assertTrue(content.contains("密钥状态"), "Report should contain key status");
        assertTrue(content.contains("性能瓶颈分析"), "Report should contain performance bottlenecks");

        if (reportFile.exists()) {
            reportFile.delete();
        }
    }

    @Test
    void testReportContainsSystemInfo() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        assertNotNull(reportPath);

        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("CPU 信息"), "Report should contain CPU info section");
        assertTrue(content.contains("内存信息"), "Report should contain memory info section");
        assertTrue(content.contains("磁盘信息"), "Report should contain disk info section");

        new File(reportPath).delete();
    }

    @Test
    void testReportContainsJvmInfo() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        assertNotNull(reportPath);

        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("内存池"), "Report should contain memory pool section");
        assertTrue(content.contains("GC 统计"), "Report should contain GC statistics section");
        assertTrue(content.contains("线程概览"), "Report should contain thread overview section");

        new File(reportPath).delete();
    }

    @Test
    void testReportContainsThreadAnalysis() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        assertNotNull(reportPath);

        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("线程状态分布"), "Report should contain thread state distribution");
        assertTrue(content.contains("线程组分布"), "Report should contain thread group distribution");
        assertTrue(content.contains("CPU占用最高的线程"), "Report should contain CPU intensive threads");
        assertTrue(content.contains("阻塞线程"), "Report should contain blocked threads");

        new File(reportPath).delete();
    }

    @Test
    void testReportContainsPerformanceAnalysis() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        assertNotNull(reportPath);

        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("严重问题"), "Report should contain critical issues section");
        assertTrue(content.contains("警告信息"), "Report should contain warnings section");
        assertTrue(content.contains("优化建议"), "Report should contain optimization suggestions");

        new File(reportPath).delete();
    }

    @Test
    void testReportFileNaming() {
        String reportPath1 = ProfileReporter.generateAndSaveReport();
        String reportPath2 = ProfileReporter.generateAndSaveReport();

        assertNotEquals(reportPath1, reportPath2, "Each report should have a unique filename");

        File file1 = new File(reportPath1);
        File file2 = new File(reportPath2);
        assertTrue(file1.exists());
        assertTrue(file2.exists());

        file1.delete();
        file2.delete();
    }

    @Test
    void testReportContainsTimestamp() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        assertNotNull(reportPath);

        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("生成时间:"), "Report should contain generation timestamp");
        assertTrue(content.contains("服务器版本:"), "Report should contain server version");
        assertTrue(content.contains("工作目录:"), "Report should contain working directory");

        new File(reportPath).delete();
    }

    @Test
    void testReportContainsNetworkConnections() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        assertNotNull(reportPath);

        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("连接概览"), "Report should contain connection overview");
        assertTrue(content.contains("活跃客户端数"), "Report should contain active client count");
        assertTrue(content.contains("TCP活跃连接数"), "Report should contain TCP active connection count");
        assertTrue(content.contains("UDP监听端口数"), "Report should contain UDP listener count");
        assertTrue(content.contains("UDP活跃会话数"), "Report should contain UDP active session count");

        new File(reportPath).delete();
    }

    @Test
    void testReportContainsKeyStatus() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        assertNotNull(reportPath);

        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("活跃密钥概览"), "Report should contain active key overview");
        assertTrue(content.contains("密钥详情"), "Report should contain key details");

        new File(reportPath).delete();
    }

    @Test
    void testReportEndsWithFooter() throws IOException {
        String reportPath = ProfileReporter.generateAndSaveReport();
        assertNotNull(reportPath);

        String content = Files.readString(new File(reportPath).toPath());

        assertTrue(content.contains("报告结束"), "Report should contain footer");
        assertTrue(content.contains("此报告用于诊断性能问题"), "Report should contain usage hint");

        new File(reportPath).delete();
    }
}
