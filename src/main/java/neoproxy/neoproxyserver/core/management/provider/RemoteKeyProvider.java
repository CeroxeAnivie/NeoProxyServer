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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteKeyProvider implements KeyDataProvider {
    private static final double SYNC_THRESHOLD_MB = 50.0;
    private static final int SYNC_INTERVAL_SECONDS = 60;

    private static final int REQUEST_TIMEOUT_MS = 2000;
    private static final int MAX_RETRIES = 2;

    private final String managerUrl;
    private final String token;
    private final String nodeId;
    private final LocalKeyProvider localFallback;
    private final HttpClient httpClient;

    private final ConcurrentHashMap<String, DoubleAdder> trafficBuffer = new ConcurrentHashMap<>();
    private final AtomicBoolean isFlushing = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "RemoteKeyProvider-Sync-Thread");
        t.setDaemon(true);
        return t;
    });

    public RemoteKeyProvider(String url, String token, String nodeId) {
        this.managerUrl = url;
        this.token = token;
        this.nodeId = nodeId;
        this.localFallback = new LocalKeyProvider();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    @Override
    public void init() {
        localFallback.init();
        scheduler.scheduleAtFixedRate(this::tryTriggerFlush, SYNC_INTERVAL_SECONDS, SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
        // 【修复】使用 Key
        ServerLogger.info("remoteProvider.initInfo", REQUEST_TIMEOUT_MS, MAX_RETRIES);
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) throws Exception {
        Exception lastException = null;
        for (int i = 0; i <= MAX_RETRIES; i++) {
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                lastException = e;
                if (i < MAX_RETRIES) {
                    // 【修复】使用 Key
                    ServerLogger.warn("remoteProvider.connRetry", i + 1, MAX_RETRIES + 1, e.getMessage());
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
        throw lastException;
    }

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
                // 【修复】使用 Key
                ServerLogger.warn("remoteProvider.reject409", name);
                PortOccupiedException.throwException("Rejected by Manager");
            } else if (response.statusCode() == 403) {
                // 【修复】使用 Key
                ServerLogger.warn("remoteProvider.disable403", name);
                return null;
            }
        } catch (PortOccupiedException e) {
            throw e;
        } catch (Exception e) {
            // 【修复】使用 Key
            ServerLogger.warn("remoteProvider.getKeyError", e.getMessage());
        }
        return null;
    }

    @Override
    public void releaseKey(String name) {
        ThreadManager.runAsync(() -> {
            try {
                String endpoint = String.format("%s/api/release?name=%s&nodeId=%s", managerUrl, name, nodeId);
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS));
                if (token != null && !token.isEmpty()) reqBuilder.header("Authorization", "Bearer " + token);

                sendWithRetry(reqBuilder.build());
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void consumeFlow(String name, double mib) {
        trafficBuffer.computeIfAbsent(name, k -> new DoubleAdder()).add(mib);

        if (mib > 1.0 || ThreadLocalRandom.current().nextInt(100) == 0) {
            DoubleAdder adder = trafficBuffer.get(name);
            if (adder != null && adder.sum() >= SYNC_THRESHOLD_MB) {
                tryTriggerFlush();
            }
        }
    }

    private void tryTriggerFlush() {
        if (trafficBuffer.isEmpty()) return;
        if (isFlushing.compareAndSet(false, true)) {
            Thread.ofVirtual().start(this::flushTraffic);
        }
    }

    private void flushTraffic() {
        ConcurrentHashMap<String, Double> snapshot = new ConcurrentHashMap<>();

        try {
            trafficBuffer.forEach((k, adder) -> {
                double val = adder.sumThenReset();
                if (val > 0.0001) {
                    snapshot.put(k, val);
                }
            });

            if (snapshot.isEmpty()) return;

            String jsonBody = buildSyncJson(snapshot);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(managerUrl + "/api/sync"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS));

            if (token != null && !token.isEmpty()) reqBuilder.header("Authorization", "Bearer " + token);

            HttpResponse<String> response = sendWithRetry(reqBuilder.build());

            if (response.statusCode() == 200) {
                String resBody = response.body();
                if (resBody.contains("kill_keys")) {
                    processKillKeys(resBody);
                }
            } else {
                // 【修复】使用 Key
                ServerLogger.warn("remoteProvider.syncStatusError", response.statusCode());
                snapshot.forEach((k, v) -> trafficBuffer.computeIfAbsent(k, x -> new DoubleAdder()).add(v));
            }
        } catch (Exception e) {
            // 【修复】使用 Key
            ServerLogger.error("remoteProvider.syncException", e);
            snapshot.forEach((k, v) -> trafficBuffer.computeIfAbsent(k, x -> new DoubleAdder()).add(v));
        } finally {
            isFlushing.set(false);
        }
    }

    private void processKillKeys(String json) {
        try {
            int start = json.indexOf("[");
            int end = json.lastIndexOf("]");
            if (start == -1 || end == -1) return;

            String listContent = json.substring(start + 1, end);
            if (listContent.isBlank()) return;

            String[] keys = listContent.split(",");
            for (String k : keys) {
                String rawKey = k.trim().replace("\"", "");
                if (!rawKey.isEmpty()) {
                    kickClient(rawKey);
                }
            }
        } catch (Exception e) {
            // 【修复】使用 Key
            ServerLogger.error("remoteProvider.killKeyError", e);
        }
    }

    private void kickClient(String keyName) {
        for (HostClient client : NeoProxyServer.availableHostClient) {
            if (client.getKey() != null && client.getKey().getName().equals(keyName)) {
                // 【修复】使用 Key
                ServerLogger.warn("remoteProvider.killClientAction", keyName);
                client.close();
            }
        }
    }

    @Override
    public void shutdown() {
        scheduler.shutdownNow();
        if (isFlushing.compareAndSet(false, true)) {
            flushTraffic();
        }
    }

    private String buildSyncJson(Map<String, Double> traffic) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"nodeId\":\"").append(nodeId).append("\",");
        sb.append("\"traffic\":{");
        int count = 0;
        for (Map.Entry<String, Double> entry : traffic.entrySet()) {
            sb.append("\"").append(entry.getKey()).append("\":")
                    .append(String.format("%.4f", entry.getValue()));
            if (++count < traffic.size()) sb.append(",");
        }
        sb.append("}");
        sb.append("}");
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
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        return null;
    }

    private double extractDouble(String json, String key, double defaultVal) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.]+)");
        Matcher m = p.matcher(json);
        if (m.find()) try {
            return Double.parseDouble(m.group(1));
        } catch (Exception ignored) {
        }
        return defaultVal;
    }

    private boolean extractBoolean(String json, String key, boolean defaultVal) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(json);
        if (m.find()) return Boolean.parseBoolean(m.group(1));
        return defaultVal;
    }
}