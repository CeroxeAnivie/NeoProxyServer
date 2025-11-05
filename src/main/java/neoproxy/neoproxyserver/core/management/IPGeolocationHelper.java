package neoproxy.neoproxyserver.core.management;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import neoproxy.neoproxyserver.core.ServerLogger;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static neoproxy.neoproxyserver.NeoProxyServer.IS_DEBUG_MODE;

public class IPGeolocationHelper {

    // --- 核心：创建两个客户端，一个使用系统代理，一个直接连接 ---
    private static final HttpClient httpClientWithProxy;
    private static final HttpClient httpClientDirect;
    private static final boolean USE_SYSTEM_PROXY;
    private static final Gson gson = new Gson();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    // 核心：API服务列表，只保留您指定的三个
    private static final List<APIService> API_SERVICES = List.of(
            // 优先级 1: ip.sb
            new APIService("ip.sb", "https://api.ip.sb/geoip", IPGeolocationHelper::parseIpSbResponse, 1, null),
            // 优先级 2: ip-api.com
            new APIService("ip-api.com", "http://ip-api.com/json/", IPGeolocationHelper::parseIPAPIResponse, 2, null),
            // 优先级 3: ipapi.co
            new APIService("ipapi.co", "https://ipapi.co/", IPGeolocationHelper::parseIPAPICoResponse, 3, null)
    );

    static {
        // 检测系统代理是否配置（使用增强版逻辑）
        USE_SYSTEM_PROXY = isSystemProxyConfigured();
        ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.systemProxyDetected",
                USE_SYSTEM_PROXY,
                ServerLogger.getMessage(USE_SYSTEM_PROXY ? "ipGeolocationHelper.systemProxyWillUse" : "ipGeolocationHelper.systemProxyWillNotUse")
        );

        // 创建一个使用系统默认代理选择器的客户端
        httpClientWithProxy = HttpClient.newBuilder()
                .executor(Executors.newCachedThreadPool())
                .build(); // 默认情况下，HttpClient会使用 ProxySelector.getDefault()

