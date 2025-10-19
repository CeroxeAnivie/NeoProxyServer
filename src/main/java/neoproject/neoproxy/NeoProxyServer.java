package neoproject.neoproxy;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import neoproject.neoproxy.core.*;
import neoproject.neoproxy.core.exceptions.*;
import neoproject.neoproxy.core.management.*;
import neoproject.neoproxy.core.threads.Transformer;
import plethora.management.bufferedFile.SizeCalculator;
import plethora.net.SecureServerSocket;
import plethora.net.SecureSocket;
import plethora.utils.ArrayUtils;
import plethora.utils.MyConsole;
import plethora.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

import static neoproject.neoproxy.core.management.IPChecker.loadBannedIPs;
import static neoproject.neoproxy.core.management.SequenceKey.DYNAMIC_PORT;
import static neoproject.neoproxy.core.management.SequenceKey.initKeyDatabase;

public class NeoProxyServer {
    public static final String CURRENT_DIR_PATH = System.getProperty("user.dir");
    public static final CopyOnWriteArrayList<HostClient> availableHostClient = new CopyOnWriteArrayList<>();
    public static String EXPECTED_CLIENT_VERSION = "3.2-RELEASE|3.3-RELEASE|3.4-RELEASE|3.5-RELEASE|3.6-RELEASE|3.7-RELEASE";//从左到右从老到新版本
    public static final CopyOnWriteArrayList<String> availableVersions = ArrayUtils.stringArrayToList(EXPECTED_CLIENT_VERSION.split("\\|"));
    public static int HOST_HOOK_PORT = 801;
    public static int HOST_CONNECT_PORT = 802;
    public static String LOCAL_DOMAIN_NAME = "localhost";
    public static SecureServerSocket hostServerTransferServerSocket = null;
    public static SecureServerSocket hostServerHookServerSocket = null;
    public static boolean IS_DEBUG_MODE = false;
    public static MyConsole myConsole;

    public static boolean isStopped = false;

    private static DatabaseReader dbReader; // 添加这行

    public static void initConsole() {
        ConsoleManager.init();
    }

    public static void initStructure() {
        // 初始化顺序更清晰
        initConsole();           // 1. 控制台系统
        printLogo();
        initKeyDatabase();       // 2. 数据库
        ConfigOperator.readAndSetValue(); // 3. 配置
        UpdateManager.init();    // 4. 更新管理器
        SecureSocket.setMaxAllowedPacketSize((int) SizeCalculator.mibToByte(200)); // 5. 设置单次数据包最大大小为 200m
        loadBannedIPs();

        // --- 新增：初始化 GeoIP2 DatabaseReader ---
        try {
            File database = new File("GeoLite2-City.mmdb"); // 请将 .mmdb 文件放在项目根目录或指定路径
            dbReader = new DatabaseReader.Builder(database).build();
            sayInfo("GeoIP2 database loaded successfully.");
        } catch (IOException e) {
            debugOperation(e);
            sayError("Could not load GeoIP2 database, location lookup will be disabled.");
            // dbReader 保持为 null
        }
        // --- 结束新增 ---

        // 5. 网络服务
        try {
            hostServerHookServerSocket = new SecureServerSocket(HOST_HOOK_PORT);
            TransferSocketAdapter.startThread();
        } catch (IOException e) {
            debugOperation(e);
            sayError("Can not bind the port, it's occupied?");
            System.exit(-1);
        }
    }

    private static void checkARGS(String[] args) {
        for (String arg : args) {
            switch (arg) {
                case "--debug" -> IS_DEBUG_MODE = true;
                // 可以继续添加其他参数，例如：
                // case "--verbose" -> IS_VERBOSE = true;
                default -> {
                } // 忽略未知参数
            }
        }
    }

    private static void printLogo(){
        sayInfo("-----------------------------------------------------");
        sayInfo("""
                
                   _____                                    \s
                  / ____|                                   \s
                 | |        ___   _ __    ___   __  __   ___\s
                 | |       / _ \\ | '__|  / _ \\  \\ \\/ /  / _ \\
                 | |____  |  __/ | |    | (_) |  >  <  |  __/
                  \\_____|  \\___| |_|     \\___/  /_/\\_\\  \\___|
                                                            \s
                                                             \
                """);
    }

