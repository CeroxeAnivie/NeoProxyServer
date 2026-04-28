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
                searcherV4 = Searcher.newWithBuffer(Version.IPv4, toLongByteArray(v4Data));
                v4Loaded = true;
                // [Key Used]: ipGeolocationHelper.init.v4.success
                ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v4.success", String.valueOf(v4Data.length));
            } catch (IOException e) {
                // [Key Used]: ipGeolocationHelper.init.v4.failed
                ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v4.failed", e.getMessage());
            }
        } else {
            // [Key Used]: ipGeolocationHelper.init.v4.notFound
            ServerLogger.warnWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v4.notFound");
        }

        // -------------------------------------------------
        // 2. 加载 IPv6
        // -------------------------------------------------
        byte[] v6Data = loadFileToBytes(FILE_V6);
        if (v6Data != null) {
            try {
                searcherV6 = Searcher.newWithBuffer(Version.IPv6, toLongByteArray(v6Data));
                v6Loaded = true;
                // [Key Used]: ipGeolocationHelper.init.v6.success
                ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v6.success", String.valueOf(v6Data.length));
            } catch (IOException e) {
                // [Key Used]: ipGeolocationHelper.init.v6.failed
                ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v6.failed", e.getMessage());
            }
        } else {
            // [Key Used]: ipGeolocationHelper.init.v6.notFound
            ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v6.notFound");
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

    private static LongByteArray toLongByteArray(byte[] data) throws IOException {
        LongByteArray buffer = new LongByteArray(data.length);
        buffer.append(data);
        return buffer;
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
     * 适配 v3.13.0 最新格式：国家|省份|城市|ISP|国家代码
     * 示例1 (国内): 中国|广东省|深圳市|移动|CN
     * 示例2 (国外): United Kingdom|England|Yateley|0|GB
     * 示例3 (保留): Reserved|Reserved|Reserved|0|0
     */
    private static LocationInfo parseRegionStr(String region, String sourceName) {
        if (region == null || region.isEmpty()) {
            return LocationInfo.failed();
        }

        String[] parts = region.split("\\|");

        // v3.13.0 统一为 5 段，如果不足 5 段说明数据文件版本不对
        if (parts.length < 5) {
            return new LocationInfo(region, "Unknown", true, sourceName);
        }

        String country = parts[0]; // 国家
        String province = parts[1]; // 省份
        String city = parts[2]; // 城市
        String isp = parts[3]; // ISP
        String isoCode = parts[4]; // 国家代码 (iso-3166-alpha2)

        // 1. 处理保留地址
        if ("Reserved".equalsIgnoreCase(country)) {
            return new LocationInfo("Reserved IP", "Unknown", true, sourceName);
        }

        // 2. 拼接地理位置 (国家 省份 城市)
        // 过滤掉为 "0" 的字段（代表无该级信息）
        String location = Arrays.stream(new String[]{country, province, city})
                .filter(s -> s != null && !"0".equals(s))
                .distinct()
                .collect(Collectors.joining(" "));

        // 3. 处理国家代码 (建议拼在位置后面，方便识别海外城市)
        if (!"0".equals(isoCode)) {
            location += " [" + isoCode + "]";
        }

        // 4. 处理 ISP
        if (isp == null || "0".equals(isp)) {
            isp = "Unknown";
        }

        if (location.isBlank()) {
            location = "Unknown Region";
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
