package neoproject.neoproxy.core.management;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static neoproject.neoproxy.NeoProxyServer.sayError;
import static neoproject.neoproxy.NeoProxyServer.sayInfo;

public class IPGeolocationHelper {

    private static final String IP_GEOLOCATION_API_KEY = "YOUR_API_KEY_HERE";

    // --- 核心修复点 1: 简化并放宽超时 ---
    // 移除了 connectTimeout，只保留 HttpRequest 的 timeout，并给予更宽松的时间
    private static final HttpClient httpClient = HttpClient.newBuilder()
            // .connectTimeout(...) // 移除连接超时，让请求超时统一控制
            .executor(Executors.newCachedThreadPool())
            .build();

    private static final Gson gson = new Gson();
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private static final List<APIService> API_SERVICES = List.of(
            new APIService("ip-api.com", "http://ip-api.com/json/", IPGeolocationHelper::parseIPAPIResponse, 1, null),
            new APIService("ipapi.co", "https://ipapi.co/", IPGeolocationHelper::parseIPAPICoResponse, 2, null),
            new APIService("ipapi.is", "https://ipapi.is/", IPGeolocationHelper::parseIPAPIIsResponse, 3, null),
            new APIService("ipgeolocation.io", "https://api.ipgeolocation.io/ipgeo", IPGeolocationHelper::parseIPGeolocationResponse, 4, IP_GEOLOCATION_API_KEY)
    );

