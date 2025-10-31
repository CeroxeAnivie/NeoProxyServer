package neoproject.neoproxy;

import neoproject.neoproxy.core.*;
import neoproject.neoproxy.core.exceptions.*;
import neoproject.neoproxy.core.management.*;
import neoproject.neoproxy.core.threads.TCPTransformer;
import neoproject.neoproxy.core.threads.UDPTransformer;
import plethora.management.bufferedFile.SizeCalculator;
import plethora.net.SecureServerSocket;
import plethora.net.SecureSocket;
import plethora.utils.ArrayUtils;
import plethora.utils.MyConsole;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import static neoproject.neoproxy.core.InternetOperator.close;
import static neoproject.neoproxy.core.ServerLogger.alert;
import static neoproject.neoproxy.core.management.IPChecker.loadBannedIPs;
import static neoproject.neoproxy.core.management.SequenceKey.DYNAMIC_PORT;
import static neoproject.neoproxy.core.management.SequenceKey.initKeyDatabase;
import static neoproject.neoproxy.core.threads.TCPTransformer.BUFFER_LEN;

public class NeoProxyServer {
    public static final String CURRENT_DIR_PATH = String.valueOf(getJarDirectory());
    public static final CopyOnWriteArrayList<HostClient> availableHostClient = new CopyOnWriteArrayList<>();
    public static String VERSION = getAppVersion();
    public static String EXPECTED_CLIENT_VERSION = "4.7.0|4.7.1|4.7.2|4.7.3";//from old to new versions
    public static final CopyOnWriteArrayList<String> availableVersions = ArrayUtils.stringArrayToList(EXPECTED_CLIENT_VERSION.split("\\|"));
    public static int HOST_HOOK_PORT = 44801;
    public static int HOST_CONNECT_PORT = 44802;
    public static String LOCAL_DOMAIN_NAME = "localhost";
    public static SecureServerSocket hostServerHookServerSocket = null;
    public static SecureServerSocket hostServerTransferServerSocket = null;
    public static boolean IS_DEBUG_MODE = false;
    public static MyConsole myConsole;
    public static boolean isStopped = false;

    private static String getAppVersion() {
        Properties props = new Properties();
        try (InputStream is = NeoProxyServer.class.getClassLoader()
                .getResourceAsStream("app.properties")) {
            if (is == null) {
                throw new IllegalStateException(
                        "Missing resource: " + "app.properties" + " (ensure it's in src/main/resources)");
            }
            props.load(is); // Java 9+ 默认 UTF-8，安全读取中文/特殊字符
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + "app.properties", e);
        }

