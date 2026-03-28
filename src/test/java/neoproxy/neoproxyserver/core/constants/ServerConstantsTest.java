package neoproxy.neoproxyserver.core.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ServerConstants 常量类测试")
class ServerConstantsTest {

    @Test
    @DisplayName("测试私有构造器抛出AssertionError")
    void testPrivateConstructorThrowsException() throws Exception {
        Constructor<ServerConstants> constructor = ServerConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        
        InvocationTargetException exception = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertTrue(exception.getCause() instanceof AssertionError);
        assertTrue(exception.getCause().getMessage().contains("常量类禁止实例化"));
    }

    @Test
    @DisplayName("测试版本常量格式正确")
    void testVersionConstants() {
        // 验证版本号格式符合语义化版本规范 (X.Y.Z)
        assertNotNull(ServerConstants.VERSION);
        assertTrue(ServerConstants.VERSION.matches("\\d+\\.\\d+\\.\\d+"),
            "版本号应符合格式 X.Y.Z, 实际值: " + ServerConstants.VERSION);

        // 验证协议版本号
        assertEquals("1.0", ServerConstants.PROTOCOL_VERSION);
    }

    @Test
    @DisplayName("测试端口常量")
    void testPortConstants() {
        assertEquals(8080, ServerConstants.DEFAULT_SERVER_PORT);
        assertEquals(8081, ServerConstants.DEFAULT_WEB_ADMIN_PORT);
    }

    @Test
    @DisplayName("测试超时和延迟常量")
    void testTimeoutConstants() {
        assertEquals(30000, ServerConstants.DEFAULT_HEARTBEAT_TIMEOUT);
        assertEquals(3000, ServerConstants.DEFAULT_SAVE_DELAY);
        assertEquals(1000, ServerConstants.DEFAULT_DETECTION_DELAY);
    }

    @Test
    @DisplayName("测试安全相关常量")
    void testSecurityConstants() {
        assertEquals(128, ServerConstants.AES_KEY_SIZE);
        assertEquals(8192, ServerConstants.BUFFER_SIZE);
        assertEquals(1000, ServerConstants.MAX_CONCURRENT_CONNECTIONS);
    }

    @Test
    @DisplayName("测试动态端口范围常量")
    void testDynamicPortConstants() {
        assertEquals(10000, ServerConstants.DYNAMIC_PORT_START);
        assertEquals(65535, ServerConstants.DYNAMIC_PORT_END);
    }

    @Test
    @DisplayName("测试默认语言常量")
    void testDefaultLanguageConstant() {
        assertEquals("zh-CN", ServerConstants.DEFAULT_LANGUAGE);
    }
}
