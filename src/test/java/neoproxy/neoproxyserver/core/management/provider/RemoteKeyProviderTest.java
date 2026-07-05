package neoproxy.neoproxyserver.core.management.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    @DisplayName("测试 releaseKey 返回前已发送释放请求")
    void testReleaseKeySendsRequestBeforeReturning() throws Exception {
        AtomicReference<String> requestText = new AtomicReference<>();
        CountDownLatch responseSent = new CountDownLatch(1);

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread serverThread = Thread.startVirtualThread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(2000);
                    requestText.set(readHttpRequest(socket));

                    byte[] response = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}"
                            .getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(response);
                    socket.getOutputStream().flush();
                    responseSent.countDown();
                } catch (Exception ignored) {
                }
            });

            RemoteKeyProvider provider = new RemoteKeyProvider(
                    "http://127.0.0.1:" + server.getLocalPort(),
                    "",
                    "test-node"
            );

            provider.releaseKey("test-key");

            assertTrue(responseSent.await(100, TimeUnit.MILLISECONDS));
            serverThread.join(1000);
        }

        String request = requestText.get();
        assertNotNull(request);
        assertTrue(request.startsWith("POST " + Protocol.API_RELEASE + " "));
        assertTrue(request.contains("\"serial\":\"test-key\""));
        assertTrue(request.contains("\"nodeId\":\"test-node\""));
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

    private static String readHttpRequest(Socket socket) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[256];
        int contentLength = -1;

        while (true) {
            int read = socket.getInputStream().read(chunk);
            if (read == -1) {
                break;
            }
            buffer.write(chunk, 0, read);

            String text = buffer.toString(StandardCharsets.UTF_8);
            int bodyStart = text.indexOf("\r\n\r\n");
            if (bodyStart < 0) {
                continue;
            }
            if (contentLength < 0) {
                contentLength = parseContentLength(text.substring(0, bodyStart));
            }
            int receivedBodyBytes = text.substring(bodyStart + 4).getBytes(StandardCharsets.UTF_8).length;
            if (receivedBodyBytes >= contentLength) {
                break;
            }
        }

        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static int parseContentLength(String headers) {
        for (String header : headers.split("\r\n")) {
            int separator = header.indexOf(':');
            if (separator > 0 && "content-length".equalsIgnoreCase(header.substring(0, separator).trim())) {
                return Integer.parseInt(header.substring(separator + 1).trim());
            }
        }
        return 0;
    }
}
