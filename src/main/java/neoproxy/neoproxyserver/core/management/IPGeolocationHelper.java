package neoproxy.neoproxyserver.core.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import neoproxy.neoproxyserver.core.ServerLogger;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * IP 地理位置查询助手 - 适配 ServerLogger (MessageFormat)
 * 集成 HttpClient 连接池，默认超时 1000ms
 */
public class IPGeolocationHelper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ZXinc API 地址模板
    private static final String API_URL_TEMPLATE = "https://ip.zxinc.org/api.php?type=json&ip=%s";

    // 性能关键：全局复用 HttpClient 以启用连接池 (Keep-Alive)
    // 强制 HTTP/1.1 以获得更好的兼容性和握手速度
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(1000)) // 连接超时 1s
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // 读取超时设置 (1000ms)
    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(1000);

    /**
     * 获取 IP 地理位置信息
     *
     * @param ip 目标 IP (支持 IPv4 和 IPv6)
     * @return LocationInfo 对象
     */
    public static LocationInfo getLocationInfo(String ip) {
        // 1. 预检查私有 IP 地址
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                // 对应 properties: {0}
                ServerLogger.info("ipGeolocation.privateIpSkipped", ip);
                return new LocationInfo("Localhost", "N/A", true, "Local Detection");
            }
        } catch (UnknownHostException e) {
            // 对应 properties: {0}
            ServerLogger.error("ipGeolocation.invalidIpFormat", ip);
            return LocationInfo.failed();
        }

        // 对应 properties: {0}
        ServerLogger.info("ipGeolocation.querying", ip);

        // 2. 执行查询
        return queryZxincApi(ip);
    }

    /**
     * 使用 HttpClient 请求 ZXinc API
     */
    private static LocationInfo queryZxincApi(String ip) {
        try {
            // 构建请求 (伪装成 Chrome 浏览器)
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(API_URL_TEMPLATE, ip)))
                    .timeout(REQUEST_TIMEOUT) // 读取超时 1s
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .GET()
                    .build();

            // 发送请求 (利用连接池)
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                LocationInfo info = parseZxincResponse(response.body());
                if (info.success()) {
                    // 对应 properties: {0}=IP, {1}=Location, {2}=ISP
                    ServerLogger.info("ipGeolocation.success", ip, info.location(), info.isp());
                    return info;
                }
            } else {
                // 对应 properties: {0}=Provider, {1}=StatusCode, {2}=IP
                ServerLogger.warn("ipGeolocation.apiErrorStatus", "ZXinc", response.statusCode(), ip);
            }

        } catch (java.net.http.HttpTimeoutException e) {
            // 对应 properties: {0}=Provider, {1}=IP
            ServerLogger.warn("ipGeolocation.timeout", "ZXinc", ip);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 对应 properties: {0}=IP, {1}=ErrorMsg
            ServerLogger.error("ipGeolocation.requestError", ip, e.getMessage());
        }

        return LocationInfo.failed();
    }

    /**
     * 解析 ZXinc JSON 响应
     */
    private static LocationInfo parseZxincResponse(String jsonResult) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResult);
            int code = rootNode.path("code").asInt(-1);

            if (code == 0) {
                JsonNode dataNode = rootNode.path("data");

                // 提取地理位置 (country 字段: "中国–广东–广州")
                String locationStr = dataNode.path("country").asText("");

                // 兜底：如果 country 为空，尝试取 location 字段
                if (locationStr.isEmpty()) {
                    locationStr = dataNode.path("location").asText("Unknown");
                }

                // 提取 ISP (local 字段: "电信")
                String ispStr = dataNode.path("local").asText("Unknown");

                return new LocationInfo(locationStr, ispStr, true, "ZXinc");
            } else {
                // 对应 properties: {0}=ErrorCode
                ServerLogger.warn("ipGeolocation.apiLogicError", "ZXinc returned code: " + code);
            }
        } catch (Exception e) {
            // 对应 properties: {0}=Provider, {1}=ErrorMsg
            ServerLogger.error("ipGeolocation.parseError", "ZXinc", e.getMessage());
        }
        return LocationInfo.failed();
    }

    public static void shutdown() {
        // HttpClient 资源由 JVM 自动管理
    }

    public record LocationInfo(String location, String isp, boolean success, String source) {
        public LocationInfo(String location, String isp, boolean success) {
            this(location, isp, success, "Unknown");
        }

        public static LocationInfo failed() {
            return new LocationInfo("N/A", "N/A", false, "Failed");
        }
    }
}