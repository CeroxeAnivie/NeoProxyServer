package neoproxy.neoproxyserver.core;

import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.webadmin.WebAdminManager;
import plethora.utils.config.LineConfigReader;

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
        try (InputStream is = NeoProxyServer.class.getResourceAsStream("/" + resourceName)) {
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

    private static void applyMainSettings(LineConfigReader reader) {
        int oldWebPort = WebAdminManager.WEB_ADMIN_PORT;

        NeoProxyServer.LOCAL_DOMAIN_NAME = reader.getOptional("LOCAL_DOMAIN_NAME").orElse("localhost");
        NeoProxyServer.HOST_HOOK_PORT = reader.getOptional("HOST_HOOK_PORT").map(Integer::parseInt).orElse(44801);
        NeoProxyServer.HOST_CONNECT_PORT = reader.getOptional("HOST_CONNECT_PORT").map(Integer::parseInt).orElse(44802);
        WebAdminManager.WEB_ADMIN_PORT = reader.getOptional("WEB_ADMIN_PORT").map(Integer::parseInt).orElse(44803);
        ServerLogger.alert = reader.getOptional("ALERT").map(Boolean::parseBoolean).orElse(true);
        HostClient.SAVE_DELAY = reader.getOptional("SAVE_DELAY").map(Integer::parseInt).orElse(3000);
        HostClient.AES_KEY_SIZE = reader.getOptional("AES_KEY_SIZE").map(Integer::parseInt).orElse(128);
        HostClient.HEARTBEAT_TIMEOUT = reader.getOptional("HEARTBEAT_TIMEOUT").map(Integer::parseInt).orElse(5000);

        String permToken = reader.getOptional("WEB_ADMIN_TOKEN").orElse("").trim();
        WebAdminManager.setPermanentToken(permToken);

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
}