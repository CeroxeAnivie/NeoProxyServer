package neoproxy.neoproxyserver.core.management.provider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fun.ceroxe.api.thread.ThreadManager;
import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.Debugger;
import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.exceptions.*;
import neoproxy.neoproxyserver.core.management.SequenceKey;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.DoubleAdder;

import static neoproxy.neoproxyserver.core.Debugger.debugOperation;

/**
 * 工业级 NKM 远程适配器 (Golden Fix - Gson Edition)
 * <p>
 * 职责：
 * 1. 与 NKM 进行 HTTP 通信。
 * 2. 使用 Gson 进行健壮的 JSON 解析。
 * 3. 将 NKM 的 RESTful 错误码精准映射为 NPS 的 Java 业务异常。
 * </p>
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
    private final Gson gson; // [新增] Gson 实例
    private final ConcurrentHashMap<String, DoubleAdder> trafficBuffer = new ConcurrentHashMap<>();
    private final AtomicBoolean isFlushing = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "NKM-Sync-Thread");
        t.setDaemon(true);
        return t;
    });

    public RemoteKeyProvider(String url, String token, String nodeId) {
        Debugger.debugOperation("Creating RemoteKeyProvider. URL: " + url + ", NodeID: " + nodeId);
        this.managerUrl = url;
        this.token = token;
        this.nodeId = nodeId;
        this.gson = new Gson(); // 初始化 Gson
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                .executor(Executors.newCachedThreadPool())
                .build();
    }

    @Override
    public void init() {
        Debugger.debugOperation("Initializing RemoteKeyProvider scheduler. Interval: " + SYNC_INTERVAL_SECONDS + "s");
        scheduler.scheduleAtFixedRate(this::tryTriggerFlush, SYNC_INTERVAL_SECONDS, SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
        ServerLogger.info("remoteProvider.initInfo", managerUrl, nodeId);
    }

    // ==================== 1. 登录鉴权 (Login) ====================

    @Override
    public SequenceKey getKey(String name) throws PortOccupiedException, NoMorePortException, OutDatedKeyException, UnRecognizedKeyException {
        Debugger.debugOperation("Remote getKey request: " + name);
        try {
            String endpoint = String.format("%s/api/key?name=%s&nodeId=%s", managerUrl, name, nodeId);
            HttpRequest req = buildRequest(endpoint).GET().build();

            Debugger.debugOperation("Sending HTTP GET to: " + endpoint);
            HttpResponse<String> response = sendWithRetry(req);
            String body = response.body();
            int statusCode = response.statusCode();

            Debugger.debugOperation("Received response: " + statusCode + ", Body: " + body);

            // 1. 成功 (200 OK)
            if (statusCode == 200) {
                NkmKeyResponse resp = gson.fromJson(body, NkmKeyResponse.class);
                return new SequenceKey(
                        resp.name,
                        resp.balance,
                        resp.expireTime,
                        resp.port,
                        resp.rate,
                        true, // 200 OK 意味着状态是 Enabled
                        resp.enableWebHTML
                );
            }

            // 2. 鉴权失败/找不到 (403/404) -> UnRecognizedKeyException
            if (statusCode == 404) {
                UnRecognizedKeyException.throwException(name);
            }
            if (statusCode == 403) {
                // 403 通常是管理员手动禁用 (DISABLED)
                UnRecognizedKeyException.throwException(name);
            }

            // 3. 业务冲突 (409 Conflict) -> 细分异常
            if (statusCode == 409) {
                NkmApiError error = gson.fromJson(body, NkmApiError.class);
                if (error == null) throw new RuntimeException("Invalid NKM Error Response");

                String reason = error.reason != null ? error.reason : "";
                String errType = error.error != null ? error.error : "";

                // A. 状态为 PAUSED (欠费/过期)
                if ("PAUSED".equalsIgnoreCase(error.status)) {
                    if (reason.contains("Expired")) {
                        // 映射为过期异常
                        OutDatedKeyException.throwException(name);
                    } else if (reason.contains("Balance") || reason.contains("Depleted")) {
                        // 映射为流量耗尽异常 (RuntimeException)
                        NoMoreNetworkFlowException.throwException("NKM-Handshake", "exception.insufficientBalance", name);
                    } else {
                        // 默认视为过期或无效
                        OutDatedKeyException.throwException(name);
                    }
                }

                // B. 状态为 ENABLED (连接数/端口冲突)
                // 场景: "Too Many Connections" -> 全局连接数超限
                if (errType.contains("Too Many Connections")) {
                    PortOccupiedException.throwException(name);
                }

                // 场景: "Port Conflict" -> 特定端口被占用
                if (errType.contains("Port Conflict")) {
                    // 尝试从 reason 中提取端口号，或者直接抛出通用异常
                    // reason: "Port 12345 is busy"
                    NoMorePortException.throwException();
                }

                // 兜底
                PortOccupiedException.throwException(name);
            }

            // 其他未知错误
            ServerLogger.warn("remoteProvider.getKeyFail", name, statusCode);

        } catch (PortOccupiedException | NoMorePortException | OutDatedKeyException | UnRecognizedKeyException |
                 NoMoreNetworkFlowException e) {
            throw e; // 业务异常直接抛出
        } catch (Exception e) {
            debugOperation(e);
            ServerLogger.error("remoteProvider.getKeyError", e.getMessage());
        }
        return null;
    }

    // ==================== 2. 流量上报与同步 (Sync) ====================

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
        ConcurrentHashMap<String, Double> snapshot = new ConcurrentHashMap<>();
        try {
            // 准备数据
            trafficBuffer.forEach((k, adder) -> {
                double val = adder.sumThenReset();
                if (val > 0.0001) snapshot.put(k, val);
            });
            for (HostClient client : NeoProxyServer.availableHostClient) {
                if (client.getKey() != null) snapshot.putIfAbsent(client.getKey().getName(), 0.0);
            }

            if (snapshot.isEmpty()) return;

            // 构建请求 JSON (使用 Gson)
            JsonObject jsonRoot = new JsonObject();
            jsonRoot.addProperty("nodeId", nodeId);
            JsonObject trafficObj = new JsonObject();
            snapshot.forEach(trafficObj::addProperty);
            jsonRoot.add("traffic", trafficObj);

            HttpRequest req = buildRequest(managerUrl + Protocol.API_SYNC)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(jsonRoot)))
                    .build();

            HttpResponse<String> response = sendWithRetry(req);

            if (response.statusCode() == 200) {
                processSyncResponse(response.body());
            } else {
                // 回滚流量
                snapshot.forEach((k, v) -> trafficBuffer.computeIfAbsent(k, x -> new DoubleAdder()).add(v));
                ServerLogger.warn("remoteProvider.syncFail", response.statusCode());
            }
        } catch (Exception e) {
            debugOperation(e);
            snapshot.forEach((k, v) -> trafficBuffer.computeIfAbsent(k, x -> new DoubleAdder()).add(v));
        } finally {
            isFlushing.set(false);
        }
    }

    private void processSyncResponse(String jsonBody) {
        try {
            NkmSyncResponse syncResp = gson.fromJson(jsonBody, NkmSyncResponse.class);
            if (syncResp == null || syncResp.metadata == null) return;

            for (HostClient client : NeoProxyServer.availableHostClient) {
                if (client.getKey() == null) continue;
                String name = client.getKey().getName();

                NkmKeyMetadata meta = syncResp.metadata.get(name);
                if (meta == null) continue;

                SequenceKey key = client.getKey();

                // 1. 状态同步 (Kill)
                if (!meta.isValid) {
                    ServerLogger.warn("remoteProvider.syncKill", name, meta.reason);
                    client.close();
                    continue;
                }

                // 2. 属性热更新
                if (meta.balance != null) key.setBalance(meta.balance);
                if (meta.rate != null) {
                    key.setRate(meta.rate);
                    client.applyDynamicUpdates(); // 通知客户端刷新限速
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
        ThreadManager.runAsync(() -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("serial", name);
                json.addProperty("nodeId", nodeId);

                HttpRequest req = buildRequest(managerUrl + "/api/release")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(json)))
                        .build();
                httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public boolean sendHeartbeat(Protocol.HeartbeatPayload payload) {
        try {
            HttpRequest req = buildRequest(managerUrl + Protocol.API_HEARTBEAT)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // 简单判断包含 status: kill 即可，无需全量解析
                return !response.body().contains("\"status\":\"kill\"");
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public void shutdown() {
        scheduler.shutdownNow();
        flushTraffic();
    }

    private HttpRequest.Builder buildRequest(String uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS));
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
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

    // ========== DTOs (Data Transfer Objects) ==========

    // 对应 NKM KeyInfoResponse
    private static class NkmKeyResponse {
        String name;
        double balance;
        double rate;
        String expireTime;
        String port;
        boolean enableWebHTML;
    }

    // 对应 NKM ApiError
    private static class NkmApiError {
        String error;
        String reason;
        String status; // PAUSED, DISABLED, ENABLED
    }

    // 对应 NKM SyncResponse
    private static class NkmSyncResponse {
        String status;
        Map<String, NkmKeyMetadata> metadata;
    }

    // 对应 NKM KeyMetadata
    private static class NkmKeyMetadata {
        boolean isValid;
        String reason;
        Double balance;
        Double rate;
        String expireTime;
        Boolean enableWebHTML;
    }
}