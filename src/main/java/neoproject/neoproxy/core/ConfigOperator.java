package neoproject.neoproxy.core;

import neoproject.neoproxy.NeoProxyServer;
import neoproject.neoproxy.core.management.IPChecker;
import neoproject.neoproxy.core.management.TransferSocketAdapter;
import neoproject.neoproxy.core.threads.TCPTransformer;
import plethora.management.bufferedFile.BufferedFile;
import plethora.utils.config.LineConfigReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ConfigOperator {
    public static final BufferedFile CONFIG_FILE = new BufferedFile(NeoProxyServer.CURRENT_DIR_PATH + File.separator + "config.cfg");

    private ConfigOperator() {
    }

    public static void readAndSetValue() {
        LineConfigReader lineConfigReader = new LineConfigReader(CONFIG_FILE);

        if (!CONFIG_FILE.exists()) {
            createAndSetDefaultConfig();
        } else {
            try {
                lineConfigReader.load();

                NeoProxyServer.LOCAL_DOMAIN_NAME = lineConfigReader.get("LOCAL_DOMAIN_NAME");
                NeoProxyServer.HOST_HOOK_PORT = Integer.parseInt(lineConfigReader.get("HOST_HOOK_PORT"));
                NeoProxyServer.HOST_CONNECT_PORT = Integer.parseInt(lineConfigReader.get("HOST_CONNECT_PORT"));
                InfoBox.alert = Boolean.parseBoolean(lineConfigReader.get("ALERT"));
                HostClient.SAVE_DELAY = Integer.parseInt(lineConfigReader.get("SAVE_DELAY"));
                HostClient.AES_KEY_SIZE = Integer.parseInt(lineConfigReader.get("AES_KEY_SIZE"));
                TCPTransformer.BUFFER_LEN = Integer.parseInt(lineConfigReader.get("BUFFER_LEN"));
                TCPTransformer.TELL_BALANCE_MIB = Integer.parseInt(lineConfigReader.get("TELL_BALANCE_MIB"));
                TCPTransformer.CUSTOM_BLOCKING_MESSAGE = lineConfigReader.get("CUSTOM_BLOCKING_MESSAGE");
                IPChecker.ENABLE_BAN = Boolean.parseBoolean(lineConfigReader.get("ENABLE_BAN"));
                TransferSocketAdapter.SO_TIMEOUT = Integer.parseInt(lineConfigReader.get("SO_TIMEOUT"));

            } catch (Exception e) {
                createAndSetDefaultConfig();
            }
        }
    }

    private static void createAndSetDefaultConfig() {
        CONFIG_FILE.delete();
        CONFIG_FILE.createNewFile();

        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(CONFIG_FILE, StandardCharsets.UTF_8));

            bufferedWriter.write("""
                    #把你的公网ip或域名放在这里，如果你只是本地测试，请用 localhost
                    #Put your public network ip or domain name here, if you are testing locally, please use localhost
                    LOCAL_DOMAIN_NAME=localhost
                    
                    #是否开启详细的连接通知
                    #Whether to enable detailed connection notifications
                    ALERT=true
                    
                    #是否开启非法连接封禁
                    #Whether to enable illegal connection ban
                    ENABLE_BAN=true
                    
                    ##自定义网页拦截消息，换行使用<br>
                    #Customize webpage interception messages, use <br> for line breaks
                    CUSTOM_BLOCKING_MESSAGE=如有疑问，请联系您的系统管理员。
                    
                    #设置服务端最大等待客户端响应的时间，单位为毫秒
                    #Set the maximum waiting time for the server to respond to the client, in milliseconds
                    SO_TIMEOUT=1000
                    
                    #当多少流量被消耗时告诉客户端剩余的流量
                    #When how much traffic is consumed, tell the client the remaining traffic
                    TELL_BALANCE_MIB=10
                    
                    #如果你不知道以下设置是干什么的，请不要动它
                    #If you don't know what the following setting does, please don't touch it
                    HOST_HOOK_PORT=801
                    HOST_CONNECT_PORT=802
                    
                    #外部接收数据包数组的长度
                    #The length of the external receive packet array
                    BUFFER_LEN=4096
                    
                    #设置保存序列号文件的间隔，单位为毫秒
                    #Set the interval for saving the serial number file, in milliseconds
                    SAVE_DELAY=3000
                    
                    #AES加密的秘钥长度
                    #AES encryption key length
                    AES_KEY_SIZE=128""");

            bufferedWriter.flush();
            bufferedWriter.close();

        } catch (IOException e) {
            NeoProxyServer.sayError("ConfigOperator", "Fail to write default config.");
            System.exit(-1);
        }

        readAndSetValue();
    }
}
