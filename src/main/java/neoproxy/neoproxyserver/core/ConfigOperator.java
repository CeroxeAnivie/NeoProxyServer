package neoproxy.neoproxyserver.core;

import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.management.IPChecker;
import neoproxy.neoproxyserver.core.management.TransferSocketAdapter;
import neoproxy.neoproxyserver.core.threads.TCPTransformer;
import plethora.utils.config.LineConfigReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 配置操作器，负责加载、创建和应用服务器配置。
 * 此版本已使用优化后的 LineConfigReader API 进行重构，以提高健壮性和可维护性。
 */
public final class ConfigOperator {

    public static final File CONFIG_FILE = new File(NeoProxyServer.CURRENT_DIR_PATH + java.io.File.separator + "config.cfg");
    private static final Path CONFIG_PATH = CONFIG_FILE.toPath();

    /**
     * 默认配置内容，提取为常量以提高可读性。
     */
    private static final String DEFAULT_CONFIG_CONTENT = """
            #把你的公网ip或域名放在这里，如果你只是本地测试，请用 localhost
            #Put your public network ip or domain name here, if you are testing locally, please use localhost
            LOCAL_DOMAIN_NAME=localhost
            
            #是否开启详细的连接通知
            #Whether to enable detailed connection notifications
            ALERT=true
            
            #是否开启非法连接封禁
            #Whether to enable illegal connection ban
            ENABLE_BAN=true
            
            #自定义网页拦截消息，换行使用<br>
            #Customize webpage interception messages, use <br> for line breaks
            CUSTOM_BLOCKING_MESSAGE=根据中国法律，网页相关服务需要报备。<br>加入 QQ 群 304509047 获取进一步支持。
            
            #设置服务端最大等待客户端响应的时间，单位为毫秒
            #Set the maximum waiting time for the server to respond to the client, in milliseconds
            SO_TIMEOUT=2000
            
            #当多少流量被消耗时告诉客户端剩余的流量
            #When how much traffic is consumed, tell the client the remaining traffic
            TELL_BALANCE_MIB=10
            
            #如果你不知道以下设置是干什么的，请不要动它
            #If you don't know what the following setting does, please don't touch it
            HOST_HOOK_PORT=44801
            HOST_CONNECT_PORT=44802
            
            #外部接收数据包数组的长度
            #The length of the external receive packet array
            BUFFER_LEN=4096
            
            #设置保存序列号文件的间隔，单位为毫秒
            #Set the interval for saving the serial number file, in milliseconds
            SAVE_DELAY=3000
            
            #AES加密的秘钥长度
            #AES encryption key length
            AES_KEY_SIZE=128
            
            #服务端判断客户端几秒内无响应就判断为超时的时间（单位：毫秒）
            # The timeout period (in milliseconds) for which the server determines if the client has not responded.
            HEARTBEAT_TIMEOUT=5000""";

    private ConfigOperator() {
        // 工具类，禁止实例化
    }

    /**
     * 读取配置文件并设置所有相关的静态变量。
     * 如果文件不存在或读取失败，将创建并使用默认配置。
     */
    public static void readAndSetValue() {
        LineConfigReader reader = new LineConfigReader(CONFIG_PATH.toFile());

        // 如果配置文件不存在，则先创建它
        if (!Files.exists(CONFIG_PATH)) {
            createDefaultConfigFile();
        }

        try {
            // 尝试加载配置文件
            reader.load();
            // 如果加载成功，应用配置
            applySettings(reader);
        } catch (IOException e) {
            // 如果加载失败（例如文件损坏），则覆盖创建默认配置，并重试一次
            ServerLogger.error("configOperator.corruptedConfigFile", e);
            ServerLogger.info("configOperator.creatingDefaultConfigAndRetry");
            createDefaultConfigFile();
            try {
                reader.load();
                applySettings(reader);
            } catch (IOException fatalException) {
                // 如果连默认配置都加载失败，则无法继续
                ServerLogger.error("configOperator.fatalConfigError", fatalException);
                System.exit(-1);
            }
        }
    }

    /**
     * 从 LineConfigReader 中安全地读取配置并应用到各个类的静态字段。
     * 使用 Optional API 来避免 NPE 和 NumberFormatException。
     */
    private static void applySettings(LineConfigReader reader) {
        NeoProxyServer.LOCAL_DOMAIN_NAME = reader.getOptional("LOCAL_DOMAIN_NAME").orElse("localhost");
        NeoProxyServer.HOST_HOOK_PORT = reader.getOptional("HOST_HOOK_PORT").map(Integer::parseInt).orElse(44801);
        NeoProxyServer.HOST_CONNECT_PORT = reader.getOptional("HOST_CONNECT_PORT").map(Integer::parseInt).orElse(44802);
        ServerLogger.alert = reader.getOptional("ALERT").map(Boolean::parseBoolean).orElse(true);
        HostClient.SAVE_DELAY = reader.getOptional("SAVE_DELAY").map(Integer::parseInt).orElse(3000);
        HostClient.AES_KEY_SIZE = reader.getOptional("AES_KEY_SIZE").map(Integer::parseInt).orElse(128);
        HostClient.HEARTBEAT_TIMEOUT = reader.getOptional("HEARTBEAT_TIMEOUT").map(Integer::parseInt).orElse(5000);
        TCPTransformer.BUFFER_LEN = reader.getOptional("BUFFER_LEN").map(Integer::parseInt).orElse(4096);
        TCPTransformer.TELL_BALANCE_MIB = reader.getOptional("TELL_BALANCE_MIB").map(Integer::parseInt).orElse(10);
        TCPTransformer.CUSTOM_BLOCKING_MESSAGE = reader.getOptional("CUSTOM_BLOCKING_MESSAGE").orElse("如有疑问，请联系您的系统管理员。");
        IPChecker.ENABLE_BAN = reader.getOptional("ENABLE_BAN").map(Boolean::parseBoolean).orElse(true);
        TransferSocketAdapter.SO_TIMEOUT = reader.getOptional("SO_TIMEOUT").map(Integer::parseInt).orElse(1000);
    }

    /**
     * 创建默认的配置文件，覆盖任何已存在的同名文件。
     * 使用现代的 NIO.2 API 和 try-with-resources 确保资源安全。
     */
    private static void createDefaultConfigFile() {
        try {
            // 使用 Files.write 提供了更原子和简洁的写文件方式
            // StandardOpenOption.CREATE, TRUNCATE_EXISTING, WRITE 确保文件被创建并覆盖
            Files.writeString(CONFIG_PATH, DEFAULT_CONFIG_CONTENT, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            ServerLogger.error("configOperator.failToWriteDefaultConfig", e);
            System.exit(-1);
        }
    }
}