        // 创建一个强制绕过所有代理的客户端
        httpClientDirect = HttpClient.newBuilder()
                .proxy(ProxySelector.of(null)) // ProxySelector.of(null) 是一个特殊值，表示不使用任何代理
                .executor(Executors.newCachedThreadPool())
                .build();
    }

    public static LocationInfo getLocationInfo(String ip) {
        // 预检查私有IP地址
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.privateIpSkipped", ip);
                return new LocationInfo("Localhost", "N/A", true, "Local Detection");
            }
        } catch (UnknownHostException e) {
            ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.invalidIpFormat", ip);
            return LocationInfo.failed();
        }

        // --- 核心：竞速逻辑 ---
        CompletableFuture<LocationInfo> finalResult = new CompletableFuture<>();
        List<CompletableFuture<LocationInfo>> apiFutures = API_SERVICES.stream()
                .map(api -> CompletableFuture.supplyAsync(() -> queryAPIService(api, ip), executor)
                        .exceptionally(e -> {
                            String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                            ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.apiError", api.name, errorMsg);
                            return null;
                        }))
                .collect(Collectors.toList());

        for (CompletableFuture<LocationInfo> apiFuture : apiFutures) {
            apiFuture.thenAccept(result -> {
                if (result != null && result.success() && !finalResult.isDone()) {
                    ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.locationFound", result.source(), ip);
                    finalResult.complete(result);
                    apiFutures.forEach(f -> {
                        if (f != apiFuture) {
                            f.cancel(true);
                        }
                    });
                }
            });
        }

        CompletableFuture.allOf(apiFutures.toArray(new CompletableFuture[0])).thenRun(() -> {
            if (!finalResult.isDone()) {
                ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.allApisFailed", ip);
                finalResult.complete(LocationInfo.failed());
            }
        });

        try {
            return finalResult.get(3000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.overallTimeout", ip);
            apiFutures.forEach(f -> f.cancel(true));
            return LocationInfo.failed();
        } catch (InterruptedException | ExecutionException e) {
            ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.parallelLookupError", e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return LocationInfo.failed();
        }
    }

    private static LocationInfo queryAPIService(APIService apiService, String ip) {
        try {
            String apiUrl = buildApiUrl(apiService, ip);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofMillis(1500)) // 单个请求超时1.5秒
                    .GET()
                    .build();

            // --- 核心：根据检测结果选择使用哪个客户端 ---
            HttpClient clientToUse = USE_SYSTEM_PROXY ? httpClientWithProxy : httpClientDirect;
            HttpResponse<String> response = clientToUse.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                LocationInfo result = apiService.parser.apply(response.body());
                if (result != null && result.success()) {
                    return new LocationInfo(result.location(), result.isp(), true, apiService.name);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    // --- 核心：增强版代理检测逻辑（增加了调试输出） ---
    private static boolean isSystemProxyConfigured() {
        // 1. 首先尝试标准的 ProxySelector
        try {
            ProxySelector defaultSelector = ProxySelector.getDefault();
            if (defaultSelector != null) {
                URI testUri = new URI("https://api.ip.sb/geoip");
                List<Proxy> proxyList = defaultSelector.select(testUri);
                if (proxyList != null && !proxyList.isEmpty() && proxyList.get(0) != Proxy.NO_PROXY) {
                    ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.systemProxyDetected", true, ServerLogger.getMessage("ipGeolocationHelper.systemProxyWillUse"));
                    return true;
                }
            }
        } catch (Exception e) {
            ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.proxySelectorCheckFailed", e.getMessage());
        }

        // 2. 如果标准方法失败，直接检查环境变量
        ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.checkingEnvVars");
        String httpProxy = System.getenv("http_proxy");
        String httpsProxy = System.getenv("https_proxy");

        // --- 核心：增加调试输出 ---
        if (IS_DEBUG_MODE) {
            ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.debugHttpProxy", httpProxy);
            ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.debugHttpsProxy", httpsProxy);
        }

        if (httpProxy != null && !httpProxy.isEmpty()) {
            ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.httpProxyFound", httpProxy);
            return true;
        }
        if (httpsProxy != null && !httpsProxy.isEmpty()) {
            ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.httpsProxyFound", httpsProxy);
            return true;
        }

        ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.noProxyFound");
        return false;
    }

    // --- 核心：URL构建逻辑，只包含三个API ---
    private static String buildApiUrl(APIService apiService, String ip) {
        return switch (apiService.name) {
            case "ip.sb" -> apiService.baseUrl + "/" + ip;
            case "ip-api.com" -> apiService.baseUrl + ip + "?fields=status,message,country,regionName,city,isp";
            case "ipapi.co" -> apiService.baseUrl + ip + "/json/";
            default -> apiService.baseUrl + ip;
        };
    }

    // --- 核心：三个API的解析器 ---
    private static LocationInfo parseIpSbResponse(String jsonResponse) {
        try {
            IpSbResponse response = gson.fromJson(jsonResponse, IpSbResponse.class);
            if (response != null && response.country != null) {
                String city = response.city != null ? response.city : "";
                String region = response.region != null ? response.region : "";
                String country = response.country != null ? response.country : "";
                String isp = response.isp != null ? response.isp : "N/A";

                StringBuilder locBuilder = new StringBuilder();
                if (!city.isEmpty()) locBuilder.append(city).append(", ");
                if (!region.isEmpty()) locBuilder.append(region).append(", ");
                if (!country.isEmpty()) locBuilder.append(country);
                String location = locBuilder.length() > 2 ? locBuilder.substring(0, locBuilder.length() - 2) : "N/A";

                return new LocationInfo(location, isp, true);
            }
        } catch (Exception e) {
            ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.parseError.ipSb", e.getMessage());
        }
        return LocationInfo.failed();
    }

    private static LocationInfo parseIPAPIResponse(String jsonResponse) {
        try {
            IPAPIResponse response = gson.fromJson(jsonResponse, IPAPIResponse.class);
            if (response != null && "success".equals(response.status)) {
                String city = response.city != null ? response.city : "";
                String region = response.regionName != null ? response.regionName : "";
                String country = response.country != null ? response.country : "";
                String isp = response.isp != null ? response.isp : "N/A";
                StringBuilder locBuilder = new StringBuilder();
                if (!city.isEmpty()) locBuilder.append(city).append(", ");
                if (!region.isEmpty()) locBuilder.append(region).append(", ");
                if (!country.isEmpty()) locBuilder.append(country);
                String location = locBuilder.length() > 2 ? locBuilder.substring(0, locBuilder.length() - 2) : "N/A";
                return new LocationInfo(location, isp, true);
            }
        } catch (Exception e) {
            ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.parseError.ipApi", e.getMessage());
        }
        return LocationInfo.failed();
    }

    private static LocationInfo parseIPAPICoResponse(String jsonResponse) {
        try {
            IPAPICoResponse response = gson.fromJson(jsonResponse, IPAPICoResponse.class);
            if (response != null && !Boolean.TRUE.equals(response.error)) {
                String city = response.city != null ? response.city : "";
                String region = response.region != null ? response.region : "";
                String country = response.country_name != null ? response.country_name : "";
                String isp = response.org != null ? response.org : "N/A";
                StringBuilder locBuilder = new StringBuilder();
                if (!city.isEmpty()) locBuilder.append(city).append(", ");
                if (!region.isEmpty()) locBuilder.append(region).append(", ");
                if (!country.isEmpty()) locBuilder.append(country);
                String location = locBuilder.length() > 2 ? locBuilder.substring(0, locBuilder.length() - 2) : "N/A";
                return new LocationInfo(location, isp, true);
            }
        } catch (Exception e) {
            ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.parseError.ipApiCo", e.getMessage());
        }
        return LocationInfo.failed();
    }

    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
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

    // --- 核心：三个API的响应模型 ---
    private static class IpSbResponse {
        String country;
        String region;
        String city;
        String isp;
    }

    private static class IPAPIResponse {
        String status;
        String message;
        String country;
        @SerializedName("regionName")
        String regionName;
        String city;
        String isp;
    }

    private static class IPAPICoResponse {
        String city;
        String region;
        @SerializedName("country_name")
        String country_name;
        String org;
        Boolean error;
        String reason;
    }
}