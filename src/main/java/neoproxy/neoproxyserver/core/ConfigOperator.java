package neoproxy.neoproxyserver.core;

import top.ceroxe.api.utils.config.LineConfigReader;
import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.constants.ServerConstants;
import neoproxy.neoproxyserver.core.management.IPChecker;
import neoproxy.neoproxyserver.core.management.TransferSocketAdapter;
import neoproxy.neoproxyserver.core.threads.TCPTransformer;
import neoproxy.neoproxyserver.core.webadmin.WebAdminManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigOperator {

    public static final File CONFIG_FILE = new File(NeoProxyServer.CURRENT_DIR_PATH + File.separator + "config.cfg");
    public static final File SYNC_CONFIG_FILE = new File(NeoProxyServer.CURRENT_DIR_PATH + File.separator + "sync.cfg");

    private static final Path CONFIG_PATH = CONFIG_FILE.toPath();
    private static final Path SYNC_CONFIG_PATH = SYNC_CONFIG_FILE.toPath();

    // ==================== Sync Config Definitions ====================
    public static String MANAGER_URL = null;
    public static String MANAGER_TOKEN = "";
    public static String NODE_ID = "Default-Node";
    private static boolean isInitialized = false;
    // =================================================================

    private ConfigOperator() {
    }

    public static void readAndSetValue() {
        readMainConfig();
        readSyncConfig();
        isInitialized = true;
    }

    private static void readMainConfig() {
        if (!Files.exists(CONFIG_PATH)) {
            copyConfigFromResource("config.cfg", CONFIG_PATH);
        }

        LineConfigReader reader = new LineConfigReader(CONFIG_FILE);

        try {
            reader.load();
            applyMainSettings(reader);
        } catch (IOException e) {
            ServerLogger.error("configOperator.corruptedConfigFile", e);
            copyConfigFromResource("config.cfg", CONFIG_PATH);
            try {
                reader.load();
                applyMainSettings(reader);
            } catch (IOException fatalException) {
                ServerLogger.error("configOperator.fatalConfigError", fatalException);
                System.exit(-1);
            }
        }
    }

    private static void readSyncConfig() {
        if (!Files.exists(SYNC_CONFIG_PATH)) {
            copyConfigFromResource("sync.cfg", SYNC_CONFIG_PATH);
        }

        LineConfigReader reader = new LineConfigReader(SYNC_CONFIG_FILE);
        try {
            reader.load();
            applySyncSettings(reader);
        } catch (IOException e) {
            ServerLogger.error("configOperator.corruptedConfigFile", e);
            copyConfigFromResource("sync.cfg", SYNC_CONFIG_PATH);
            try {
                reader.load();
                applySyncSettings(reader);
            } catch (IOException fatalException) {
                ServerLogger.error("configOperator.fatalConfigError", fatalException);
            }
        }
    }

    private static void copyConfigFromResource(String resourceName, Path targetPath) {
        try (InputStream is = openDefaultConfigResource(resourceName)) {
            if (is == null) {
                throw new RuntimeException("Default " + resourceName + " not found in resources!");
            }
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            ServerLogger.info("configOperator.createdDefaultConfig");
        } catch (IOException e) {
            ServerLogger.error("configOperator.failToWriteDefaultConfig", e);
            System.exit(-1);
        }
    }

    private static InputStream openDefaultConfigResource(String resourceName) {
        InputStream fromTemplates = NeoProxyServer.class.getResourceAsStream("/templates/" + resourceName);
        return fromTemplates != null ? fromTemplates : NeoProxyServer.class.getResourceAsStream("/" + resourceName);
    }

    private static void applyMainSettings(LineConfigReader reader) {
        int oldWebPort = WebAdminManager.WEB_ADMIN_PORT;

        NeoProxyServer.LOCAL_DOMAIN_NAME = reader.getOptional("LOCAL_DOMAIN_NAME")
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(ServerConstants.DEFAULT_LOCAL_DOMAIN_NAME);
        NeoProxyServer.HOST_HOOK_PORT = readPort(reader, "HOST_HOOK_PORT", ServerConstants.DEFAULT_HOST_HOOK_PORT);
        NeoProxyServer.HOST_CONNECT_PORT = readPort(reader, "HOST_CONNECT_PORT", ServerConstants.DEFAULT_HOST_CONNECT_PORT);
        WebAdminManager.WEB_ADMIN_PORT = readPort(reader, "WEB_ADMIN_PORT", ServerConstants.DEFAULT_WEB_ADMIN_PORT);
        IPChecker.ENABLE_BAN = readBoolean(reader, "ENABLE_BAN", true);
        ServerLogger.alert = readBoolean(reader, "ALERT", true);
        HostClient.SAVE_DELAY = readInt(reader, "SAVE_DELAY", ServerConstants.DEFAULT_SAVE_DELAY, 0, Integer.MAX_VALUE);
        HostClient.AES_KEY_SIZE = readAesKeySize(reader, "AES_KEY_SIZE", ServerConstants.AES_KEY_SIZE);
        HostClient.HEARTBEAT_TIMEOUT = readInt(reader, "HEARTBEAT_TIMEOUT", ServerConstants.DEFAULT_HEARTBEAT_TIMEOUT, 0, Integer.MAX_VALUE);
        TCPTransformer.CUSTOM_BLOCKING_MESSAGE = reader.getOptional("CUSTOM_BLOCKING_MESSAGE").orElse("您没有访问网页的权限<br>请联系管理员以获取进一步支持");
        TCPTransformer.TELL_BALANCE_MIB = readInt(reader, "TELL_BALANCE_MIB", 10, 1, Integer.MAX_VALUE);
        TransferSocketAdapter.SO_TIMEOUT = readInt(reader, "SO_TIMEOUT", 5000, 1, Integer.MAX_VALUE);

        String permToken = reader.getOptional("WEB_ADMIN_TOKEN").orElse("").trim();
        WebAdminManager.setPermanentToken(permToken);

        // 读取 SSL 配置
        String sslCertPath = reader.getOptional("SSL_CERT_PATH").orElse("").trim();
        String sslKeyPath = reader.getOptional("SSL_KEY_PATH").orElse("").trim();
        String sslPassword = reader.getOptional("SSL_KEY_PASSWORD").orElse("").trim();
        WebAdminManager.setSslConfig(sslCertPath, sslKeyPath, sslPassword);

        if (!permToken.isEmpty()) {
            ServerLogger.warnWithSource("Config", "configOperator.permTokenWarning");
        }

        if (isInitialized) {
            if (oldWebPort != WebAdminManager.WEB_ADMIN_PORT && WebAdminManager.isRunning()) {
                WebAdminManager.restart();
            }
        }
    }

    private static void applySyncSettings(LineConfigReader reader) {
        // 获取配置中的原始 URL
        String rawUrl = reader.getOptional("MANAGER_URL").orElse(null);

        // 【智能修正】：去除 URL 末尾的斜杠 /
        if (rawUrl != null && !rawUrl.isBlank()) {
            rawUrl = rawUrl.trim(); // 去除首尾空格
            while (rawUrl.endsWith("/")) {
                rawUrl = rawUrl.substring(0, rawUrl.length() - 1);
            }
            MANAGER_URL = rawUrl;
        } else {
            MANAGER_URL = null;
        }

        MANAGER_TOKEN = reader.getOptional("MANAGER_TOKEN").orElse("");
        NODE_ID = reader.getOptional("NODE_ID").orElse("Default-Node");

        if (MANAGER_URL != null && !MANAGER_URL.isBlank()) {
            ServerLogger.info("configOperator.syncModeEnabled", MANAGER_URL, NODE_ID);
        }
    }

    private static int readPort(LineConfigReader reader, String key, int defaultValue) {
        return readInt(reader, key, defaultValue, 1, 65535);
    }

    private static int readAesKeySize(LineConfigReader reader, String key, int defaultValue) {
        int value = readInt(reader, key, defaultValue, 1, Integer.MAX_VALUE);
        if (value == 128 || value == 192 || value == 256) {
            return value;
        }
        Debugger.debugOperation("Invalid AES key size for " + key + ": " + value + ", fallback to " + defaultValue);
        return defaultValue;
    }

    private static int readInt(LineConfigReader reader, String key, int defaultValue, int min, int max) {
        String rawValue = reader.getOptional(key).orElse(null);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(rawValue.trim());
            if (value < min || value > max) {
                Debugger.debugOperation("Config value out of range for " + key + ": " + rawValue + ", fallback to " + defaultValue);
                return defaultValue;
            }
            return value;
        } catch (NumberFormatException e) {
            Debugger.debugOperation("Invalid integer config for " + key + ": " + rawValue + ", fallback to " + defaultValue);
            return defaultValue;
        }
    }

    private static boolean readBoolean(LineConfigReader reader, String key, boolean defaultValue) {
        String rawValue = reader.getOptional(key).orElse(null);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        Debugger.debugOperation("Invalid boolean config for " + key + ": " + rawValue + ", fallback to " + defaultValue);
        return defaultValue;
    }
}
