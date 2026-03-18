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
    @DisplayName("测试setPermanentToken方法")
    void testSetPermanentToken() {
        assertDoesNotThrow(() -> WebAdminManager.setPermanentToken("test-token"));
    }

    @Test
    @DisplayName("测试generateNewSessionUrl方法 - 未初始化时")
    void testGenerateNewSessionUrl_NotRunning() {
        String url = WebAdminManager.generateNewSessionUrl();
        assertNotNull(url);
        assertTrue(url.contains("token="));
    }
}
