package neoproxy.neoproxyserver.core.management.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalKeyProvider 测试")
@ExtendWith(MockitoExtension.class)
class LocalKeyProviderTest {

    private LocalKeyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LocalKeyProvider();
    }

    @Test
    @DisplayName("测试consumeFlow方法 - 正流量")
    void testConsumeFlow_Positive() {
        assertDoesNotThrow(() -> provider.consumeFlow("test-key", 10.0));
    }

    @Test
    @DisplayName("测试consumeFlow方法 - 零流量")
    void testConsumeFlow_Zero() {
        assertDoesNotThrow(() -> provider.consumeFlow("test-key", 0));
    }

    @Test
    @DisplayName("测试consumeFlow方法 - 负流量")
    void testConsumeFlow_Negative() {
        assertDoesNotThrow(() -> provider.consumeFlow("test-key", -10.0));
    }

    @Test
    @DisplayName("测试releaseKey方法")
    void testReleaseKey() {
        assertDoesNotThrow(() -> provider.releaseKey("test-key"));
    }

    @Test
    @DisplayName("测试sendHeartbeat方法 - 本地模式始终返回true")
    void testSendHeartbeat() {
        Protocol.HeartbeatPayload payload = new Protocol.HeartbeatPayload();
        payload.serial = "test-key";
        
        assertTrue(provider.sendHeartbeat(payload));
    }

    @Test
    @DisplayName("测试shutdown方法")
    void testShutdown() {
        assertDoesNotThrow(() -> provider.shutdown());
    }
}
