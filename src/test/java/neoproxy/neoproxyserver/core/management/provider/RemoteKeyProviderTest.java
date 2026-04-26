package neoproxy.neoproxyserver.core.management.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RemoteKeyProvider 测试")
class RemoteKeyProviderTest {

    @Test
    @DisplayName("测试构造器")
    void testConstructor() {
        RemoteKeyProvider provider = new RemoteKeyProvider("http://localhost:8080", "test-token", "test-node");

        assertNotNull(provider);
    }

    @Test
    @DisplayName("测试构造器 - 空token")
    void testConstructor_EmptyToken() {
        RemoteKeyProvider provider = new RemoteKeyProvider("http://localhost:8080", "", "test-node");

        assertNotNull(provider);
    }

    @Test
    @DisplayName("测试构造器 - null token")
    void testConstructor_NullToken() {
        RemoteKeyProvider provider = new RemoteKeyProvider("http://localhost:8080", null, "test-node");

        assertNotNull(provider);
    }

    @Test
    @DisplayName("测试init方法")
    void testInit() {
        RemoteKeyProvider provider = new RemoteKeyProvider("http://localhost:8080", "test-token", "test-node");

        assertDoesNotThrow(() -> provider.init());
    }

    @Test
    @DisplayName("测试shutdown方法")
    void testShutdown() {
        RemoteKeyProvider provider = new RemoteKeyProvider("http://localhost:8080", "test-token", "test-node");
        provider.init();

        assertDoesNotThrow(() -> provider.shutdown());
    }

    @Test
    @DisplayName("测试releaseKey方法")
    void testReleaseKey() {
        RemoteKeyProvider provider = new RemoteKeyProvider("http://localhost:8080", "test-token", "test-node");
        provider.init();

        assertDoesNotThrow(() -> provider.releaseKey("test-key"));
    }

    @Test
    @DisplayName("测试consumeFlow方法")
    void testConsumeFlow() {
        RemoteKeyProvider provider = new RemoteKeyProvider("http://localhost:8080", "test-token", "test-node");
        provider.init();

        assertDoesNotThrow(() -> provider.consumeFlow("test-key", 10.0));
    }

    @Test
    @DisplayName("测试sendHeartbeat方法 - 返回true")
    void testSendHeartbeat() {
        RemoteKeyProvider provider = new RemoteKeyProvider("http://localhost:8080", "test-token", "test-node");
        provider.init();

        Protocol.HeartbeatPayload payload = new Protocol.HeartbeatPayload();
        payload.serial = "test-serial";
        payload.nodeId = "test-node";

        boolean result = provider.sendHeartbeat(payload);

        assertTrue(result);
    }

    @Test
    @DisplayName("测试getClientUpdateUrl方法")
    void testGetClientUpdateUrl() {
        RemoteKeyProvider provider = new RemoteKeyProvider("http://localhost:8080", "test-token", "test-node");
        provider.init();

        String url = provider.getClientUpdateUrl("jar", "test-serial");

        assertNull(url);
    }
}
