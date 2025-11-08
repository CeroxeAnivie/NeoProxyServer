package neoproxy.neoproxyserver.core.management;

import com.google.gson.Gson;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import neoproxy.neoproxyserver.core.ServerLogger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class IPGeolocationHelper {

    private static final Gson gson = new Gson();
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    // 芯&API 的免费接口地址（无需密钥）
    private static final String COREX_IP_API_URL = "http://api.corexwear.com/ip/index.php";

    // 核心：API服务列表，这里简化为只使用芯&API的IP归属地接口
    private static final List<APIService> API_SERVICES = List.of(
            new APIService("CorexIP", COREX_IP_API_URL, IPGeolocationHelper::parseCorexIpResponse, 1, null)
    );

    static {
        // 初始化Unirest
        Unirest.setObjectMapper(new com.mashape.unirest.http.ObjectMapper() {
            private final Gson gson = new Gson();
            public <T> T readValue(String value, Class<T> valueType) {
                return gson.fromJson(value, valueType);
            }
            public String writeValue(Object value) {
                return gson.toJson(value);
            }
        });

        // 为Unirest设置全局超时（兼容Unirest 1.x）
        Unirest.setTimeouts(300, 300);
    }

    public static LocationInfo getLocationInfo(String ip) {
        // 预检查私有IP地址
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                ServerLogger.info("ipGeolocation.privateIpSkipped", ip);
                return new LocationInfo("Localhost", "N/A", true, "Local Detection");
            }
        } catch (UnknownHostException e) {
            ServerLogger.error("ipGeolocation.invalidIpFormat", ip);
            return LocationInfo.failed();
        }

        ServerLogger.info("ipGeolocation.querying", ip);

        // --- 核心：竞速逻辑 ---
        CompletableFuture<LocationInfo> finalResult = new CompletableFuture<>();
        List<CompletableFuture<LocationInfo>> apiFutures = API_SERVICES.stream()
                .map(api -> CompletableFuture.supplyAsync(() -> queryAPIService(api, ip), executor)
                        .exceptionally(e -> {
                            String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                            ServerLogger.error("ipGeolocation.apiErrorException", api.name, ip, errorMsg);
                            return null;
                        }))
                .collect(Collectors.toList());

        for (CompletableFuture<LocationInfo> apiFuture : apiFutures) {
            apiFuture.thenAccept(result -> {
                if (result != null && result.success() && !finalResult.isDone()) {
                    ServerLogger.info("ipGeolocation.apiSuccess", ip, result.source(), result.location(), result.isp());
                    ServerLogger.info("ipGeolocation.finalSuccess", ip);
                    finalResult.complete(result);
                    apiFutures.forEach(f -> {
                        if (f != apiFuture) {
                            f.cancel(true);
                        }
                    });
                }
            });
        }

        try {
            return finalResult.get(2000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            ServerLogger.warn("ipGeolocation.finalTimeout", ip);
            apiFutures.forEach(f -> f.cancel(true));
            return LocationInfo.failed();
        } catch (InterruptedException | ExecutionException e) {
            ServerLogger.error("ipGeolocation.finalFailure", ip, e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return LocationInfo.failed();
        }
    }

    private static LocationInfo queryAPIService(APIService apiService, String ip) {
        try {
            HttpResponse<String> response = Unirest.get(apiService.baseUrl)
                    .queryString("ip", ip)
                    .asString();

            if (response.getStatus() == 200) {
                LocationInfo result = apiService.parser.apply(response.getBody());
                if (result != null && result.success()) {
                    return new LocationInfo(result.location(), result.isp(), true, apiService.name);
                }
                return result;
            } else {
                ServerLogger.warn("ipGeolocation.apiErrorStatus", apiService.name, response.getStatus(), ip);
            }
        } catch (UnirestException e) {
            // Exception is handled by the exceptionally block in getLocationInfo
            throw new RuntimeException(e);
        }
        return null;
    }

    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            Unirest.shutdown();
        } catch (Exception ignored) {
        }
    }

    public record LocationInfo(String location, String isp, boolean success, String source) {
        public LocationInfo(String location, String isp, boolean success) {
            this(location, isp, success, "Unknown");
        }

        public static LocationInfo failed() {
            return new LocationInfo("N/A", "N/A", false, "Failed");
        }
    }

    private static class APIService {
        String name;
        String baseUrl;
        java.util.function.Function<String, LocationInfo> parser;
        int priority;
        String apiKey;

        APIService(String name, String baseUrl, java.util.function.Function<String, LocationInfo> parser, int priority, String apiKey) {
            this.name = name;
            this.baseUrl = baseUrl;
            this.parser = parser;
            this.priority = priority;
            this.apiKey = apiKey;
        }
    }

    // --- 芯&API的响应解析器 ---
    private static LocationInfo parseCorexIpResponse(String jsonResponse) {
        try {
            CorexIpResponse response = gson.fromJson(jsonResponse, CorexIpResponse.class);
            if (response != null && response.code == 200 && response.data != null) {
                String location = response.data.country != null ? response.data.country : "N/A";
                String isp = response.data.area != null ? response.data.area : "N/A";
                return new LocationInfo(location, isp, true);
            }
        } catch (Exception e) {
            ServerLogger.error("ipGeolocation.parseError", "CorexIP", e.getMessage());
        }
        return LocationInfo.failed();
    }

    // --- 芯&API的响应模型 ---
    private static class CorexIpResponse {
        int code;
        CorexIpData data;
        String message;
    }

    private static class CorexIpData {
        String code;
        String ip;
        String beginip;
        String endip;
        String country;
        String area;
    }
}