package neoproxy.neoproxyserver.core.management.provider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import top.ceroxe.api.thread.ThreadManager;
import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.Debugger;
import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.exceptions.*;
import neoproxy.neoproxyserver.core.management.SequenceKey;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.DoubleAdder;

import static neoproxy.neoproxyserver.core.Debugger.debugOperation;

/**
 * 工业级 NKM 远程适配器 (Golden Fix - Gson Edition)
 */
public class RemoteKeyProvider implements KeyDataProvider {

    // ==================== 配置常量 ====================
    private static final int SYNC_INTERVAL_SECONDS = 60;
    private static final double SYNC_THRESHOLD_MB = 50.0;
    private static final int REQUEST_TIMEOUT_MS = 5000;
    private static final int MAX_RETRIES = 1;

    // ==================== 成员变量 ====================
    private final String managerUrl;
    private final String token;
    private final String nodeId;
    private final HttpClient httpClient;
    private final Gson gson;
    private final ExecutorService httpExecutor;
    private final ConcurrentHashMap<String, DoubleAdder> trafficBuffer = new ConcurrentHashMap<>();
    private final AtomicBoolean isFlushing = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(NeoProxyServer.LOW_RAM_MODE ? 1 : 2, r -> {
        Thread t = new Thread(r, "NKM-Worker-Thread");
        t.setDaemon(true);
        return t;
    });

