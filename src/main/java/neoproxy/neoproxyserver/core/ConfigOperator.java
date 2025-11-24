package neoproxy.neoproxyserver.core;

import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.management.IPChecker;
import neoproxy.neoproxyserver.core.management.TransferSocketAdapter;
import neoproxy.neoproxyserver.core.threads.TCPTransformer;
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
    private static final Path CONFIG_PATH = CONFIG_FILE.toPath();

    // 标记是否已完成首次初始化
    private static boolean isInitialized = false;

    private ConfigOperator() {
    }

    public static void readAndSetValue() {
        // 如果配置文件不存在，从资源文件复制
        if (!Files.exists(CONFIG_PATH)) {
            copyDefaultConfigFromResource();
        }

        LineConfigReader reader = new LineConfigReader(CONFIG_FILE);

        try {
            reader.load();
            applySettings(reader);
            // 【关键】首次加载完成后，标记为已初始化
            isInitialized = true;

        } catch (IOException e) {
            ServerLogger.error("configOperator.corruptedConfigFile", e);
            // 尝试再次复制修复
            ServerLogger.info("configOperator.creatingDefaultConfigAndRetry");
            copyDefaultConfigFromResource();
            try {
                reader.load();
                applySettings(reader);
                isInitialized = true;
            } catch (IOException fatalException) {
                ServerLogger.error("configOperator.fatalConfigError", fatalException);
                System.exit(-1);
            }
        }
    }

    private static void copyDefaultConfigFromResource() {
        try (InputStream is = NeoProxyServer.class.getResourceAsStream("/config.cfg")) {
            if (is == null) {
                throw new RuntimeException("Default config.cfg not found in resources!");
            }
            Files.copy(is, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            ServerLogger.info("configOperator.createdDefaultConfig");
        } catch (IOException e) {
            ServerLogger.error("configOperator.failToWriteDefaultConfig", e);
            System.exit(-1);
        }
    }

    private static void applySettings(LineConfigReader reader) {
        // 1. 保存旧值用于对比
        int oldWebPort = WebAdminManager.WEB_ADMIN_PORT;
        int oldHookPort = NeoProxyServer.HOST_HOOK_PORT;
        int oldConnectPort = NeoProxyServer.HOST_CONNECT_PORT;

        // 2. 读取并更新配置
        NeoProxyServer.LOCAL_DOMAIN_NAME = reader.getOptional("LOCAL_DOMAIN_NAME").orElse("localhost");
        NeoProxyServer.HOST_HOOK_PORT = reader.getOptional("HOST_HOOK_PORT").map(Integer::parseInt).orElse(44801);
        NeoProxyServer.HOST_CONNECT_PORT = reader.getOptional("HOST_CONNECT_PORT").map(Integer::parseInt).orElse(44802);
        WebAdminManager.WEB_ADMIN_PORT = reader.getOptional("WEB_ADMIN_PORT").map(Integer::parseInt).orElse(44803);
        ServerLogger.alert = reader.getOptional("ALERT").map(Boolean::parseBoolean).orElse(true);
        HostClient.SAVE_DELAY = reader.getOptional("SAVE_DELAY").map(Integer::parseInt).orElse(3000);
        HostClient.AES_KEY_SIZE = reader.getOptional("AES_KEY_SIZE").map(Integer::parseInt).orElse(128);
        HostClient.HEARTBEAT_TIMEOUT = reader.getOptional("HEARTBEAT_TIMEOUT").map(Integer::parseInt).orElse(5000);
        TCPTransformer.BUFFER_LEN = reader.getOptional("BUFFER_LEN").map(Integer::parseInt).orElse(4096);
        TCPTransformer.TELL_BALANCE_MIB = reader.getOptional("TELL_BALANCE_MIB").map(Integer::parseInt).orElse(10);
        TCPTransformer.CUSTOM_BLOCKING_MESSAGE = reader.getOptional("CUSTOM_BLOCKING_MESSAGE").orElse("如有疑问，请联系您的系统管理员。");
        IPChecker.ENABLE_BAN = reader.getOptional("ENABLE_BAN").map(Boolean::parseBoolean).orElse(true);
        TransferSocketAdapter.SO_TIMEOUT = reader.getOptional("SO_TIMEOUT").map(Integer::parseInt).orElse(5000);

        // 读取永久 Token
        String permToken = reader.getOptional("WEB_ADMIN_TOKEN").orElse("").trim();
        WebAdminManager.setPermanentToken(permToken);

        // 3. 警告与变更检测

        // 【修复】统一使用 ServerLogger 输出警告，不再使用 System.out
        if (!permToken.isEmpty()) {
            ServerLogger.warnWithSource("Config", "configOperator.permTokenWarning");
        }

        if (isInitialized) {
            // WebAdmin 端口变更处理
            if (oldWebPort != WebAdminManager.WEB_ADMIN_PORT) {
                if (WebAdminManager.isRunning()) {
                    ServerLogger.infoWithSource("Config", "configOperator.webAdminRestart");
                    WebAdminManager.restart();
                }
            }

            // 核心端口变更警告
            if (oldHookPort != NeoProxyServer.HOST_HOOK_PORT || oldConnectPort != NeoProxyServer.HOST_CONNECT_PORT) {
                ServerLogger.warnWithSource("Config", "configOperator.corePortWarning");
            }
        }
    }
}