    public static void main(String[] args) {
        // 注册优雅关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(NeoProxyServer::shutdown));

        NeoProxyServer.checkARGS(args);
        NeoProxyServer.initStructure();

        sayInfo("Current log file : " + myConsole.getLogFile().getAbsolutePath());
        sayInfo("LOCAL_DOMAIN_NAME: " + LOCAL_DOMAIN_NAME);
        sayInfo("Listen HOST_CONNECT_PORT on " + HOST_CONNECT_PORT);
        sayInfo("Listen HOST_HOOK_PORT on " + HOST_HOOK_PORT);
        sayInfo("Support client versions: " + EXPECTED_CLIENT_VERSION);

        while (!isStopped) {
            try {
                HostClient hostClient = listenAndConfigureHostClient();
                handleNewHostClient(hostClient);
            } catch (IOException e) {
                debugOperation(e);
                if (!isStopped) {
                    InfoBox.sayAHostClientTryToConnectButFail();
                } else {
                    break;
                }
            } catch (SlientException ignored) {
                // 静默异常，继续循环
            }
        }
    }

    // 新增方法：处理新连接的 HostClient
    private static void handleNewHostClient(HostClient hostClient) {
        new Thread(() -> {
            try {
                NeoProxyServer.checkHostClientLegitimacyAndTellInfo(hostClient);
                NeoProxyServer.handleTransformerServiceWithNewThread(hostClient);
            } catch (IndexOutOfBoundsException | IOException |
                     NoMorePortException |
                     AlreadyBlindPortException | UnRecognizedKeyException | OutDatedKeyException e) {
                InfoBox.sayHostClientDiscInfo(hostClient, "Main");
                hostClient.close();
            } catch (UnSupportHostVersionException e) {
                UpdateManager.handle(hostClient);
            } catch (SlientException ignore) {
                // 静默处理
            }
        }, "HostClient-Handler").start();
    }

    public static HostClient listenAndConfigureHostClient() throws SlientException, IOException {
        SecureSocket hostServerHook = hostServerHookServerSocket.accept();
        String clientAddress = hostServerHook.getInetAddress().getHostAddress();

        if (IPChecker.exec(clientAddress, IPChecker.CHECK_IS_BAN)) {
            InternetOperator.close(hostServerHook);
            InfoBox.sayBanConnectInfo(clientAddress);
            SlientException.throwException();
        }

        InfoBox.sayHostClientTryToConnect(clientAddress, hostServerHook.getPort());

        return new HostClient(hostServerHook);

    }

    public static void handleTransformerServiceWithNewThread(HostClient hostClient) {
        new Thread(() -> {
            while (!hostClient.getClientServerSocket().isClosed()) {
                Socket client;
                try {
                    client = hostClient.getClientServerSocket().accept();
                } catch (IOException e) {
                    debugOperation(e);
                    continue;
                }

                try {
                    InternetOperator.sendCommand(hostClient, "sendSocket" + ";" + InternetOperator.getInternetAddressAndPort(client));
                } catch (Exception e) {
                    InfoBox.sayHostClientDiscInfo(hostClient, "Main");
                    hostClient.close();
                    InternetOperator.close(client);
                    break;
                }

                HostReply hostReply;
                try {
                    hostReply = TransferSocketAdapter.getThisHostClientHostSign(hostClient.getOutPort());
                } catch (IOException e) {
                    InfoBox.sayClientSuccConnectToChaSerButHostClientTimeOut(hostClient);
                    InfoBox.sayKillingClientSideConnection(client);
                    InternetOperator.close(client);
                    continue;
                }

                Transformer.startThread(hostClient, hostReply, client);
                InfoBox.sayClientConnectBuildUpInfo(hostClient, client);
            }
        }, "HostClient-Service").start();
    }

    public static void sayInfo(String str) {
        myConsole.log("Main", str);
    }

    public static void sayInfo(String subject, String str) {
        myConsole.log(subject, str);
    }

    public static void sayError(String str) {
        myConsole.error("Main", str);
    }

    public static void sayError(String subject, String str) {
        myConsole.error(subject, str);
    }

    // 优化后的端口检查方法
    private static int getCurrentAvailableOutPort(SequenceKey sequenceKey) {
        for (int i = sequenceKey.getDyStart(); i <= sequenceKey.getDyEnd(); i++) {
            try (ServerSocket serverSocket = new ServerSocket()) {
                serverSocket.bind(new InetSocketAddress(i), 0);
                return i;
            } catch (IOException ignore) {
                // 端口不可用，继续下一个
            }
        }
        return -1;
    }

