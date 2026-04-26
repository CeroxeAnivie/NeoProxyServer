package neoproxy.neoproxyserver.core.webadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebAdminManager 测试")
class WebAdminManagerTest {

    @Test
    @DisplayName("测试私有构造器")
    void testPrivateConstructor() throws Exception {
        Constructor<WebAdminManager> constructor = WebAdminManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        WebAdminManager instance = constructor.newInstance();

        assertNotNull(instance);
    }

    @Test
    @DisplayName("测试WEB_ADMIN_PORT初始值")
    void testWebAdminPortInitialValue() {
        assertEquals(44803, WebAdminManager.WEB_ADMIN_PORT);
    }

    @Test
    @DisplayName("测试isRunning方法 - 未初始化时")
    void testIsRunning_BeforeInit() {
        assertFalse(WebAdminManager.isRunning());
    }

    @Test
    @DisplayName("测试verifyToken方法 - null token")
    void testVerifyToken_NullToken() {
        assertFalse(WebAdminManager.verifyToken(null));
    }

    @Test
    @DisplayName("测试verifyToken方法 - 空字符串")
    void testVerifyToken_EmptyToken() {
        assertFalse(WebAdminManager.verifyToken(""));
    }

    @Test
    @DisplayName("测试verifyToken方法 - 无效token")
    void testVerifyToken_InvalidToken() {
        assertFalse(WebAdminManager.verifyToken("invalid-token"));
    }

    @Test
    @DisplayName("测试verifyTokenAndGetType方法 - null token")
    void testVerifyTokenAndGetType_NullToken() {
        assertEquals(0, WebAdminManager.verifyTokenAndGetType(null));
    }

    @Test
    @DisplayName("测试verifyTokenAndGetType方法 - 空字符串")
    void testVerifyTokenAndGetType_EmptyToken() {
        assertEquals(0, WebAdminManager.verifyTokenAndGetType(""));
    }

    @Test
    @DisplayName("测试verifyTokenAndGetType方法 - 无效token")
    void testVerifyTokenAndGetType_InvalidToken() {
        assertEquals(0, WebAdminManager.verifyTokenAndGetType("invalid-token"));
    }

    @Test
    @DisplayName("测试setPermanentToken方法")
    void testSetPermanentToken() throws InterruptedException {
        // 先初始化WebAdminManager
        WebAdminManager.init();

        // 等待服务器启动
        int retries = 50;
        while (!WebAdminManager.isRunning() && retries-- > 0) {
            Thread.sleep(100);
        }

        WebAdminManager.setPermanentToken("test-token");
        assertTrue(WebAdminManager.verifyToken("test-token"));
        assertEquals(2, WebAdminManager.verifyTokenAndGetType("test-token"));

        // 清理
        WebAdminManager.setPermanentToken("");
        WebAdminManager.shutdown();
    }

    @Test
    @DisplayName("测试setSslConfig方法")
    void testSetSslConfig() {
        WebAdminManager.setSslConfig("/path/to/cert.pem", "/path/to/key.pem", "password");
        assertTrue(WebAdminManager.isSslEnabled());

        // 清理
        WebAdminManager.setSslConfig("", "", "");
    }

    @Test
    @DisplayName("测试isSslEnabled方法 - 未配置SSL")
    void testIsSslEnabled_NotConfigured() {
        assertFalse(WebAdminManager.isSslEnabled());
    }

    @Test
    @DisplayName("测试isSslEnabled方法 - 配置不完整")
    void testIsSslEnabled_PartialConfig() {
        WebAdminManager.setSslConfig("/path/to/cert.pem", "", "");
        assertFalse(WebAdminManager.isSslEnabled());

        WebAdminManager.setSslConfig("", "/path/to/key.pem", "");
        assertFalse(WebAdminManager.isSslEnabled());

        // 清理
        WebAdminManager.setSslConfig("", "", "");
    }
}
