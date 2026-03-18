package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ServerLogger 测试")
class ServerLoggerTest {

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
}
