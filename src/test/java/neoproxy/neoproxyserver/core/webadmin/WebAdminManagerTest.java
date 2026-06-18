package neoproxy.neoproxyserver.core.webadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

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
    @DisplayName("测试临时token - 未打开时仅在5分钟内有效")
    void testTempToken_ExpiresIfNotOpened() throws Exception {
        setStaticField("isRunning", true);
        setStaticField("currentTempToken", "temp-token-1");
        setStaticField("tempTokenCreatedAt", System.currentTimeMillis() - (5 * 60 * 1000) - 1);
        setStaticField("tempSessionOpen", false);

        assertEquals(0, WebAdminManager.verifyTokenAndGetType("temp-token-1"));

        clearTempState();
    }

    @Test
    @DisplayName("测试临时token - 打开后保持有效，关闭前不受5分钟限制")
    void testTempToken_RemainsValidWhileOpen() throws Exception {
        setStaticField("isRunning", true);
        setStaticField("currentTempToken", "temp-token-2");
        setStaticField("tempTokenCreatedAt", System.currentTimeMillis() - (2 * 60 * 1000));

        WebAdminManager.markTempSessionOpened("temp-token-2");
        setStaticField("tempTokenCreatedAt", System.currentTimeMillis() - (10 * 60 * 1000));

        assertEquals(1, WebAdminManager.verifyTokenAndGetType("temp-token-2"));

        WebAdminManager.markTempSessionClosed("temp-token-2");
        assertEquals(0, WebAdminManager.verifyTokenAndGetType("temp-token-2"));

        clearTempState();
    }

    @Test
    @DisplayName("测试临时token - 5分钟内关闭后仍按原始时限到期")
    void testTempToken_StillExpiresAtOriginalWindowAfterClose() throws Exception {
        setStaticField("isRunning", true);
        setStaticField("currentTempToken", "temp-token-3");
        setStaticField("tempTokenCreatedAt", System.currentTimeMillis() - (2 * 60 * 1000));
        setStaticField("tempSessionOpen", true);

        assertEquals(1, WebAdminManager.verifyTokenAndGetType("temp-token-3"));

        setStaticField("tempSessionOpen", false);
        assertEquals(1, WebAdminManager.verifyTokenAndGetType("temp-token-3"));

        setStaticField("tempTokenCreatedAt", System.currentTimeMillis() - (5 * 60 * 1000) - 1);
        assertEquals(0, WebAdminManager.verifyTokenAndGetType("temp-token-3"));

        clearTempState();
    }

    @Test
    @DisplayName("测试临时token - 超过5分钟后才建立WebSocket不能复活token")
    void testTempToken_CannotBeOpenedAfterOriginalWindow() throws Exception {
        setStaticField("isRunning", true);
        setStaticField("currentTempToken", "temp-token-4");
        setStaticField("tempTokenCreatedAt", System.currentTimeMillis() - (5 * 60 * 1000) - 1);
        setStaticField("tempSessionOpen", false);

        WebAdminManager.markTempSessionOpened("temp-token-4");

        assertEquals(0, WebAdminManager.verifyTokenAndGetType("temp-token-4"));

        clearTempState();
    }

    @Test
    @DisplayName("测试临时token - 关闭错误token不会影响当前打开会话")
    void testTempToken_WrongTokenCannotCloseCurrentSession() throws Exception {
        setStaticField("isRunning", true);
        setStaticField("currentTempToken", "temp-token-5");
        setStaticField("tempTokenCreatedAt", System.currentTimeMillis() - (2 * 60 * 1000));

        WebAdminManager.markTempSessionOpened("temp-token-5");
        setStaticField("tempTokenCreatedAt", System.currentTimeMillis() - (10 * 60 * 1000));
        WebAdminManager.markTempSessionClosed("wrong-token");

        assertEquals(1, WebAdminManager.verifyTokenAndGetType("temp-token-5"));

        clearTempState();
    }

    @Test
    @DisplayName("测试永久token - 优先于同名过期临时token")
    void testPermanentToken_HasPriorityOverExpiredTempToken() throws Exception {
        setStaticField("isRunning", true);
        setStaticField("currentTempToken", "shared-token");
        setStaticField("tempTokenCreatedAt", System.currentTimeMillis() - (10 * 60 * 1000));
        setStaticField("tempSessionOpen", false);
        WebAdminManager.setPermanentToken("shared-token");

        assertEquals(2, WebAdminManager.verifyTokenAndGetType("shared-token"));

        WebAdminManager.setPermanentToken("");
        clearTempState();
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

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = WebAdminManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void clearTempState() throws Exception {
        setStaticField("currentTempToken", null);
        setStaticField("tempTokenCreatedAt", 0L);
        setStaticField("tempSessionOpen", false);
        WebAdminManager.setPermanentToken("");
        setStaticField("isRunning", false);
    }
}
