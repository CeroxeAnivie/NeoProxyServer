package neoproxy.neoproxyserver.core.management;

import com.google.gson.Gson;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import neoproxy.neoproxyserver.core.ServerLogger;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.*;

public class IPGeolocationHelper {

    private static final Gson gson = new Gson();
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    // 芯&API 的免费接口地址（无需密钥）
    private static final String COREXWEAR_IP_API_URL = "http://api.corexwear.com/ip/index.php";

    // ip.sb API 地址（用于 IPv6 查询）
    private static final String IP_SB_API_URL = "https://api.ip.sb/geoip/";

    // API服务列表，包含 IPv4 和 IPv6 的服务
    private static final List<APIService> IPV4_API_SERVICES = List.of(
            new APIService("CorexIP", COREXWEAR_IP_API_URL, IPGeolocationHelper::parseCorexIpResponse, 1, null, 300)
    );

    private static final List<APIService> IPV6_API_SERVICES = List.of(
            new APIService("IPSb", IP_SB_API_URL, IPGeolocationHelper::parseIpSbResponse, 1, null, 2000)
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

        // 为Unirest设置默认全局超时（兼容Unirest 1.x）
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

        // 判断IP类型并选择相应的API服务
        boolean isIPv6 = isIPv6Address(ip);
        List<APIService> apiServices = isIPv6 ? IPV6_API_SERVICES : IPV4_API_SERVICES;
        ServerLogger.info(isIPv6 ? "ipGeolocation.ipv6Detected" : "ipGeolocation.ipv4Detected", ip);

        // --- 核心：竞速逻辑 ---
        CompletableFuture<LocationInfo> finalResult = new CompletableFuture<>();
        List<CompletableFuture<LocationInfo>> apiFutures = apiServices.stream()
                .map(api -> CompletableFuture.supplyAsync(() -> queryAPIService(api, ip), executor)
                        .exceptionally(e -> {
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            String errorMsg = cause.getMessage();

                            // 检查是否是超时异常
                            if (cause instanceof SocketTimeoutException ||
                                    (errorMsg != null && errorMsg.contains("timeout"))) {
                                ServerLogger.warn("ipGeolocation.apiTimeout", api.name, ip);
                            } else {
                                ServerLogger.error("ipGeolocation.apiErrorException", api.name, ip, errorMsg);
                            }
                            return null;
                        }))
                .toList();

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

    /**
     * 判断IP地址是否为IPv6
     */
    private static boolean isIPv6Address(String ip) {
        return ip.contains(":");
    }

    private static LocationInfo queryAPIService(APIService apiService, String ip) {
        try {
            // 为当前API服务设置特定的超时时间
            Unirest.setTimeouts(apiService.timeout, apiService.timeout);

            HttpResponse<String> response;

            // 根据API类型构建不同的请求
            if ("IPSb".equals(apiService.name)) {
                // IPv6 使用 ip.sb API，直接在URL中包含IP
                response = Unirest.get(apiService.baseUrl + ip)
                        .asString();
            } else {
                // IPv4 使用原来的API，通过查询参数传递IP
                response = Unirest.get(apiService.baseUrl)
                        .queryString("ip", ip)
                        .asString();
            }

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
            // 检查是否是超时异常
            if (e.getCause() instanceof SocketTimeoutException ||
                    (e.getMessage() != null && e.getMessage().contains("timeout"))) {
                // 抛出带有超时标记的异常，以便在上层处理
                throw new RuntimeException("API timeout", e);
            } else {
                // 抛出普通异常
                throw new RuntimeException(e);
            }
        } finally {
            // 恢复默认超时设置
            Unirest.setTimeouts(300, 300);
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
        int timeout; // 添加超时时间字段（毫秒）

        APIService(String name, String baseUrl, java.util.function.Function<String, LocationInfo> parser, int priority, String apiKey, int timeout) {
            this.name = name;
            this.baseUrl = baseUrl;
            this.parser = parser;
            this.priority = priority;
            this.apiKey = apiKey;
            this.timeout = timeout;
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

    // --- ip.sb API的响应解析器 ---
    private static LocationInfo parseIpSbResponse(String jsonResponse) {
        try {
            IpSbResponse response = gson.fromJson(jsonResponse, IpSbResponse.class);
            if (response != null) {
                String location = response.country != null ? response.country : "N/A";
                String isp = response.isp != null ? response.isp : "N/A";
                return new LocationInfo(location, isp, true);
            }
        } catch (Exception e) {
            ServerLogger.error("ipGeolocation.parseError", "IPSb", e.getMessage());
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

    // --- ip.sb API的响应模型 ---
    private static class IpSbResponse {
        String ip;
        String country;
        String country_code;
        String region;
        String region_code;
        String city;
        double latitude;
        double longitude;
        String isp;
        String asn;
        String organization;
    }
}