    public static LocationInfo getLocationInfo(String ip) {
        List<CompletableFuture<LocationInfo>> futures = API_SERVICES.stream()
                .filter(api -> !("ipgeolocation.io".equals(api.name) && "YOUR_API_KEY_HERE".equals(api.apiKey)))
                .map(api -> CompletableFuture.supplyAsync(() -> queryAPIService(api, ip), executor)
                        .exceptionally(e -> {
                            // --- 核心修复点 2: 更清晰的错误日志 ---
                            Throwable cause = e.getCause();
                            String errorMsg = cause != null ? cause.getMessage() : e.getMessage();
                            sayError("IPGeolocationHelper: Error with " + api.name + ": " + errorMsg);
                            return null;
                        }))
                .collect(Collectors.toList());

        try {
            // --- 核心修复点 3: 放宽总超时时间 ---
            // 给所有请求一个更合理的总时间窗口（3秒）
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(3000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            sayError("IPGeolocationHelper: Overall timeout (3000ms) reached for IP " + ip + ".");
            futures.forEach(f -> f.cancel(true));
        } catch (InterruptedException | ExecutionException e) {
            sayError("IPGeolocationHelper: Error during parallel lookup: " + e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }

        // 收集所有成功的结果
        List<LocationInfo> results = futures.stream()
                .filter(f -> f.isDone() && !f.isCancelled() && f.getNow(null) != null)
                .map(CompletableFuture::join)
                .filter(LocationInfo::success)
                .collect(Collectors.toList());

        if (results.isEmpty()) {
            sayError("IPGeolocationHelper: All API services failed for IP " + ip);
            return LocationInfo.failed();
        }

        // 选择最详细的结果（优先级最高的）
        LocationInfo bestResult = results.get(0);
        for (LocationInfo result : results) {
            if (isMoreDetailed(result, bestResult)) {
                bestResult = result;
            }
        }

        sayInfo("IPGeolocationHelper: Got location from " + bestResult.source() + " for IP " + ip);
        return bestResult;
    }

    private static LocationInfo queryAPIService(APIService apiService, String ip) {
        try {
            String apiUrl = buildApiUrl(apiService, ip);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    // --- 核心修复点 4: 单个请求超时放宽 ---
                    // 给每个请求2.5秒的完成时间，这在绝大多数网络环境下都是足够的
                    .timeout(Duration.ofMillis(2500))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                LocationInfo result = apiService.parser.apply(response.body());
                if (result != null && result.success()) {
                    return new LocationInfo(result.location(), result.isp(), true, apiService.name);
                }
            }
        } catch (Exception e) {
            // 将异常包装后抛出，由外部的 exceptionally 捕获
            throw new RuntimeException(e);
        }
        return null;
    }

    private static String buildApiUrl(APIService apiService, String ip) {
        return switch (apiService.name) {
            case "ip-api.com" -> apiService.baseUrl + ip + "?fields=status,message,country,regionName,city,isp";
            case "ipapi.co" -> apiService.baseUrl + ip + "/json/";
            case "ipapi.is" -> apiService.baseUrl + ip + "?fields=location,org";
            case "ipgeolocation.io" -> apiService.baseUrl + "?apiKey=" + apiService.apiKey + "&ip=" + ip + "&fields=country_name,state_prov,city,isp";
            default -> apiService.baseUrl + ip;
        };
    }

    private static boolean isMoreDetailed(LocationInfo newResult, LocationInfo currentBest) {
        int newPriority = API_SERVICES.stream().filter(api -> api.name.equals(newResult.source())).map(api -> api.priority).findFirst().orElse(Integer.MAX_VALUE);
        int currentPriority = API_SERVICES.stream().filter(api -> api.name.equals(currentBest.source())).map(api -> api.priority).findFirst().orElse(Integer.MAX_VALUE);
        if (newPriority < currentPriority) return true;
        if (newPriority == currentPriority) {
            long newDetails = Arrays.stream(new String[]{newResult.location(), newResult.isp()}).filter(s -> s != null && !s.equals("N/A")).count();
            long currentDetails = Arrays.stream(new String[]{currentBest.location(), currentBest.isp()}).filter(s -> s != null && !s.equals("N/A")).count();
            return newDetails > currentDetails;
        }
        return false;
    }

    // --- 解析器 (与之前版本完全相同) ---
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
        } catch (Exception e) { sayError("IPGeolocationHelper: Failed to parse IP-API response: " + e.getMessage()); }
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
        } catch (Exception e) { sayError("IPGeolocationHelper: Failed to parse IPAPI.co response: " + e.getMessage()); }
        return LocationInfo.failed();
    }
    private static LocationInfo parseIPAPIIsResponse(String jsonResponse) {
        try {
            IPAPIIsResponse response = gson.fromJson(jsonResponse, IPAPIIsResponse.class);
            if (response != null && response.location != null) {
                String city = response.location.city != null ? response.location.city : "";
                String region = response.location.region != null ? response.location.region : "";
                String country = response.location.country != null ? response.location.country : "";
                String isp = response.org != null ? response.org : "N/A";
                StringBuilder locBuilder = new StringBuilder();
                if (!city.isEmpty()) locBuilder.append(city).append(", ");
                if (!region.isEmpty()) locBuilder.append(region).append(", ");
                if (!country.isEmpty()) locBuilder.append(country);
                String location = locBuilder.length() > 2 ? locBuilder.substring(0, locBuilder.length() - 2) : "N/A";
                return new LocationInfo(location, isp, true);
            }
        } catch (Exception e) { sayError("IPGeolocationHelper: Failed to parse IPAPI.is response: " + e.getMessage()); }
        return LocationInfo.failed();
    }
    private static LocationInfo parseIPGeolocationResponse(String jsonResponse) {
        try {
            IPGeolocationResponse response = gson.fromJson(jsonResponse, IPGeolocationResponse.class);
            if (response != null) {
                String city = response.city != null ? response.city : "";
                String region = response.state_prov != null ? response.state_prov : "";
                String country = response.country_name != null ? response.country_name : "";
                String isp = response.isp != null ? response.isp : "N/A";
                StringBuilder locBuilder = new StringBuilder();
                if (!city.isEmpty()) locBuilder.append(city).append(", ");
                if (!region.isEmpty()) locBuilder.append(region).append(", ");
                if (!country.isEmpty()) locBuilder.append(country);
                String location = locBuilder.length() > 2 ? locBuilder.substring(0, locBuilder.length() - 2) : "N/A";
                return new LocationInfo(location, isp, true);
            }
        } catch (Exception e) { sayError("IPGeolocationHelper: Failed to parse IPGeolocation.io response: " + e.getMessage()); }
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
        public LocationInfo(String location, String isp, boolean success) { this(location, isp, success, "Unknown"); }
        public static LocationInfo failed() { return new LocationInfo("N/A", "N/A", false, "Failed"); }
    }

    private static class APIService {
        String name; String baseUrl; java.util.function.Function<String, LocationInfo> parser; int priority; String apiKey;
        APIService(String name, String baseUrl, java.util.function.Function<String, LocationInfo> parser, int priority, String apiKey) {
            this.name = name; this.baseUrl = baseUrl; this.parser = parser; this.priority = priority; this.apiKey = apiKey;
        }
    }

    // --- 响应模型 (与之前版本完全相同) ---
    private static class IPAPIResponse { String status; String message; String country; @SerializedName("regionName") String regionName; String city; String isp; }
    private static class IPAPICoResponse { String city; String region; @SerializedName("country_name") String country_name; String org; Boolean error; String reason; }
    private static class IPAPIIsResponse { Location location; String org; }
    private static class Location { String city; String region; String country; }
    private static class IPGeolocationResponse { String city; @SerializedName("state_prov") String state_prov; @SerializedName("country_name") String country_name; String isp; }
}