    public RemoteKeyProvider(String url, String token, String nodeId) {
        Debugger.debugOperation("Creating RemoteKeyProvider. URL: " + url + ", NodeID: " + nodeId);
        this.managerUrl = url;
        this.token = token;
        this.nodeId = nodeId;
        this.gson = new Gson();
        this.httpExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("NKM-Http-Thread-", 0).factory()
        );
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                .executor(httpExecutor)
                .build();
    }

    @Override
    public void init() {
        Debugger.debugOperation("Initializing RemoteKeyProvider...");
        scheduler.scheduleAtFixedRate(this::tryTriggerFlush, SYNC_INTERVAL_SECONDS, SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::reportNodeStatus, 5, Protocol.NODE_STATUS_INTERVAL_SECONDS, TimeUnit.SECONDS);
        ServerLogger.info("remoteProvider.initInfo", managerUrl, nodeId);
    }

    // ==================== [新增] 获取客户端更新 URL ====================

    /**
     * 向 NKM 请求特定 OS 和 Key 的客户端下载 URL
     *
     * @param os           "exe" 或 "jar"
     * @param clientSerial 客户端使用的密钥 Serial
     * @return URL 字符串，如果获取失败或不可用则返回 null
     */
    public String getClientUpdateUrl(String os, String clientSerial) {
        Debugger.debugOperation("Requesting update URL from NKM. OS: " + os + ", Key: " + clientSerial);
        try {
            // 构建请求 URL: /api/node/client/update-url?os=xxx&serial=xxx&nodeId=xxx
            String endpoint = String.format("%s%s?os=%s&serial=%s&nodeId=%s",
                    managerUrl,
                    Protocol.API_CLIENT_UPDATE_URL,
                    encodeQueryValue(os),
                    encodeQueryValue(clientSerial),
                    encodeQueryValue(nodeId));

            HttpRequest req = buildRequest(endpoint, "GET", "").GET().build();
            HttpResponse<String> response = sendWithRetry(req);

            if (response.statusCode() == 200) {
                String body = response.body();
                Debugger.debugOperation("NKM Update Response: " + body);

                // 解析 JSON
                Protocol.UpdateUrlResponse resp = gson.fromJson(body, Protocol.UpdateUrlResponse.class);
                if (resp != null && resp.url != null && !resp.url.isBlank()) {
                    return resp.url;
                }
            } else {
                Debugger.debugOperation("NKM Update Request failed. Code: " + response.statusCode());
            }
        } catch (Exception e) {
            Debugger.debugOperation("Error requesting update URL: " + e.getMessage());
        }
        return null;
    }

    // ==================== 0. 节点状态上报 ====================

    private void reportNodeStatus() {
        Debugger.debugOperation("Reporting node status to NKM...");
        try {
            Protocol.NodeStatusPayload payload = new Protocol.NodeStatusPayload();
            payload.nodeId = this.nodeId;
            payload.address = NeoProxyServer.LOCAL_DOMAIN_NAME;
            payload.hookPort = NeoProxyServer.HOST_HOOK_PORT;
            payload.connectPort = NeoProxyServer.HOST_CONNECT_PORT;
            payload.version = NeoProxyServer.VERSION;
            payload.timestamp = System.currentTimeMillis();
            payload.activeTunnels = NeoProxyServer.availableHostClient.size();

            String body = gson.toJson(payload);
            HttpRequest req = buildRequest(managerUrl + Protocol.API_NODE_STATUS, "POST", body)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                Debugger.debugOperation("Node status report failed: " + response.statusCode());
            }
        } catch (Exception e) {
            Debugger.debugOperation("Error reporting node status: " + e.getMessage());
        }
    }

    // ==================== 1. 登录鉴权 (Login) ====================

    @Override
    public SequenceKey getKey(String name) throws PortOccupiedException, NoMorePortException, OutDatedKeyException, UnRecognizedKeyException {
        // ... (保持原有代码不变)
        Debugger.debugOperation("Remote getKey request: " + name);
        try {
            String endpoint = String.format("%s/api/key?name=%s&nodeId=%s",
                    managerUrl,
                    encodeQueryValue(name),
                    encodeQueryValue(nodeId));
            HttpRequest req = buildRequest(endpoint, "GET", "").GET().build();

            HttpResponse<String> response = sendWithRetry(req);
            String body = response.body();
            int statusCode = response.statusCode();

            if (statusCode == 200) {
                NkmKeyResponse resp = gson.fromJson(body, NkmKeyResponse.class);
                return new SequenceKey(
                        resp.name,
                        resp.balance,
                        resp.expireTime,
                        resp.port,
                        resp.rate,
                        true,
                        resp.enableWebHTML
                );
            }

            try {
                if (body != null && !body.isEmpty()) {
                    NkmApiError error = gson.fromJson(body, NkmApiError.class);
                    if (error != null && error.customBlockingMessage != null && !error.customBlockingMessage.isBlank()) {
                        throw new BlockingMessageException(error.customBlockingMessage);
                    }
                }
            } catch (BlockingMessageException bme) {
                throw bme;
            } catch (Exception ignored) {
            }

            if (statusCode == 404 || statusCode == 403) {
                UnRecognizedKeyException.throwException(name);
            }

            if (statusCode == 409) {
                NkmApiError error = gson.fromJson(body, NkmApiError.class);
                if (error == null) throw new RuntimeException("Invalid NKM Error Response");

                String reason = error.reason != null ? error.reason : "";
                String errType = error.error != null ? error.error : "";

                if ("PAUSED".equalsIgnoreCase(error.status)) {
                    if (reason.contains("Expired")) {
                        OutDatedKeyException.throwException(name);
                    } else if (reason.contains("Balance") || reason.contains("Depleted")) {
                        NoMoreNetworkFlowException.throwException("NKM-Handshake", "exception.insufficientBalance", name);
                    } else {
                        OutDatedKeyException.throwException(name);
                    }
                }
                if (errType.contains("Too Many Connections")) {
                    PortOccupiedException.throwException(name);
                }
                if (errType.contains("Port Conflict")) {
                    NoMorePortException.throwException();
                }
                PortOccupiedException.throwException(name);
            }
        } catch (PortOccupiedException | NoMorePortException | OutDatedKeyException | UnRecognizedKeyException |
                 NoMoreNetworkFlowException e) {
            throw e;
        } catch (BlockingMessageException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            if (e.getCause() instanceof BlockingMessageException) {
                throw new RuntimeException(e.getCause());
            }
            debugOperation(e);
            ServerLogger.error("remoteProvider.getKeyError", e.getMessage());
        }
        return null;
    }

    // ==================== 2. 流量上报与同步 (Sync) ====================
    // ... (保持原有代码不变)

    @Override
    public void consumeFlow(String name, double mib) {
        trafficBuffer.computeIfAbsent(name, k -> new DoubleAdder()).add(mib);
        if (trafficBuffer.get(name).sum() >= SYNC_THRESHOLD_MB) {
            tryTriggerFlush();
        }
    }

    private void tryTriggerFlush() {
        if (isFlushing.compareAndSet(false, true)) {
            ThreadManager.runAsync(this::flushTraffic);
        }
    }

    private void flushTraffic() {
        // ... (保持原有代码不变)
        ConcurrentHashMap<String, Double> snapshot = new ConcurrentHashMap<>();
        try {
            trafficBuffer.forEach((k, adder) -> {
                double val = adder.sumThenReset();
                if (val > 0.0001) snapshot.put(k, val);
            });
            for (HostClient client : NeoProxyServer.availableHostClient) {
                if (client.getKey() != null) snapshot.putIfAbsent(client.getKey().getName(), 0.0);
            }

            if (snapshot.isEmpty()) return;

            JsonObject jsonRoot = new JsonObject();
            jsonRoot.addProperty("nodeId", nodeId);
            JsonObject trafficObj = new JsonObject();
            snapshot.forEach(trafficObj::addProperty);
            jsonRoot.add("traffic", trafficObj);

            String body = gson.toJson(jsonRoot);
            HttpRequest req = buildRequest(managerUrl + Protocol.API_SYNC, "POST", body)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = sendWithRetry(req);

            if (response.statusCode() == 200) {
                processSyncResponse(response.body());
            } else {
                snapshot.forEach((k, v) -> trafficBuffer.computeIfAbsent(k, x -> new DoubleAdder()).add(v));
            }
        } catch (Exception e) {
            debugOperation(e);
            snapshot.forEach((k, v) -> trafficBuffer.computeIfAbsent(k, x -> new DoubleAdder()).add(v));
        } finally {
            isFlushing.set(false);
        }
    }

    private void processSyncResponse(String jsonBody) {
        // ... (保持原有代码不变)
        try {
            NkmSyncResponse syncResp = gson.fromJson(jsonBody, NkmSyncResponse.class);
            if (syncResp == null || syncResp.metadata == null) return;

            for (HostClient client : NeoProxyServer.availableHostClient) {
                if (client.getKey() == null) continue;
                String name = client.getKey().getName();

                NkmKeyMetadata meta = syncResp.metadata.get(name);
                if (meta == null) continue;

                SequenceKey key = client.getKey();
                if (!meta.isValid) {
                    client.close();
                    continue;
                }
                if (meta.balance != null) key.setBalance(meta.balance);
                if (meta.rate != null) {
                    key.setRate(meta.rate);
                    client.applyDynamicUpdates();
                }
                if (meta.expireTime != null) key.setExpireTime(meta.expireTime);
                if (meta.enableWebHTML != null) key.setHTMLEnabled(meta.enableWebHTML);
            }
        } catch (Exception e) {
            ServerLogger.error("remoteProvider.syncParseError", e.getMessage());
        }
    }

    // ==================== 3. 辅助方法与 DTO ====================

    @Override
    public void releaseKey(String name) {
        // ... (保持原有代码不变)
        ThreadManager.runAsync(() -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("serial", name);
                json.addProperty("nodeId", nodeId);
                String body = gson.toJson(json);
                HttpRequest req = buildRequest(managerUrl + "/api/release", "POST", body)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public boolean sendHeartbeat(Protocol.HeartbeatPayload payload) {
        // ... (保持原有代码不变)
        try {
            String body = gson.toJson(payload);
            HttpRequest req = buildRequest(managerUrl + Protocol.API_HEARTBEAT, "POST", body)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return !response.body().contains("\"status\":\"kill\"");
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public void shutdown() {
        // ... (保持原有代码不变)
        Debugger.debugOperation("Shutting down RemoteKeyProvider...");
        scheduler.shutdownNow();
        flushTraffic();
        httpExecutor.shutdownNow();
        Debugger.debugOperation("RemoteKeyProvider shutdown complete.");
    }

    private static String encodeQueryValue(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private HttpRequest.Builder buildRequest(String uri, String method, String body) {
        URI parsedUri = URI.create(uri);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(parsedUri)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS));
        if (token != null && !token.isEmpty()) {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String nonce = UUID.randomUUID().toString().replace("-", "");
            builder.header("Authorization", "Bearer " + token);
            builder.header("X-Timestamp", timestamp);
            builder.header("X-Nonce", nonce);
            builder.header("X-Signature", buildSignature(method, parsedUri.getPath(), timestamp, nonce, body));
        }
        return builder;
    }

    private String buildSignature(String method, String path, String timestamp, String nonce, String body) {
        try {
            String data = method + "|" + path + "|" + timestamp + "|" + nonce + "|" + (body == null ? "" : body);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign NKM node request", e);
        }
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) throws Exception {
        Exception last = null;
        for (int i = 0; i <= MAX_RETRIES; i++) {
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                last = e;
                if (i < MAX_RETRIES) Thread.sleep(200);
            }
        }
        throw last;
    }

    // ========== DTO 数据结构 ==========

    // ... (内部类保持不变)
    private static class NkmKeyResponse {
        String name;
        double balance;
        double rate;
        String expireTime;
        String port;
        boolean enableWebHTML;
    }

    private static class NkmApiError {
        String error;
        String reason;
        String status;
        String customBlockingMessage;
    }

    private static class NkmSyncResponse {
        String status;
        Map<String, NkmKeyMetadata> metadata;
    }

    private static class NkmKeyMetadata {
        boolean isValid;
        String reason;
        Double balance;
        Double rate;
        String expireTime;
        Boolean enableWebHTML;
    }
}
