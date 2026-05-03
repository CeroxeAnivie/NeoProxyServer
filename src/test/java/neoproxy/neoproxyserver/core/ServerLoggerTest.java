package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ServerLogger 测试")
class ServerLoggerTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        ServerLogger.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        ServerLogger.setLocale(Locale.ENGLISH);
    }

    @Test
    @DisplayName("测试info方法 - 正常消息")
    void testInfo_NormalMessage() {
        assertDoesNotThrow(() -> ServerLogger.info("neoProxyServer.currentLogFile", "test.log"));
    }

    @Test
    @DisplayName("测试info方法 - 无参数")
    void testInfo_NoArgs() {
        assertDoesNotThrow(() -> ServerLogger.info("neoProxyServer.currentLogFile"));
    }

    @Test
    @DisplayName("测试info方法 - 多个参数")
    void testInfo_MultipleArgs() {
        assertDoesNotThrow(() -> ServerLogger.info("consoleManager.currentServerVersion", "6.1.0", "1.0"));
    }

    @Test
    @DisplayName("测试warn方法 - 正常消息")
    void testWarn_NormalMessage() {
        assertDoesNotThrow(() -> ServerLogger.warn("neoProxyServer.clientConnectButFail", "error message"));
    }

    @Test
    @DisplayName("测试warn方法 - 无参数")
    void testWarn_NoArgs() {
        assertDoesNotThrow(() -> ServerLogger.warn("neoProxyServer.clientConnectButFail"));
    }

    @Test
    @DisplayName("测试error方法 - 正常消息")
    void testError_NormalMessage() {
        assertDoesNotThrow(() -> ServerLogger.error("neoProxyServer.clientConnectButFail", "error message"));
    }

    @Test
    @DisplayName("测试error方法 - 无参数")
    void testError_NoArgs() {
        assertDoesNotThrow(() -> ServerLogger.error("neoProxyServer.clientConnectButFail"));
    }

    @Test
    @DisplayName("测试error方法 - 带异常")
    void testError_WithException() {
        Exception testException = new RuntimeException("Test exception");

        assertDoesNotThrow(() -> ServerLogger.error("neoProxyServer.clientConnectButFail", testException));
    }

    @Test
    @DisplayName("测试getMessage方法 - 正常键")
    void testGetMessage_ValidKey() {
        String message = ServerLogger.getMessage("neoProxyServer.currentLogFile", "test.log");

        assertNotNull(message);
        assertTrue(message.length() > 0);
    }

    @Test
    @DisplayName("测试getMessage方法 - 无效键")
    void testGetMessage_InvalidKey() {
        String message = ServerLogger.getMessage("nonexistent.key");

        assertNotNull(message);
        assertTrue(message.contains("not found"));
    }

    @Test
    @DisplayName("测试logRaw方法")
    void testLogRaw() {
        assertDoesNotThrow(() -> ServerLogger.logRaw("TestSource", "Test message"));
    }

    @Test
    @DisplayName("测试setLocale方法")
    void testSetLocale() {
        assertDoesNotThrow(() -> ServerLogger.setLocale(java.util.Locale.CHINA));
        assertDoesNotThrow(() -> ServerLogger.setLocale(java.util.Locale.US));
    }

    @Test
    @DisplayName("测试所有生产日志键都存在于中英资源文件")
    void allProductionServerLoggerKeysExistInBothBundles() throws IOException {
        Set<String> codeKeys = new TreeSet<>();
        Pattern withSourcePattern = Pattern.compile(
                "ServerLogger\\.(?:infoWithSource|warnWithSource|errorWithSource)\\(\"[^\"]+\"\\s*,\\s*\"([^\"]+)\"");
        Pattern plainPattern = Pattern.compile(
                "ServerLogger\\.(?:info|warn|error)\\((?:\"[^\"]+\"\\s*,\\s*)?\"([^\"]+)\"");
        Pattern getMessagePattern = Pattern.compile(
                "ServerLogger\\.getMessage\\(\"([^\"]+)\"");

        try (var stream = Files.walk(Path.of("src/main/java"))) {
            stream.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = Files.readString(path);
                    collectMatches(codeKeys, withSourcePattern.matcher(content));
                    collectMatches(codeKeys, plainPattern.matcher(content));
                    collectMatches(codeKeys, getMessagePattern.matcher(content));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        Properties en = new Properties();
        Properties zh = new Properties();
        try (var enStream = Files.newInputStream(Path.of("src/main/resources/messages_en.properties"));
             var zhStream = Files.newInputStream(Path.of("src/main/resources/messages_zh.properties"))) {
            en.load(enStream);
            zh.load(zhStream);
        }

        Set<String> missingInEn = new TreeSet<>();
        Set<String> missingInZh = new TreeSet<>();
        for (String key : codeKeys) {
            if (!en.containsKey(key)) {
                missingInEn.add(key);
            }
            if (!zh.containsKey(key)) {
                missingInZh.add(key);
            }
        }

        assertTrue(missingInEn.isEmpty(), "Missing keys in messages_en.properties: " + missingInEn);
        assertTrue(missingInZh.isEmpty(), "Missing keys in messages_zh.properties: " + missingInZh);
    }

    private static void collectMatches(Set<String> target, Matcher matcher) {
        while (matcher.find()) {
            target.add(matcher.group(1));
        }
    }
}
