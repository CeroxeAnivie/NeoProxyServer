package neoproxy.neoproxyserver.core.webadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("WebAdminServer 测试")
class WebAdminServerTest {

    @Test
    @DisplayName("测试构造器")
    void testConstructor() {
        WebAdminServer server = new WebAdminServer(8080);

        assertNotNull(server);
    }

    @Test
    @DisplayName("测试broadcastLog方法")
    void testBroadcastLog() {
        assertDoesNotThrow(() -> WebAdminServer.broadcastLog("Test log message"));
    }

    @Test
    @DisplayName("测试broadcastLog方法 - 包含特殊字符")
    void testBroadcastLog_SpecialChars() {
        assertDoesNotThrow(() -> WebAdminServer.broadcastLog("Test ____ message"));
    }
}