        String version = props.getProperty("app.version");
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Property '" + "app.version" + "' is missing or empty in " + "app.properties");
        }

        return version.trim();
    }

    public static void initConsole() {
        ConsoleManager.init();
    }

    public static void initStructure() {
        try {//处理eula，这是头等大事
            copyResourceToJarDirectory("eula.txt");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-2);
        }
        // Initialization order is clearer
        initConsole();           // 1. Console system
        printLogo();
        initKeyDatabase();       // 2. Database
        ConfigOperator.readAndSetValue(); // 3. Configuration
        UpdateManager.init();    // 4. Update manager
        SecureSocket.setMaxAllowedPacketSize((int) SizeCalculator.mibToByte(200)); // 5. Set max packet size to 200m

        // 5. Network services
        try {
            hostServerHookServerSocket = new SecureServerSocket(HOST_HOOK_PORT);
            // Start the unified TransferSocketAdapter, which can handle both TCP and UDP
            TransferSocketAdapter.startThread();
        } catch (IOException e) {
            debugOperation(e);
            ServerLogger.error("neoProxyServer.canNotBindPort");
            System.exit(-1);
        }
        loadBannedIPs();
    }

    private static void checkARGS(String[] args) {
        for (String arg : args) {
            switch (arg) {
                case "--debug" -> IS_DEBUG_MODE = true;
                case "--zh-cn" -> ServerLogger.setLocale(Locale.SIMPLIFIED_CHINESE);
                case "--en-us" -> ServerLogger.setLocale(Locale.US);
                default -> {
                    // 忽略未知参数，但可以在这里添加提示
                    if (IS_DEBUG_MODE) {
                        System.err.println("Debug: Ignoring unknown argument: " + arg);
                    }
                }
            }
        }
    }

    private static void printLogo() {
        ServerLogger.info("neoProxyServer.logo");
        myConsole.log("NeoProxyServer", """
                
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
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(NeoProxyServer::shutdown));

        NeoProxyServer.checkARGS(args);
        NeoProxyServer.initStructure();

        ServerLogger.info("neoProxyServer.currentLogFile", myConsole.getLogFile().getAbsolutePath());
        ServerLogger.info("neoProxyServer.localDomainName", LOCAL_DOMAIN_NAME);
        ServerLogger.info("neoProxyServer.listenHostConnectPort", HOST_CONNECT_PORT);
        ServerLogger.info("neoProxyServer.listenHostHookPort", HOST_HOOK_PORT);
        ServerLogger.info("consoleManager.currentServerVersion",VERSION, EXPECTED_CLIENT_VERSION);

        while (!isStopped) {
            try {
                HostClient hostClient = listenAndConfigureHostClient();
                handleNewHostClient(hostClient);
            } catch (IOException e) {
                debugOperation(e);
                if (!isStopped) {
                    if (alert){
                        ServerLogger.info("neoProxyServer.clientConnectButFail");
                    }
                } else {
                    break;
                }
            } catch (SlientException ignored) {
                // Silent exception, continue loop
            }
        }
    }

    //handle new connected HostClient
    private static void handleNewHostClient(HostClient hostClient) {
        new Thread(() -> {
            try {
                NeoProxyServer.checkHostClientLegitimacyAndTellInfo(hostClient);
                NeoProxyServer.handleTransformerServiceWithNewThread(hostClient);
            } catch (IndexOutOfBoundsException | IOException |
                     NoMorePortException |
                     AlreadyBlindPortException | UnRecognizedKeyException | OutDatedKeyException e) {
                ServerLogger.sayHostClientDiscInfo(hostClient, "NeoProxyServer");
                hostClient.close();
            } catch (UnSupportHostVersionException e) {
                UpdateManager.handle(hostClient);
            } catch (SlientException ignore) {
                // Silent handling
            }
        }, "HostClient-Handler").start();
    }

    public static HostClient listenAndConfigureHostClient() throws SlientException, IOException {
        SecureSocket hostServerHook = hostServerHookServerSocket.accept();
        if (hostServerHook == null) {//获得了封禁ip的socket
            SlientException.throwException();
        }
        String clientAddress = hostServerHook.getInetAddress().getHostAddress();

        if (IPChecker.exec(clientAddress, IPChecker.CHECK_IS_BAN)) {
            close(hostServerHook);
            if (alert) {
                ServerLogger.info("neoProxyServer.banConnectInfo", clientAddress);
            }
            SlientException.throwException();
        }

        ServerLogger.info("neoProxyServer.clientTryToConnect", clientAddress, hostServerHook.getPort());

        return new HostClient(hostServerHook);

    }

    public static void handleTransformerServiceWithNewThread(HostClient hostClient) {
        //tcp
        new Thread(() -> {
            while (!hostClient.isStopped()) {
                Socket client;
                try {
                    client = hostClient.getClientServerSocket().accept();
                    if (IPChecker.exec(client.getInetAddress().getHostAddress(),IPChecker.CHECK_IS_BAN)){
                        client.close();
                        continue;
                    }
                } catch (IOException e) {
                    debugOperation(e);
                    continue;
                }

                try {
                    InternetOperator.sendCommand(hostClient, "sendSocket;" + InternetOperator.getInternetAddressAndPort(client));
                } catch (Exception e) {
                    ServerLogger.sayHostClientDiscInfo(hostClient, "NeoProxyServer");
                    hostClient.close();
                    close(client);
                    break;
                }

                HostReply hostReply;
                try {
                    hostReply = TransferSocketAdapter.getHostReply(hostClient.getOutPort(), TransferSocketAdapter.CONN_TYPE.TCP);
                } catch (SocketTimeoutException e) {
                    ServerLogger.sayClientSuccConnectToChaSerButHostClientTimeOut(hostClient);
                    ServerLogger.sayKillingClientSideConnection(client);
                    close(client);
                    continue;
                }

                TCPTransformer.startThread(hostClient, hostReply, client);
                ServerLogger.sayClientTCPConnectBuildUpInfo(hostClient, client);
            }
        }, "HostClient-TCP-Service").start();
        //udp
        new Thread(() -> {
            DatagramSocket datagramSocket = hostClient.getClientDatagramSocket();
            while (!datagramSocket.isClosed()) {
                byte[] buffer = new byte[BUFFER_LEN];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                try {
                    datagramSocket.receive(datagramPacket); // 【唯一的 receive 点】
                } catch (IOException e) {
                    debugOperation(e);
                    continue;
                }

                final String clientIP = datagramPacket.getAddress().getHostAddress();
                final int clientOutPort = datagramPacket.getPort();

                // Use synchronized block to prevent concurrency issues
                synchronized (UDPTransformer.udpClientConnections) {
                    // Check if a forwarding channel for this clientIP and port already exists
                    UDPTransformer existingReply = null;
                    for (UDPTransformer reply : UDPTransformer.udpClientConnections) {
                        if (reply.getClientOutPort() == clientOutPort && reply.getClientIP().equals(clientIP) && reply.isRunning()) {
                            existingReply = reply;
                            break;
                        }
                    }

                    if (existingReply != null) {
                        // If it exists, serialize the packet and add it to its send queue
                        byte[] serializedData = UDPTransformer.serializeDatagramPacket(datagramPacket);
                        existingReply.addPacketToSend(serializedData);
                    } else {
                        // If it does not exist, create a new forwarding channel
                        new Thread(() -> {
                            try {
                                // 1. Notify client A to create a new UDP connection
                                InternetOperator.sendCommand(hostClient, "sendSocketUDP;" + InternetOperator.getInternetAddressAndPort(datagramPacket));

                                // Timeout will throw an exception SocketTimeoutException
                                HostReply hostReply;
                                try {
                                    hostReply = TransferSocketAdapter.getHostReply(hostClient.getOutPort(), TransferSocketAdapter.CONN_TYPE.UDP);
                                } catch (SocketTimeoutException e) {// 此时后端S离线，A没有发 SecureSocket 给B
                                    ServerLogger.sayClientSuccConnectToChaSerButHostClientTimeOut(hostClient);
                                    //直接丢弃 UDP 包，什么也不管
                                    return;
                                }

                                UDPTransformer newUdpTransformer = new UDPTransformer(hostClient, hostReply, datagramSocket, clientIP, clientOutPort);
                                UDPTransformer.udpClientConnections.add(newUdpTransformer);
                                new Thread(newUdpTransformer, "UDP-Transformer-" + clientOutPort).start();

                                // 5. 【关键】将触发创建的第一个包加入它的发送队列
                                byte[] firstData = UDPTransformer.serializeDatagramPacket(datagramPacket);
                                newUdpTransformer.addPacketToSend(firstData);

                                // 6. Record successful connection establishment
                                ServerLogger.sayClientUDPConnectBuildUpInfo(hostClient, datagramPacket);

                            } catch (Exception e) {
                                debugOperation(e);
                            }
                        }, "UDP-Create-New-" + clientOutPort).start();
                    }
                }
            }
        }, "HostClient-UDP-Service").start();
    }

    private static int getCurrentAvailableOutPort(SequenceKey sequenceKey) {
        for (int i = sequenceKey.getDyStart(); i <= sequenceKey.getDyEnd(); i++) {
            boolean tcpAvailable;
            boolean udpAvailable;

            // 检查TCP端口可用性
            try (ServerSocket serverSocket = new ServerSocket()) {
                serverSocket.bind(new InetSocketAddress(i), 0);
                tcpAvailable = true;
            } catch (IOException ignore) {
                // TCP端口不可用，继续检查下一个端口
                continue;
            }

            // 检查UDP端口可用性
            try (DatagramSocket datagramSocket = new DatagramSocket(i)) {
                udpAvailable = true;
            } catch (IOException ignore) {
                // UDP端口不可用，继续检查下一个端口
                continue;
            }

            // 只有当TCP和UDP都可用时，才返回该端口
            if (tcpAvailable && udpAvailable) {
                return i;
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
        hostClient.setClientServerSocket(new ServerSocket(port));//tcp
        hostClient.setClientDatagramSocket(new DatagramSocket(port));//udp
        String clientAddress = InternetOperator.getInternetAddressAndPort(hostClient.getHostServerHook());
        ServerLogger.info("neoProxyServer.hostClientRegisterSuccess", clientAddress);
        printClientRegistrationInfo(hostClient);
        InternetOperator.sendCommand(hostClient, String.valueOf(port));
        InternetOperator.sendStr(hostClient, hostClient.getLangData().THIS_ACCESS_CODE_HAVE +
                hostClient.getKey().getBalance() + hostClient.getLangData().MB_OF_FLOW_LEFT);
        InternetOperator.sendStr(hostClient, hostClient.getLangData().EXPIRE_AT +
                hostClient.getKey().getExpireTime());
        InternetOperator.sendStr(hostClient, hostClient.getLangData().USE_THE_ADDRESS +
                LOCAL_DOMAIN_NAME + ":" + port + hostClient.getLangData().TO_START_UP_CONNECTION);
        ServerLogger.info("neoProxyServer.assignedConnectionAddress", LOCAL_DOMAIN_NAME + ":" + port);
    }

    /**
     * 打印客户端注册信息，并缓存地理位置信息。
     *
     * @param hostClient 新注册的HostClient
     */
    private static void printClientRegistrationInfo(HostClient hostClient) {
        String ip = hostClient.getHostServerHook().getInetAddress().getHostAddress();
        String accessCode = hostClient.getKey().getName();

        IPGeolocationHelper.LocationInfo locInfo = IPGeolocationHelper.getLocationInfo(ip);
        String location = locInfo.location();
        String isp = locInfo.isp();

        hostClient.setCachedLocation(location);
        hostClient.setCachedISP(isp);

        ConsoleManager.printClientRegistrationTable(accessCode, ip, location, isp);
    }

    private static Object[] checkHostClientVersionAndKeyAndLang(HostClient hostClient)
            throws IOException, UnSupportHostVersionException, UnRecognizedKeyException,
            AlreadyBlindPortException, IndexOutOfBoundsException, OutDatedKeyException {

        String hostClientInfo = InternetOperator.receiveStr(hostClient);
        if (hostClientInfo == null || hostClientInfo.isEmpty()) {
            UnSupportHostVersionException.throwException(hostClient.getIP(),"_NULL_");
        }

        String[] info = hostClientInfo.split(";");
        if (info.length != 3) {
            UnSupportHostVersionException.throwException(hostClient.getIP(),"_NULL_");
        }

        LanguageData languageData = "zh".equals(info[0]) ?
                LanguageData.getChineseLanguage() : new LanguageData();

        if (!availableVersions.contains(info[1])) {
            InternetOperator.sendStr(hostClient, languageData.UNSUPPORTED_VERSION_MSG + availableVersions.getLast());
            UnSupportHostVersionException.throwException(hostClient.getIP(),info[1]);
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

    // MODIFIED: Use ServerLogger
    public static void debugOperation(Exception e) {
        if (IS_DEBUG_MODE) {
            ServerLogger.error("neoProxyServer.debugOperation", e, e.getMessage());
            e.printStackTrace();
        }
    }

    private static void shutdown() {
        ServerLogger.info("neoProxyServer.shuttingDown");

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
        ServerLogger.info("neoProxyServer.shutdownCompleted");
    }

    //copy EULA
    public static Path copyResourceToJarDirectory(String resourcePath) throws IOException {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("Resource path cannot be null or blank.");
        }

        // 1. 获取资源的输入流
        // 使用当前类的类加载器来查找资源，这是最可靠的方式
        ClassLoader classLoader = NeoProxyServer.class.getClassLoader();
        try (InputStream resourceStream = classLoader.getResourceAsStream(resourcePath)) {
            if (resourceStream == null) {
                throw new IOException("Resource not found in classpath: " + resourcePath);
            }

            // 2. 定位当前 JAR 文件的位置
            Path targetDirectory = getJarDirectory();

            // 3. 构建目标文件的完整路径
            // Paths.get("a/b/c", "d.txt") 会正确处理路径分隔符
            Path targetFile = targetDirectory.resolve(Paths.get(resourcePath).getFileName());

            // 4. 确保目标文件的父目录存在（如果资源路径包含子目录）
            // 例如，如果资源是 "config/app.properties"，我们需要确保 "config" 目录存在
            Path parentDir = targetFile.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // 5. 执行拷贝操作
            // REPLACE_EXISTING 选项会覆盖已存在的同名文件
            Files.copy(resourceStream, targetFile, StandardCopyOption.REPLACE_EXISTING);

            return targetFile;
        }
    }

    /**
     * 获取当前运行的 JAR 文件所在的目录。
     *
     * @return JAR 文件所在的目录 {@link Path}。
     */
    private static Path getJarDirectory(){
        try {
            // 获取当前类的 ProtectionDomain
            ProtectionDomain domain = NeoProxyServer.class.getProtectionDomain();
            if (domain == null) {
                throw new IOException("ProtectionDomain is null. Cannot determine JAR location.");
            }

            // 获取 CodeSource，它包含了类的来源位置（通常是 JAR 文件）
            CodeSource codeSource = domain.getCodeSource();
            if (codeSource == null) {
                throw new IOException("CodeSource is null. Cannot determine JAR location.");
            }

            // 获取位置的 URL
            URL location = codeSource.getLocation();
            if (location == null) {
                throw new IOException("CodeSource location is null. Cannot determine JAR location.");
            }

            // 将 URL 转换为 Path
            // location.toURI() 能正确处理路径中的空格和特殊字符
            Path jarPath = Paths.get(location.toURI());

            // 返回 JAR 文件的父目录
            Path parentDir = jarPath.getParent();
            if (parentDir == null) {
                // 这在 JAR 位于文件系统根目录时极不可能发生，但作为一种防御性检查
                throw new IOException("Cannot determine parent directory of JAR file: " + jarPath);
            }
            return parentDir;
        } catch (Exception e) {
            e.printStackTrace();
            return Path.of(System.getProperty("user.dir"));
        }
    }
}