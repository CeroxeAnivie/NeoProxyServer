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
 * 工业级 NKM 远程适配器 (Golden Fix)
 * <p>
 * 职责：
 * 1. 负责与 NKM 进行 HTTP 通信。
 * 2. 负责将 JSON 数据反序列化为 SequenceKey 对象。
 * 3. 负责 Sync 协议的流量上报与元数据下发。
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
    private final ConcurrentHashMap<String, DoubleAdder> trafficBuffer = new ConcurrentHashMap<>();
    private final AtomicBoolean isFlushing = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "NKM-Sync-Thread");
        t.setDaemon(true);
        return t;
    });

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
                // 【修复点 1】此处调用修复后的 JSON 解析逻辑
                return parseKeyFromJson(response.body());
            } else if (response.statusCode() == 409) {
                throw new PortOccupiedException("Max connections reached (Rejected by NKM)");
            } else {
                ServerLogger.warn("remoteProvider.getKeyFail", name, response.statusCode());
            }
        } catch (PortOccupiedException e) {
            throw e;
        } catch (Exception e) {
            ServerLogger.error("remoteProvider.getKeyError", e.getMessage());
        }
        return null;
    }

    // ==================== 2. 流量上报与全量状态同步 (Sync) ====================

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
            // 1. 提取流量快照
            trafficBuffer.forEach((k, adder) -> {
                double val = adder.sumThenReset();
                if (val > 0.0001) snapshot.put(k, val);
            });

            // 2. 确保在线 Key 即使无流量也能接收 Sync 更新
            for (HostClient client : NeoProxyServer.availableHostClient) {
                if (client.getKey() != null) {
                    snapshot.putIfAbsent(client.getKey().getName(), 0.0);
                }
            }

            if (snapshot.isEmpty()) return;

            // 3. 发送请求
            String jsonBody = buildSyncJson(snapshot);
            HttpRequest req = buildRequest(managerUrl + Protocol.API_SYNC)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = sendWithRetry(req);

            if (response.statusCode() == 200) {
                // 【修复点 2】处理全量同步响应
                processSyncResponse(response.body());
            } else {
                // 失败回滚流量
                snapshot.forEach((k, v) -> trafficBuffer.computeIfAbsent(k, x -> new DoubleAdder()).add(v));
                ServerLogger.warn("remoteProvider.syncFail", response.statusCode());
            }
        } catch (Exception e) {
            snapshot.forEach((k, v) -> trafficBuffer.computeIfAbsent(k, x -> new DoubleAdder()).add(v));
            ServerLogger.error("remoteProvider.syncError", e.getMessage());
        } finally {
            isFlushing.set(false);
        }
    }

    /**
     * 处理 Sync 响应，实现配置热更新
     * 遵循职责分离：只更新 SequenceKey 数据模型，不直接操作 HostClient 的 Socket/Threads
     */
    private void processSyncResponse(String json) {
        for (HostClient client : NeoProxyServer.availableHostClient) {
            if (client.getKey() == null) continue;

            SequenceKey key = client.getKey();
            String name = key.getName();

            // 1. 同步有效性 (IsValid)
            Boolean isValid = extractBooleanFromMetadata(json, name, "isValid");
            if (isValid != null && !isValid) {
                String reason = extractStringFromMetadata(json, name, "reason");
                ServerLogger.warn("remoteProvider.syncKill", name, reason);
                client.close(); // 唯一例外：Kill 信号需要立即断开
                continue;
            }

            // 2. 同步余额 (Balance)
            Double newBalance = extractDoubleFromMetadata(json, name, "balance");
            if (newBalance != null) key.setBalance(newBalance);

            // 3. 同步 Web 开关 (WebHTML) - 修复 Bug 的核心
            Boolean webEnabled = extractBooleanFromMetadata(json, name, "enableWebHTML");
            if (webEnabled != null) key.setHTMLEnabled(webEnabled);

            // 4. 同步限速 (Rate)
            Double newRate = extractDoubleFromMetadata(json, name, "rate");
            if (newRate != null) {
                key.setRate(newRate);
                // 触发 HostClient 内部的动态限速更新（如果在 HostClient 中有此方法）
                // 即使没有，RateLimiter 下次读取 Rate 时也会生效
                client.getGlobalRateLimiter().setMaxMbps(newRate);
            }

            // 5. 同步过期时间 (ExpireTime)
            String newExpire = extractStringFromMetadata(json, name, "expireTime");
            if (newExpire != null) key.setExpireTime(newExpire);
        }
    }

    // ==================== 3. 其他接口实现 ====================

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

    @Override
    public void shutdown() {
        scheduler.shutdownNow();
        flushTraffic();
    }

    // ==================== 私有辅助方法 (Robust JSON Parsing) ====================

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

    // 【核心修复】初始化 Key 时读取 enableWebHTML
    private SequenceKey parseKeyFromJson(String json) {
        String name = extractString(json, "name");
        if (name == null) return null;
        double balance = extractDouble(json, "balance", 0.0);
        String expireTime = extractString(json, "expireTime");
        String port = extractString(json, "port");
        double rate = extractDouble(json, "rate", 1.0);

        // 修复：从 JSON 动态解析，不再硬编码 false
        boolean enableWebHTML = extractBoolean(json, "enableWebHTML", false);

        return new SequenceKey(name, balance, expireTime, port, rate, true, enableWebHTML);
    }

    // --- High-Performance Regex Based Extraction Helpers ---

    private String extractString(String json, String key) {
        // 匹配 "key" : "value"
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private double extractDouble(String json, String key, double def) {
        // 匹配 "key" : 123.45
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.-]+)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : def;
    }

    private boolean extractBoolean(String json, String key, boolean def) {
        // 匹配 "key" : true 或 false (忽略大小写)
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE).matcher(json);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : def;
    }

    // --- Metadata Block Extraction (Optimized for Protocol structure) ---

    // 提取 sync 响应中某个 key 对应的 metadata JSON 块
    private String getMetadataBlock(String json, String keyName) {
        // 这里的假设是 NKM 返回的 metadata 结构扁平，不包含复杂的嵌套对象
        int idx = json.indexOf("\"" + keyName + "\"");
        if (idx == -1) return null;
        String sub = json.substring(idx);
        int end = sub.indexOf("}"); // 查找当前对象的结束符
        if (end != -1) sub = sub.substring(0, end);
        return sub;
    }

    private Double extractDoubleFromMetadata(String json, String keyName, String fieldName) {
        String block = getMetadataBlock(json, keyName);
        if (block == null) return null;
        // 使用 Double 包装类来区分 "存在且为0" 和 "不存在"
        Matcher m = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*([0-9.-]+)").matcher(block);
        return m.find() ? Double.parseDouble(m.group(1)) : null;
    }

    private Boolean extractBooleanFromMetadata(String json, String keyName, String fieldName) {
        String block = getMetadataBlock(json, keyName);
        if (block == null) return null;
        Matcher m = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE).matcher(block);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : null;
    }

    private String extractStringFromMetadata(String json, String keyName, String fieldName) {
        String block = getMetadataBlock(json, keyName);
        if (block == null) return null;
        return extractString(block, fieldName);
    }
}