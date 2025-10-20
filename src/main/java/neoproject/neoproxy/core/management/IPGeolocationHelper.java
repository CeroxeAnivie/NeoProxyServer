package neoproject.neoproxy.core.management;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.*;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import static neoproject.neoproxy.NeoProxyServer.sayError;
import static neoproject.neoproxy.NeoProxyServer.sayInfo;

public class IPGeolocationHelper {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)) // 连接超时
            .build();
    private static final Gson gson = new Gson();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    public static class LocationInfo {
        public final String location; // e.g., "Beijing, Beijing, China"
        public final String isp;      // e.g., "China Telecom"
        public final boolean success; // Indicate if the lookup was successful

        public LocationInfo(String location, String isp, boolean success) {
            this.location = location;
            this.isp = isp;
            this.success = success;
        }

        // Convenience constructor for success case
        public LocationInfo(String location, String isp) {
            this(location, isp, true);
        }

        // Convenience constructor for failure case
        public static LocationInfo failed() {
            return new LocationInfo("N/A", "N/A", false);
        }
    }

    public static LocationInfo getLocationInfo(String ip) {
        // Submit the task to a CompletableFuture
        CompletableFuture<LocationInfo> future = CompletableFuture.supplyAsync(() -> {
            String queryResult = queryIPAPI(ip);
            if (queryResult != null) {
                IPAPIResponse response = parseIPAPIResponse(queryResult);
                if (response != null && response.success) {
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
                } else if (response != null && "private range".equals(response.message)) {
                    sayInfo("IPGeolocationHelper: IP " + ip + " is a private IP address.");
                    return new LocationInfo("Private IP", "N/A", false);
                } else if (response != null && "reserved range".equals(response.message)) {
                    sayInfo("IPGeolocationHelper: IP " + ip + " is in a reserved range (e.g., loopback, multicast).");
                    return new LocationInfo("Reserved IP Range", "N/A", false);
                }
                sayError("IPGeolocationHelper: Unexpected response or error for IP " + ip + ".");
            }
            return LocationInfo.failed(); // Return a failed instance
        }, scheduler); // Use the scheduler for async execution

        try {
            // Get the result with a timeout of 1000 milliseconds
            return future.get(1000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            sayError("IPGeolocationHelper: Timeout (1000ms) occurred while fetching location for IP " + ip + ".");
            // Cancel the underlying task if it's still running
            future.cancel(true);
            // Optionally, log a warning using your application's logging mechanism
            // e.g., NeoProxyServer.sayError("IP Geolocation timed out for IP: " + ip);
            return LocationInfo.failed(); // Return N/A on timeout
        } catch (InterruptedException | ExecutionException e) {
            sayError("IPGeolocationHelper: An error occurred during IP lookup for " + ip + ": " + e.getMessage());
            // Restore interrupted state
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return LocationInfo.failed(); // Return N/A on error
        }
    }

    private static String queryIPAPI(String ip) {
        String apiUrl = "http://ip-api.com/json/" + ip + "?fields=status,message,country,regionName,city,isp";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofMillis(900)) // 设置请求总超时，略小于 1000ms，为 Future.get 留出缓冲
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                sayError("IPGeolocationHelper: IP-API request failed with status code: " + response.statusCode());
            }
        } catch (Exception e) {
            // HttpRequest.Builder.timeout can throw java.net.http.HttpTimeoutException
            // HttpClient.send can throw IOException or InterruptedException
            // We let the calling method (CompletableFuture) handle these exceptions
            sayError("IPGeolocationHelper: Exception during IP-API request for " + ip + ": " + e.getMessage());
            // Don't print stack trace here unless needed for debugging, as it might be frequent
            // e.printStackTrace();
        }
        return null;
    }

    private static IPAPIResponse parseIPAPIResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            sayError("IPGeolocationHelper: Received empty or null JSON response.");
            return null;
        }
        try {
            IPAPIResponse response = gson.fromJson(jsonResponse, IPAPIResponse.class);
            if (response != null) {
                if ("success".equals(response.status)) {
                    response.success = true;
                    return response;
                } else if (response.message != null) {
                    sayInfo("IPGeolocationHelper: IP-API lookup failed: " + response.message);
                    response.success = false;
                    return response;
                } else {
                    sayError("IPGeolocationHelper: IP-API response status is not 'success' and no message provided.");
                    response.success = false;
                    return response;
                }
            } else {
                sayError("IPGeolocationHelper: Failed to parse JSON into IPAPIResponse object.");
            }
        } catch (Exception e) {
            sayError("IPGeolocationHelper: Failed to parse IP-API response: " + e.getMessage());
        }
        return null;
    }

    private static class IPAPIResponse {
        String status;
        String message;
        String country;
        @SerializedName("regionName")
        String regionName;
        String city;
        String isp;
        transient boolean success = false;
    }

    // Shutdown the scheduler when the application shuts down (optional, if you have a shutdown hook)
    public static void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}