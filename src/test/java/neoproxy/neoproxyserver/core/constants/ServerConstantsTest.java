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
    @DisplayName("测试协议版本号")
    void testProtocolVersion() {
        assertEquals("1.0", ServerConstants.PROTOCOL_VERSION);
    }

    @Test
    @DisplayName("测试端口常量 — 与 config.cfg 默认值严格对齐")
    void testPortConstants() {
        assertEquals(44801, ServerConstants.DEFAULT_HOST_HOOK_PORT);
        assertEquals(44802, ServerConstants.DEFAULT_HOST_CONNECT_PORT);
        assertEquals(44803, ServerConstants.DEFAULT_WEB_ADMIN_PORT);
    }

    @Test
    @DisplayName("测试超时和延迟常量 — 与 config.cfg 默认值严格对齐")
    void testTimeoutConstants() {
        assertEquals(5000, ServerConstants.DEFAULT_HEARTBEAT_TIMEOUT);
        assertEquals(3000, ServerConstants.DEFAULT_SAVE_DELAY);
        assertEquals(1000, ServerConstants.DEFAULT_DETECTION_DELAY);
    }

    @Test
    @DisplayName("测试安全与缓冲区常量")
    void testSecurityAndBufferConstants() {
        assertEquals(128, ServerConstants.AES_KEY_SIZE);
        assertEquals(65535, ServerConstants.TCP_BUFFER_SIZE);
    }

    @Test
    @DisplayName("测试动态端口标识值")
    void testDynamicPortConstant() {
        assertEquals(-1, ServerConstants.DYNAMIC_PORT);
    }

    @Test
    @DisplayName("测试默认本地域名常量")
    void testDefaultLocalDomainName() {
        assertEquals("localhost", ServerConstants.DEFAULT_LOCAL_DOMAIN_NAME);
    }

    @Test
    @DisplayName("测试默认语言常量")
    void testDefaultLanguageConstant() {
        assertEquals("zh-CN", ServerConstants.DEFAULT_LANGUAGE);
    }
}
