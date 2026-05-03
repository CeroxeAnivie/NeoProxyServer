package neoproxy.neoproxyserver.core.management.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RemoteKeyProvider 测试")
class RemoteKeyProviderTest {

    @Test
    @DisplayName("测试 buildSignature 采用稳定签名顺序")
    void testBuildSignatureUsesStableCanonicalOrder() throws Exception {
        RemoteKeyProvider provider = new RemoteKeyProvider("http://localhost:8080", "token-123", "test-node");

        Method buildSignature = RemoteKeyProvider.class.getDeclaredMethod(
                "buildSignature",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        buildSignature.setAccessible(true);

        String signature = (String) buildSignature.invoke(
                provider,
                "POST",
                Protocol.API_SYNC,
                "1746259200",
                "abc123nonce",
                "{\"nodeId\":\"test-node\"}"
        );

        assertEquals("uSIuoqJ53DG26iGTIwbH4NVTA5a/Y18SelOb6z8qlMg=", signature);
    }

    @Test
    @DisplayName("测试心跳响应只在结构化 status=kill 时终止连接")
    void testHeartbeatKillDecisionUsesStructuredJsonStatus() throws Exception {
        RemoteKeyProvider provider = new RemoteKeyProvider("http://localhost:8080", "token-123", "test-node");

        Method shouldKillHeartbeatResponse = RemoteKeyProvider.class.getDeclaredMethod(
                "shouldKillHeartbeatResponse",
                String.class
        );
        shouldKillHeartbeatResponse.setAccessible(true);

        assertEquals(true, shouldKillHeartbeatResponse.invoke(provider, "{\"status\":\"kill\"}"));
        assertEquals(false, shouldKillHeartbeatResponse.invoke(provider, "{\"status\":\"ok\"}"));
        assertEquals(false, shouldKillHeartbeatResponse.invoke(provider, "not json"));
    }

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
