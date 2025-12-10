package neoproxy.neoproxyserver.core.management.provider;

import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.exceptions.PortOccupiedException;
import neoproxy.neoproxyserver.core.management.SequenceKey;
import plethora.thread.ThreadManager;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工业级 NKM 远程适配器
 * 负责与 NKM 服务器进行鉴权、流量上报、状态同步和心跳保活。
 */
public class RemoteKeyProvider implements KeyDataProvider {

    // ==================== 配置常量 ====================
    private static final int SYNC_INTERVAL_SECONDS = 60; // 1分钟同步一次
    private static final double SYNC_THRESHOLD_MB = 50.0;
    private static final int REQUEST_TIMEOUT_MS = 5000;
    private static final int MAX_RETRIES = 1;

    // ==================== 成员变量 ====================
    private final String managerUrl;
    private final String token;
    private final String nodeId;
    private final HttpClient httpClient;

    // 流量缓冲池 (Key -> 流量累加器)
    private final ConcurrentHashMap<String, DoubleAdder> trafficBuffer = new ConcurrentHashMap<>();

    // 刷盘原子锁
    private final AtomicBoolean isFlushing = new AtomicBoolean(false);

    // 单线程调度器
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "NKM-Sync-Thread");
        t.setDaemon(true);
        return t;
    });

    // ==================== 构造与初始化 ====================

    public RemoteKeyProvider(String url, String token, String nodeId) {
        this.managerUrl = url;
        this.token = token;
        this.nodeId = nodeId;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                .executor(Executors.newCachedThreadPool())
                .build();
    }

    @Override
    public void init() {
        scheduler.scheduleAtFixedRate(this::tryTriggerFlush, SYNC_INTERVAL_SECONDS, SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
        // Log: RemoteKeyProvider initialized with URL: {0}, NodeID: {1}
        ServerLogger.info("remoteProvider.initInfo", managerUrl, nodeId);
    }

    // ==================== 1. 登录鉴权 (Login) ====================

    @Override
    public SequenceKey getKey(String name) throws PortOccupiedException {
        try {
            String endpoint = String.format("%s/api/key?name=%s&nodeId=%s", managerUrl, name, nodeId);
            HttpRequest req = buildRequest(endpoint).GET().build();

            HttpResponse<String> response = sendWithRetry(req);

            if (response.statusCode() == 200) {
                return parseKeyFromJson(response.body());
            } else if (response.statusCode() == 409) {
                throw new PortOccupiedException("Max connections reached (Rejected by NKM)");
            } else {
                // Log: Get Key failed for {0}. Status: {1}
                ServerLogger.warn("remoteProvider.getKeyFail", name, response.statusCode());
            }
        } catch (PortOccupiedException e) {
            throw e;
        } catch (Exception e) {
            // Log: Error getting key: {0}
            ServerLogger.error("remoteProvider.getKeyError", e.getMessage());
        }
        return null;
    }

    // ==================== 2. 流量上报与状态同步 (Sync) ====================

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
            trafficBuffer.forEach((k, adder) -> {
                double val = adder.sumThenReset();
                if (val > 0.0001) snapshot.put(k, val);
            });

            for (HostClient client : NeoProxyServer.availableHostClient) {
                if (client.getKey() != null) {
                    snapshot.putIfAbsent(client.getKey().getName(), 0.0);
                }
            }

            if (snapshot.isEmpty()) return;

            String jsonBody = buildSyncJson(snapshot);
            HttpRequest req = buildRequest(managerUrl + Protocol.API_SYNC)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = sendWithRetry(req);

            if (response.statusCode() == 200) {
                processSyncResponse(response.body());
            } else {
                snapshot.forEach((k, v) -> trafficBuffer.computeIfAbsent(k, x -> new DoubleAdder()).add(v));
                // Log: Traffic sync failed. Status: {0}
                ServerLogger.warn("remoteProvider.syncFail", response.statusCode());
            }
        } catch (Exception e) {
            snapshot.forEach((k, v) -> trafficBuffer.computeIfAbsent(k, x -> new DoubleAdder()).add(v));
            // Log: Traffic sync error: {0}
            ServerLogger.error("remoteProvider.syncError", e.getMessage());
        } finally {
            isFlushing.set(false);
        }
    }

    private void processSyncResponse(String json) {
        for (HostClient client : NeoProxyServer.availableHostClient) {
            if (client.getKey() == null) continue;
            String name = client.getKey().getName();

            Double newBalance = extractBalanceFromMetadata(json, name);
            if (newBalance != null) {
                client.getKey().setBalance(newBalance);
            }

            Boolean isValid = extractIsValidFromMetadata(json, name);
            if (isValid != null && !isValid) {
                // Log: Key {0} invalidated by remote server. Disconnecting.
                ServerLogger.warn("remoteProvider.syncKill", name);
                client.close();
            }
        }
    }

    // ==================== 3. 释放 Key (Release) ====================

    @Override
    public void releaseKey(String name) {
        ThreadManager.runAsync(() -> {
            try {
                String body = String.format("{\"serial\":\"%s\", \"nodeId\":\"%s\"}", name, nodeId);
                HttpRequest req = buildRequest(managerUrl + "/api/release")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
            }
        });
    }

    // ==================== 4. 心跳保活 (Heartbeat) ====================

    @Override
    public boolean sendHeartbeat(Protocol.HeartbeatPayload payload) {
        try {
            String jsonBody = String.format(
                    "{\"serial\":\"%s\", \"nodeId\":\"%s\", \"port\":\"%s\"}",
                    payload.serial, payload.nodeId, payload.port
            );

            HttpRequest req = buildRequest(managerUrl + Protocol.API_HEARTBEAT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
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

    // ==================== 5. 生命周期管理 ====================

    @Override
    public void shutdown() {
        scheduler.shutdownNow();
        flushTraffic();
    }

    // ==================== 私有辅助方法 ====================

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

    private String buildSyncJson(Map<String, Double> traffic) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"nodeId\":\"").append(nodeId).append("\",\"traffic\":{");
        int i = 0;
        for (Map.Entry<String, Double> e : traffic.entrySet()) {
            sb.append("\"").append(e.getKey()).append("\":").append(String.format("%.4f", e.getValue()));
            if (++i < traffic.size()) sb.append(",");
        }
        sb.append("}}");
        return sb.toString();
    }

    private SequenceKey parseKeyFromJson(String json) {
        String name = extractString(json, "name");
        if (name == null) return null;
        double balance = extractDouble(json, "balance", 0.0);
        String expireTime = extractString(json, "expireTime");
        String port = extractString(json, "port");
        double rate = extractDouble(json, "rate", 1.0);
        return new SequenceKey(name, balance, expireTime, port, rate, true, false);
    }

    private String extractString(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private double extractDouble(String json, String key, double def) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.-]+)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : def;
    }

    private Boolean extractIsValidFromMetadata(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        String sub = json.substring(idx);
        int end = sub.indexOf("}");
        if (end != -1) sub = sub.substring(0, end);
        if (sub.contains("\"isValid\":false") || sub.contains("\"isValid\": false")) return false;
        if (sub.contains("\"isValid\":true") || sub.contains("\"isValid\": true")) return true;
        return null;
    }

    private Double extractBalanceFromMetadata(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        String sub = json.substring(idx);
        int end = sub.indexOf("}");
        if (end != -1) sub = sub.substring(0, end);
        Matcher m = Pattern.compile("\"balance\"\\s*:\\s*([0-9.-]+)").matcher(sub);
        return m.find() ? Double.parseDouble(m.group(1)) : null;
    }
}