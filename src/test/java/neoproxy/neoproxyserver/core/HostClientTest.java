package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HostClient 静态测试")
class HostClientTest {

    @Test
    @DisplayName("测试静态常量 SAVE_DELAY")
    void testSaveDelayConstant() {
        assertTrue(HostClient.SAVE_DELAY > 0);
    }

    @Test
    @DisplayName("测试静态常量 DETECTION_DELAY")
    void testDetectionDelayConstant() {
        assertTrue(HostClient.DETECTION_DELAY > 0);
    }

    @Test
    @DisplayName("测试静态常量 AES_KEY_SIZE")
    void testAesKeySizeConstant() {
        assertTrue(HostClient.AES_KEY_SIZE > 0);
    }

    @Test
    @DisplayName("测试静态常量 HEARTBEAT_TIMEOUT")
    void testHeartbeatTimeoutConstant() {
        assertTrue(HostClient.HEARTBEAT_TIMEOUT > 0);
    }

    @Test
    @DisplayName("测试HostClient实现Closeable接口")
    void testImplementsCloseable() {
        assertTrue(java.io.Closeable.class.isAssignableFrom(HostClient.class));
    }

    @Test
    @DisplayName("测试EXPECTED_HEARTBEAT常量")
    void testExpectedHeartbeatConstant() throws Exception {
        java.lang.reflect.Field field = HostClient.class.getDeclaredField("EXPECTED_HEARTBEAT");
        field.setAccessible(true);
        String value = (String) field.get(null);
        
        assertEquals("PING", value);
    }
}