    private static void checkHostClientLegitimacyAndTellInfo(HostClient hostClient) throws IOException, NoMorePortException, SlientException, UnRecognizedKeyException, AlreadyBlindPortException, UnSupportHostVersionException, OutDatedKeyException {
        Object[] obj = NeoProxyServer.checkHostClientVersionAndKeyAndLang(hostClient);
        hostClient.enableCheckAliveThread();
        availableHostClient.add(hostClient);
        SequenceKey key = (SequenceKey) obj[0];
        hostClient.setKey(key);
        hostClient.setLangData((LanguageData) obj[1]);
        int port;
        if (hostClient.getKey().getPort() != DYNAMIC_PORT) {
            port = hostClient.getKey().getPort();
        } else {
            port = NeoProxyServer.getCurrentAvailableOutPort(key);
            if (port == -1) {
                NoMorePortException.throwException();
            }
        }
        hostClient.setOutPort(port);
        hostClient.setClientServerSocket(new ServerSocket(port));
        String clientAddress = InternetOperator.getInternetAddressAndPort(hostClient.getHostServerHook());
        sayInfo("HostClient on " + clientAddress + " register successfully!");
        // --- 调用新方法 ---
        printClientRegistrationInfo(hostClient);
        // --- 结束调用 ---
        InternetOperator.sendCommand(hostClient, String.valueOf(port));
        InternetOperator.sendStr(hostClient, hostClient.getLangData().THIS_ACCESS_CODE_HAVE +
                hostClient.getKey().getBalance() + hostClient.getLangData().MB_OF_FLOW_LEFT);
        InternetOperator.sendStr(hostClient, hostClient.getLangData().EXPIRE_AT +
                hostClient.getKey().getExpireTime());
        InternetOperator.sendStr(hostClient, hostClient.getLangData().USE_THE_ADDRESS +
                LOCAL_DOMAIN_NAME + ":" + port + hostClient.getLangData().TO_START_UP_CONNECTION);
        sayInfo("Assigned connection address: " + LOCAL_DOMAIN_NAME + ":" + port);
    }

    private static void printClientRegistrationInfo(HostClient hostClient) {
        String ip = hostClient.getHostServerHook().getInetAddress().getHostAddress();
        String accessCode = hostClient.getKey().getName();

        // --- 新增：获取位置信息 ---
        String location = "N/A";
        if (dbReader != null) {
            try {
                InetAddress ipAddress = InetAddress.getByName(ip);
                CityResponse response = dbReader.city(ipAddress);
                String country = response.getCountry().getName();
                String city = response.getCity().getName();
                // 组合国家和城市，如果城市为空则只显示国家
                if (city != null && !city.isEmpty()) {
                    location = country + ", " + city;
                } else {
                    location = country; // 如果没有城市信息，只显示国家
                }
            } catch (IOException | GeoIp2Exception e) {
                // debugOperation(e); // 可选：记录查询失败
                location = "Lookup failed";
            }
        }
        // --- 结束新增 ---

        // 定义表头和数据
        String[] headers = {"Access Code", "IP Address", "Location"};
        String[] data = {accessCode, ip, location};

        // 计算每列的最大宽度
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = Math.max(headers[i].length(), data[i].length());
        }

        // 构建输出
        StringBuilder output = new StringBuilder();
        output.append("\n"); // Use \n

        // 添加表头分隔线 (┌─┬─┬─┐)
        output.append("┌");
        for (int i = 0; i < widths.length; i++) {
            output.append("─".repeat(widths[i] + 2)); // +2 for padding spaces
            if (i < widths.length - 1) output.append("┬");
        }
        output.append("┐\n"); // Use \n

        // 添加表头 (│ Name │ Balance │ ... │)
        output.append("│");
        for (int i = 0; i < headers.length; i++) {
            output.append(" ").append(String.format("%-" + widths[i] + "s", headers[i])).append(" ");
            output.append("│");
        }
        output.append("\n"); // Use \n

