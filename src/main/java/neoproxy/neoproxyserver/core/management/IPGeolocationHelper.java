package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.ServerLogger;
import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class IPGeolocationHelper {

    private static final String FILE_V4 = "ip2region_v4.xdb";
    private static final String FILE_V6 = "ip2region_v6.xdb";
    private static final int XDB_LOAD_SLICE_BYTES = 1024 * 1024;
    private static final List<Path> TEMP_XDB_FILES = new ArrayList<>();

    private static Searcher searcherV4;
    private static Searcher searcherV6;
    private static boolean v4Loaded = false;
    private static boolean v6Loaded = false;
    private static Path tempXdbDirectory;
    private static boolean lowRamMode = false;

    static {
        initialize();
        Runtime.getRuntime().addShutdownHook(new Thread(IPGeolocationHelper::shutdown));
    }

    private static void initialize() {
        lowRamMode = NeoProxyServer.LOW_RAM_MODE;

        searcherV4 = openSearcher(FILE_V4, Version.IPv4,
                "ipGeolocationHelper.init.v4.success", "ipGeolocationHelper.init.v4.failed");
        v4Loaded = searcherV4 != null;
        if (!v4Loaded) {
            ServerLogger.warnWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v4.notFound");
        }

        searcherV6 = openSearcher(FILE_V6, Version.IPv6,
                "ipGeolocationHelper.init.v6.success", "ipGeolocationHelper.init.v6.failed");
        v6Loaded = searcherV6 != null;
        if (!v6Loaded) {
            ServerLogger.infoWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.v6.notFound");
        }
    }

    private static Searcher openSearcher(String fileName, Version version, String successKey, String failedKey) {
        try {
            if (lowRamMode) {
                File xdbFile = resolveFileBackedDatabase(fileName);
                if (xdbFile == null) {
                    return null;
                }
                Searcher searcher = Searcher.newWithFileOnly(version, xdbFile);
                ServerLogger.infoWithSource("IPGeolocationHelper", successKey, "file:" + xdbFile.length());
                return searcher;
            }

            LongByteArray content = loadResourceToBuffer(fileName);
            if (content == null) {
                return null;
            }
            Searcher searcher = Searcher.newWithBuffer(version, content);
            ServerLogger.infoWithSource("IPGeolocationHelper", successKey, String.valueOf(content.length()));
            return searcher;
        } catch (IOException e) {
            ServerLogger.errorWithSource("IPGeolocationHelper", failedKey, e.getMessage());
            return null;
        }
    }

    private static LongByteArray loadResourceToBuffer(String fileName) {
        try (InputStream inputStream = IPGeolocationHelper.class.getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                return null;
            }
            // 使用小块切片，避免启动期间额外申请一个 50 MiB 的临时内存块。
            return Searcher.loadContentFromInputStream(inputStream, XDB_LOAD_SLICE_BYTES);
        } catch (IOException e) {
            ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.ioError",
                    fileName + ", " + e.getMessage());
            return null;
        }
    }

    private static File resolveFileBackedDatabase(String fileName) throws IOException {
        Path externalPath = Path.of(NeoProxyServer.CURRENT_DIR_PATH, fileName);
        if (Files.isRegularFile(externalPath)) {
            return externalPath.toFile();
        }

        if (tempXdbDirectory == null) {
            tempXdbDirectory = Files.createTempDirectory("neoproxy-ipdb-");
            tempXdbDirectory.toFile().deleteOnExit();
        }

        Path targetPath = tempXdbDirectory.resolve(fileName);
        try (InputStream inputStream = IPGeolocationHelper.class.getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                return null;
            }
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            targetPath.toFile().deleteOnExit();
            TEMP_XDB_FILES.add(targetPath);
            return targetPath.toFile();
        } catch (IOException e) {
            ServerLogger.errorWithSource("IPGeolocationHelper", "ipGeolocationHelper.init.ioError",
                    fileName + ", " + e.getMessage());
            throw e;
        }
    }

    public static LocationInfo getLocationInfo(String ip) {
        if (ip == null || ip.isBlank()) {
            return LocationInfo.failed("Empty IP");
        }

        try {
            if (isInternalIp(ip)) {
                return new LocationInfo("Localhost", "Intranet", true, "Local");
            }

            boolean isIpv6 = ip.indexOf(':') > -1;
            String region;
            String usedSource;

            if (isIpv6) {
                if (!v6Loaded || searcherV6 == null) {
                    return LocationInfo.failed("IPv6 DB not loaded");
                }
                region = search(searcherV6, ip);
                usedSource = lowRamMode ? "Ip2region-v6-file" : "Ip2region-v6";
            } else {
                if (!v4Loaded || searcherV4 == null) {
                    return LocationInfo.failed("IPv4 DB not loaded");
                }
                region = search(searcherV4, ip);
                usedSource = lowRamMode ? "Ip2region-v4-file" : "Ip2region-v4";
            }

            if (region == null || region.isEmpty()) {
                return LocationInfo.failed();
            }

            return parseRegionStr(region, usedSource);
        } catch (Exception e) {
            ServerLogger.warnWithSource("IPGeolocationHelper", "ipGeolocationHelper.query.error",
                    ip + ", " + e.getMessage());
            return LocationInfo.failed();
        }
    }

    private static String search(Searcher searcher, String ip) throws Exception {
        // Searcher 持有可变的游标和计数状态；同步可以避免 RandomAccessFile 的 seek 状态被并发破坏。
        synchronized (searcher) {
            return searcher.search(ip);
        }
    }

    private static LocationInfo parseRegionStr(String region, String sourceName) {
        if (region == null || region.isEmpty()) {
            return LocationInfo.failed();
        }

        String[] parts = region.split("\\|");
        if (parts.length < 5) {
            return new LocationInfo(region, "Unknown", true, sourceName);
        }

        String country = parts[0];
        String province = parts[1];
        String city = parts[2];
        String isp = parts[3];
        String isoCode = parts[4];

        if ("Reserved".equalsIgnoreCase(country)) {
            return new LocationInfo("Reserved IP", "Unknown", true, sourceName);
        }

        String location = Arrays.stream(new String[]{country, province, city})
                .filter(s -> s != null && !"0".equals(s))
                .distinct()
                .collect(Collectors.joining(" "));

        if (!"0".equals(isoCode)) {
            location += " [" + isoCode + "]";
        }

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

    public static synchronized void shutdown() {
        closeSearcher(searcherV4);
        closeSearcher(searcherV6);
        searcherV4 = null;
        searcherV6 = null;
        v4Loaded = false;
        v6Loaded = false;

        for (Path path : TEMP_XDB_FILES) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
        TEMP_XDB_FILES.clear();

        if (tempXdbDirectory != null) {
            try {
                Files.deleteIfExists(tempXdbDirectory);
            } catch (IOException ignored) {
            }
            tempXdbDirectory = null;
        }
    }

    private static void closeSearcher(Searcher searcher) {
        if (searcher == null) {
            return;
        }
        try {
            searcher.close();
        } catch (IOException ignored) {
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
