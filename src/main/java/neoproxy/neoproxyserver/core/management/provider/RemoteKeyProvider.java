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

public class RemoteKeyProvider implements KeyDataProvider {
    // 【核心配置】同步周期 60秒
    private static final int SYNC_INTERVAL_SECONDS = 60;
    // 流量缓冲阈值 (50MB)，超过立即上报
    private static final double SYNC_THRESHOLD_MB = 50.0;

    private static final int REQUEST_TIMEOUT_MS = 5000;
    private static final int MAX_RETRIES = 1;

    private final String managerUrl;
    private final String token;
    private final String nodeId;
    private final LocalKeyProvider localFallback;
    private final HttpClient httpClient;

    // 高并发流量累加器
    private final ConcurrentHashMap<String, DoubleAdder> trafficBuffer = new ConcurrentHashMap<>();
    private final AtomicBoolean isFlushing = new AtomicBoolean(false);

    // 单线程调度器，用于执行定时同步任务
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "NKM-Sync-Thread");
        t.setDaemon(true);
        return t;
    });

    public RemoteKeyProvider(String url, String token, String nodeId) {
        this.managerUrl = url;
        this.token = token;
        this.nodeId = nodeId;
        this.localFallback = new LocalKeyProvider();

        // 使用 Java 11+ HttpClient，配合虚拟线程（如果 Java 版本支持）或默认线程池
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    @Override
    public void init() {
        localFallback.init();
        // 启动 60秒 定时同步任务
        scheduler.scheduleAtFixedRate(this::tryTriggerFlush, SYNC_INTERVAL_SECONDS, SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
        ServerLogger.info("remoteProvider.initInfo", managerUrl, nodeId);
    }

    // =============================================================
    // 1. 心跳接口 (由 HostClient 每5秒调用)
    // =============================================================
    @Override
    public boolean sendHeartbeat(Protocol.HeartbeatPayload payload) {
        try {
            // 手动构建 JSON 避免依赖
            String jsonBody = String.format(
                    "{\"serial\":\"%s\", \"nodeId\":\"%s\", \"port\":\"%s\", \"timestamp\":%d, \"currentConnections\":%d}",
                    payload.serial, payload.nodeId, payload.port, payload.timestamp, payload.currentConnections
            );

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(managerUrl + Protocol.API_HEARTBEAT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS));

            if (token != null && !token.isEmpty()) reqBuilder.header("Authorization", "Bearer " + token);

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // 如果返回 kill，则通知断开
                return !response.body().contains("\"status\":\"kill\"");
            }
            // 网络错误默认保活，防止误杀
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    // =============================================================
    // 2. 流量上报与状态回拉 (每60秒或流量激增时触发)
    // =============================================================

    @Override
    public void consumeFlow(String name, double mib) {
        trafficBuffer.computeIfAbsent(name, k -> new DoubleAdder()).add(mib);
        // 检查是否达到立即上报阈值
        if (trafficBuffer.get(name).sum() >= SYNC_THRESHOLD_MB) {
            tryTriggerFlush();
        }
    }

    private void tryTriggerFlush() {
        // CAS 锁防止并发执行
        if (isFlushing.compareAndSet(false, true)) {
            Thread.ofVirtual().start(this::flushTraffic);
        }
    }

    private void flushTraffic() {
        ConcurrentHashMap<String, Double> snapshot = new ConcurrentHashMap<>();
        try {
            // 1. 提取并重置流量数据
            trafficBuffer.forEach((k, adder) -> {
                double val = adder.sumThenReset();
                if (val > 0.0001) snapshot.put(k, val);
            });

            // 构造请求
            String jsonBody = buildSyncJson(snapshot);
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(managerUrl + Protocol.API_SYNC))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS));

            if (token != null && !token.isEmpty()) reqBuilder.header("Authorization", "Bearer " + token);

            // 发送请求
            HttpResponse<String> response = sendWithRetry(reqBuilder.build());

            if (response.statusCode() == 200) {
                // A. 处理服务端返回的强制下线列表
                String resBody = response.body();
                if (resBody.contains("kill_keys")) {
                    processKillKeys(resBody);
                }

                // B. 【核心逻辑】上报成功后，立即拉取最新 Key 状态
                refreshActiveKeys();

            } else {
                // 失败回滚：将流量加回缓冲区
                snapshot.forEach((k, v) -> trafficBuffer.computeIfAbsent(k, x -> new DoubleAdder()).add(v));
                ServerLogger.warn("remoteProvider.syncFail", response.statusCode());
            }
        } catch (Exception e) {
            // 异常回滚
            snapshot.forEach((k, v) -> trafficBuffer.computeIfAbsent(k, x -> new DoubleAdder()).add(v));
            ServerLogger.error("remoteProvider.syncError", e);
        } finally {
            isFlushing.set(false);
        }
    }

    /**
     * 主动拉取所有在线客户端的最新 Key 信息，实现状态热更新
     */
    private void refreshActiveKeys() {
        // 遍历当前 NPS 所有活跃连接
        for (HostClient client : NeoProxyServer.availableHostClient) {
            if (client.getKey() == null) continue;

            String keyName = client.getKey().getName();

            // 使用虚拟线程并发拉取，互不阻塞
            Thread.ofVirtual().start(() -> {
                try {
                    // 调用 getKey 实际上是请求 GET /api/key，获取 NKM 里的最新配置
                    SequenceKey freshKey = this.getKey(keyName);

                    if (freshKey != null) {
                        // 1. 更新 Key 对象属性 (余额, 限速, 过期时间等)
                        client.getKey().refreshFrom(freshKey);

                        // 2. 通知 Client 应用动态变更 (如重置限速器)
                        client.applyDynamicUpdates();

                        // 3. 检查硬性指标 (禁用或余额耗尽)
                        // 注意：这里余额判断要小心，部分系统 -1 代表无限
                        if (!freshKey.isEnable() || (freshKey.getBalance() <= 0 && freshKey.getBalance() > -100)) {
                            ServerLogger.warn("remoteProvider.stateChangedKill", keyName);
                            client.close();
                        }
                    }
                } catch (Exception ignored) {
                    // 拉取失败忽略，等待下一次同步
                }
            });
        }
    }

    // =============================================================
    // 3. 基础方法与工具
    // =============================================================

    @Override
    public SequenceKey getKey(String name) throws PortOccupiedException {
        try {
            String endpoint = String.format("%s/api/key?name=%s&nodeId=%s", managerUrl, name, nodeId);
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .GET()
                    .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS));

            if (token != null && !token.isEmpty()) reqBuilder.header("Authorization", "Bearer " + token);

            HttpResponse<String> response = sendWithRetry(reqBuilder.build());

            if (response.statusCode() == 200) {
                return parseKeyFromJson(response.body());
            } else if (response.statusCode() == 409) {
                PortOccupiedException.throwException("Rejected by Manager");
            }
        } catch (PortOccupiedException e) {
            throw e;
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @Override
    public void releaseKey(String name) {
        ThreadManager.runAsync(() -> {
            try {
                String endpoint = String.format("%s/api/release?name=%s&nodeId=%s", managerUrl, name, nodeId);
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(endpoint))
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void shutdown() {
        scheduler.shutdownNow();
        // 尝试最后一次提交
        if (isFlushing.compareAndSet(false, true)) {
            flushTraffic();
        }
    }

    // --- JSON 解析与辅助工具 ---

    private HttpResponse<String> sendWithRetry(HttpRequest request) throws Exception {
        Exception last = null;
        for (int i = 0; i <= MAX_RETRIES; i++) {
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                last = e;
                Thread.sleep(500);
            }
        }
        throw last;
    }

    private void processKillKeys(String json) {
        try {
            int start = json.indexOf("[");
            int end = json.lastIndexOf("]");
            if (start != -1 && end != -1) {
                String content = json.substring(start + 1, end);
                if (!content.isBlank()) {
                    String[] keys = content.split(",");
                    for (String k : keys) {
                        kickClient(k.trim().replace("\"", ""));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void kickClient(String keyName) {
        for (HostClient client : NeoProxyServer.availableHostClient) {
            if (client.getKey() != null && client.getKey().getName().equals(keyName)) {
                ServerLogger.warn("remoteProvider.forceKill", keyName);
                client.close();
            }
        }
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
        try {
            String name = extractString(json, "name");
            if (name == null) return null;
            double balance = extractDouble(json, "balance", 0.0);
            String expireTime = extractString(json, "expireTime");
            String port = extractString(json, "port");
            double rate = extractDouble(json, "rate", 1.0);
            boolean isEnable = extractBoolean(json, "isEnable", true);
            boolean enableWebHTML = extractBoolean(json, "enableWebHTML", false);

            return new SequenceKey(name, balance, expireTime, port, rate, isEnable, enableWebHTML);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractString(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private double extractDouble(String json, String key, double def) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.]+)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : def;
    }

    private boolean extractBoolean(String json, String key, boolean def) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)").matcher(json);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : def;
    }
}