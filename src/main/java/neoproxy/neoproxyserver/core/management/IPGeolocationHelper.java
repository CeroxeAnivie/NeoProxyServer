package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.core.ServerLogger;
import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.stream.Collectors;

public class IPGeolocationHelper {

    // 定义文件名常量
    private static final String FILE_V4 = "ip2region_v4.xdb";
    private static final String FILE_V6 = "ip2region_v6.xdb";
    private static Searcher searcherV4;
    private static Searcher searcherV6;
    private static boolean v4Loaded = false;
    private static boolean v6Loaded = false;

    static {
        initialize();
    }

    private static void initialize() {
        // -------------------------------------------------
        // 1. 加载 IPv4
        // -------------------------------------------------
        byte[] v4Data = loadFileToBytes(FILE_V4);
        if (v4Data != null) {
            try {
                // v3.1.1 API: Version.IPv4 + LongByteArray
                searcherV4 = Searcher.newWithBuffer(Version.IPv4, new LongByteArray(v4Data));
                v4Loaded = true;
                // [Key Used]: ipGeolocationHelper.init.v4.success
                ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v4.success", String.valueOf(v4Data.length));
            } catch (IOException e) {
                // [Key Used]: ipGeolocationHelper.init.v4.failed
                ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v4.failed", e.getMessage());
            }
        } else {
            // [Key Used]: ipGeolocationHelper.init.v4.notFound
            ServerLogger.warnWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v4.notFound", null);
        }

        // -------------------------------------------------
        // 2. 加载 IPv6
        // -------------------------------------------------
        byte[] v6Data = loadFileToBytes(FILE_V6);
        if (v6Data != null) {
            try {
                // v3.1.1 API: Version.IPv6 + LongByteArray
                searcherV6 = Searcher.newWithBuffer(Version.IPv6, new LongByteArray(v6Data));
                v6Loaded = true;
                // [Key Used]: ipGeolocationHelper.init.v6.success
                ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v6.success", String.valueOf(v6Data.length));
            } catch (IOException e) {
                // [Key Used]: ipGeolocationHelper.init.v6.failed
                ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v6.failed", e.getMessage());
            }
        } else {
            // [Key Used]: ipGeolocationHelper.init.v6.notFound
            ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v6.notFound", null);
        }
    }

    /**
     * 读取 classpath 下的文件到 byte[]
     */
    private static byte[] loadFileToBytes(String fileName) {
        try (InputStream inputStream = IPGeolocationHelper.class.getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                // 这里返回 null，由调用方打印具体的 notFound 日志，避免重复
                return null;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            // [Key Used]: ipGeolocationHelper.init.ioError
            // 记录具体是哪个文件读取失败
            ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.ioError", fileName + ", " + e.getMessage());
            return null;
        }
    }

    public static LocationInfo getLocationInfo(String ip) {
        if (ip == null || ip.isBlank()) {
            return LocationInfo.failed("Empty IP");
        }

        try {
            // 简单内网判断
            if (isInternalIp(ip)) {
                return new LocationInfo("Localhost", "Intranet", true, "Local");
            }

            String region = null;
            String usedSource = "Unknown";

            // 判断是否为 IPv6 (包含冒号)
            boolean isIpv6 = ip.indexOf(':') > -1;

            if (isIpv6) {
                if (v6Loaded && searcherV6 != null) {
                    region = searcherV6.search(ip);
                    usedSource = "Ip2region-v6";
                } else {
                    return LocationInfo.failed("IPv6 DB not loaded");
                }
            } else {
                if (v4Loaded && searcherV4 != null) {
                    region = searcherV4.search(ip);
                    usedSource = "Ip2region-v4";
                } else {
                    return LocationInfo.failed("IPv4 DB not loaded");
                }
            }

            if (region == null || region.isEmpty()) {
                return LocationInfo.failed();
            }

            return parseRegionStr(region, usedSource);

        } catch (Exception e) {
            // [Key Used]: ipGeolocationHelper.query.error
            ServerLogger.warnWithSource("IPGeolocationHelper", "ipGeolocationHelper.query.error", ip + ", " + e.getMessage());
            return LocationInfo.failed();
        }
    }

    /**
     * 智能解析：兼容 5段式 和 4段式 数据
     * 5段式: 国家|区域|省份|城市|ISP
     * 4段式: 国家|省份|城市|ISP
     */
    private static LocationInfo parseRegionStr(String region, String sourceName) {
        String[] parts = region.split("\\|");
        String location = "";
        String isp = "Unknown";

        if (parts.length == 5) {
            location = Arrays.stream(new String[]{parts[0], parts[2], parts[3]})
                    .filter(s -> s != null && !"0".equals(s) && !"Unknown".equalsIgnoreCase(s))
                    .distinct()
                    .collect(Collectors.joining(" "));
            isp = parts[4];
        } else if (parts.length == 4) {
            location = Arrays.stream(new String[]{parts[0], parts[1], parts[2]})
                    .filter(s -> s != null && !"0".equals(s) && !"Unknown".equalsIgnoreCase(s))
                    .distinct()
                    .collect(Collectors.joining(" "));
            isp = parts[3];
        } else {
            return new LocationInfo(region, "Unknown", true, sourceName);
        }

        if (location.isBlank()) {
            location = "Unknown Region";
        }
        if (isp == null || "0".equals(isp)) {
            isp = "Unknown";
        }

        return new LocationInfo(location, isp, true, sourceName);
    }

    private static boolean isInternalIp(String ip) {
        return ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
                || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }

    public static void shutdown() {
        try {
            if (searcherV4 != null) searcherV4.close();
            if (searcherV6 != null) searcherV6.close();
        } catch (IOException e) {
            // ignore
        }
    }

    public record LocationInfo(String location, String isp, boolean success, String source) {
        public static LocationInfo failed() {
            return new LocationInfo("N/A", "N/A", false, "Failed");
        }

        public static LocationInfo failed(String reason) {
            return new LocationInfo("N/A", "N/A", false, reason);
        }
    }
}