        // 添加表头和数据分隔线 (├─┼─┼─┤)
        output.append("├");
        for (int i = 0; i < widths.length; i++) {
            output.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) output.append("┼");
        }
        output.append("┤\n"); // Use \n

        // 添加数据行 (│ value1 │ value2 │ ... │)
        output.append("│");
        for (int i = 0; i < data.length; i++) {
            output.append(" ").append(String.format("%-" + widths[i] + "s", data[i])).append(" ");
            output.append("│");
        }
        output.append("\n"); // Use \n

        // 添加底部边框 (└─┴─┴─┘)
        output.append("└");
        for (int i = 0; i < widths.length; i++) {
            output.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) output.append("┴");
        }
        output.append("┘"); // No \n at the very end if not desired

        sayInfo(output.toString()); // Output the formatted table
    }

    private static Object[] checkHostClientVersionAndKeyAndLang(HostClient hostClient)
            throws IOException, UnSupportHostVersionException, UnRecognizedKeyException,
            AlreadyBlindPortException, IndexOutOfBoundsException, OutDatedKeyException {

        String hostClientInfo = InternetOperator.receiveStr(hostClient);
        if (hostClientInfo == null || hostClientInfo.isEmpty()) {
            UnSupportHostVersionException.throwException("_NULL_", hostClient);
        }

        assert hostClientInfo != null;
        String[] info = hostClientInfo.split(";");
        if (info.length != 3) {
            UnSupportHostVersionException.throwException("_NULL_", hostClient);
        }

        LanguageData languageData = "zh".equals(info[0]) ?
                LanguageData.getChineseLanguage() : new LanguageData();

        if (!availableVersions.contains(info[1])) {
            InternetOperator.sendStr(hostClient, languageData.UNSUPPORTED_VERSION_MSG + EXPECTED_CLIENT_VERSION);
            UnSupportHostVersionException.throwException(info[1], hostClient);
        }

        SequenceKey currentSequenceKey = SequenceKey.getEnabledKeyFromDB(info[2]);
        if (currentSequenceKey == null) {
            InternetOperator.sendStr(hostClient, languageData.ACCESS_DENIED_FORCE_EXITING);
            hostClient.close();
            UnRecognizedKeyException.throwException(info[2]);
        }

        assert currentSequenceKey != null;
        if (currentSequenceKey.getPort() != DYNAMIC_PORT) {
            try (ServerSocket testSocket = new ServerSocket()) {
                testSocket.bind(new InetSocketAddress(currentSequenceKey.getPort()), 0);
            } catch (IOException e) {
                InternetOperator.sendStr(hostClient, languageData.THE_PORT_HAS_ALREADY_BLIND);
                AlreadyBlindPortException.throwException(currentSequenceKey.getPort());
            }
        } else {
            int i = getCurrentAvailableOutPort(currentSequenceKey);
            if (i == -1) {
                InternetOperator.sendStr(hostClient, languageData.THE_PORT_HAS_ALREADY_BLIND);
                AlreadyBlindPortException.throwException(currentSequenceKey.getDyStart(), currentSequenceKey.getDyEnd());
            }
        }

        if (currentSequenceKey.isOutOfDate()) {
            InternetOperator.sendStr(hostClient, languageData.KEY + info[2] + languageData.ARE_OUT_OF_DATE);
            OutDatedKeyException.throwException(currentSequenceKey);
        }

        InternetOperator.sendStr(hostClient, languageData.CONNECTION_BUILD_UP_SUCCESSFULLY);
        return new Object[]{currentSequenceKey, languageData};
    }

    public static void debugOperation(Exception e) {
        if (IS_DEBUG_MODE) {
            String exceptionMsg = StringUtils.getExceptionMsg(e);
            myConsole.error("Debugger", exceptionMsg, e);
        }
    }

    //关闭方法
    private static void shutdown() {
        sayInfo("Shutting down the NeoProxyServer...");

        // --- 新增：关闭 GeoIP2 DatabaseReader ---
        if (dbReader != null) {
            try {
                dbReader.close();
            } catch (IOException e) {
                debugOperation(e);
            }
        }
        // --- 结束新增 ---

        isStopped = true;
        // 关闭所有 HostClient 连接
        for (HostClient hostClient : availableHostClient) {
            try {
                hostClient.close();
            } catch (Exception e) {
                debugOperation(e);
            }
        }
        availableHostClient.clear();
        // 关闭服务器套接字
        try {
            if (hostServerHookServerSocket != null && !hostServerHookServerSocket.isClosed()) {
                hostServerHookServerSocket.close();
            }
        } catch (IOException e) {
            debugOperation(e);
        }
        try {
            if (hostServerTransferServerSocket != null && !hostServerTransferServerSocket.isClosed()) {
                hostServerTransferServerSocket.close();
            }
        } catch (IOException e) {
            debugOperation(e);
        }
        sayInfo("NeoProxyServer shutdown completed.");
    }
}