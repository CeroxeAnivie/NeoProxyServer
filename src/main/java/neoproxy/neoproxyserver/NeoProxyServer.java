package neoproxy.neoproxyserver;

import neoproxy.neoproxyserver.core.*;
import neoproxy.neoproxyserver.core.exceptions.*;
import neoproxy.neoproxyserver.core.management.*;
import neoproxy.neoproxyserver.core.threads.TCPTransformer;
import neoproxy.neoproxyserver.core.threads.UDPTransformer;
import neoproxy.neoproxyserver.core.webadmin.WebAdminManager;
import plethora.management.bufferedFile.SizeCalculator;
import plethora.net.SecureServerSocket;
import plethora.net.SecureSocket;
import plethora.security.AtomicIdGenerator;
import plethora.thread.ThreadManager;
import plethora.utils.ArrayUtils;
import plethora.utils.MyConsole;
import plethora.utils.Sleeper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
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
import java.util.concurrent.locks.ReentrantLock;

import static neoproxy.neoproxyserver.core.HostClient.waitForTcpEnabled;
import static neoproxy.neoproxyserver.core.HostClient.waitForUDPEnabled;
import static neoproxy.neoproxyserver.core.InternetOperator.*;
import static neoproxy.neoproxyserver.core.ServerLogger.alert;
import static neoproxy.neoproxyserver.core.management.IPChecker.loadBannedIPs;
import static neoproxy.neoproxyserver.core.management.SequenceKey.DYNAMIC_PORT;
import static neoproxy.neoproxyserver.core.management.SequenceKey.initKeyDatabase;
import static neoproxy.neoproxyserver.core.threads.TCPTransformer.BUFFER_LEN;

/**
 * NeoProxyServer (Java 21 Virtual Threads Compatible)
 * <p>
 * 修改说明：
 * 1. 移除了所有 synchronized 关键字，防止虚拟线程 Pinning。
 * 2. 引入 UDP_GLOBAL_LOCK (ReentrantLock) 保护 UDPTransformer 连接列表。
 */
public class NeoProxyServer {
    public static final String CURRENT_DIR_PATH = getJarDirOrUserDir();
    public static final CopyOnWriteArrayList<HostClient> availableHostClient = new CopyOnWriteArrayList<>();
    public static final String ASCII_LOGO = """
            
               _____                                    \s
              / ____|                                   \s
             | |        ___   _ __    ___   __  __   ___\s
             | |       / _ \\ | '__|  / _ \\  \\ \\/ /  / _ \\
             | |____  |  __/ | |    | (_) |  >  <  |  __/
              \\_____|  \\___| |_|     \\___/  /_/\\_\\  \\___|
                                                        \s
                                                         \
            """;
    // 【新增】全局 UDP 连接锁，替代 synchronized (UDPTransformer.udpClientConnections)
    private static final ReentrantLock UDP_GLOBAL_LOCK = new ReentrantLock();
    public static String VERSION = getFromAppProperties("app.version");
    public static String EXPECTED_CLIENT_VERSION = getFromAppProperties("app.expected.client.version");
    public static final CopyOnWriteArrayList<String> availableVersions = ArrayUtils.toCopyOnWriteArrayListWithLoop(EXPECTED_CLIENT_VERSION.split("\\|"));
    public static int HOST_HOOK_PORT = 44801;
    public static int HOST_CONNECT_PORT = 44802;
    public static String LOCAL_DOMAIN_NAME = "localhost";
    public static SecureServerSocket hostServerHookServerSocket = null;
    public static SecureServerSocket hostServerTransferServerSocket = null;
    public static boolean IS_DEBUG_MODE = false;
    public static MyConsole myConsole;
    public static boolean isStopped = false;

    private static String getFromAppProperties(String name) {
        Properties props = new Properties();
        try (InputStream is = NeoProxyServer.class.getClassLoader()
                .getResourceAsStream("app.properties")) {
            if (is == null) {
                throw new IllegalStateException(
                        "Missing resource: " + "app.properties" + " (ensure it's in src/main/resources)");
            }
            props.load(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + "app.properties", e);
        }

        String version = props.getProperty(name);
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Property '" + name + "' is missing or empty in " + "app.properties");
        }

        return version.trim();
    }

    public static void initStructure() {
        try {
            copyResourceToJarDirectory("eula.txt");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-2);
        }

        //以下顺序不能错
        ConsoleManager.init(); // 初始化控制台
        ConfigOperator.readAndSetValue(); // 读取配置 (此时会加载 WEB_ADMIN_PORT)
        WebAdminManager.init(); // 使用配置中的端口启动 WebAdmin
        printLogo();
        initKeyDatabase();
        UpdateManager.init();
        SecureSocket.setMaxAllowedPacketSize((int) SizeCalculator.mibToByte(200));

        try {
            hostServerHookServerSocket = new SecureServerSocket(HOST_HOOK_PORT);
            TransferSocketAdapter.startThread();
        } catch (IOException e) {
            debugOperation(e);
            ServerLogger.error("neoProxyServer.canNotBindPort");
            System.exit(-1);
        }
        loadBannedIPs();

        WebAdminManager.init();
    }

    private static void checkARGS(String[] args) {
        for (String arg : args) {
            switch (arg) {
                case "--debug" -> IS_DEBUG_MODE = true;
                case "--zh-cn" -> ServerLogger.setLocale(Locale.SIMPLIFIED_CHINESE);
                case "--en-us" -> ServerLogger.setLocale(Locale.US);
                default -> {
                    if (IS_DEBUG_MODE) {
                        System.err.println("Debug: Ignoring unknown argument: " + arg);
                    }
                }
            }
        }
    }

    private static void printLogo() {
        ServerLogger.info("neoProxyServer.logo");
        myConsole.log("NeoProxyServer", ASCII_LOGO);
    }

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(NeoProxyServer::shutdown));

        NeoProxyServer.checkARGS(args);
        NeoProxyServer.initStructure();

        ServerLogger.info("neoProxyServer.currentLogFile", myConsole.getLogFile().getAbsolutePath());
        ServerLogger.info("neoProxyServer.localDomainName", LOCAL_DOMAIN_NAME);
        ServerLogger.info("neoProxyServer.listenHostConnectPort", HOST_CONNECT_PORT);
        ServerLogger.info("neoProxyServer.listenHostHookPort", HOST_HOOK_PORT);
        ServerLogger.info("consoleManager.currentServerVersion", VERSION, EXPECTED_CLIENT_VERSION);

        while (!isStopped) {
            HostClient hostClient;
            try {
                hostClient = listenAndConfigureHostClient();
                handleNewHostClient(hostClient);
            } catch (IOException e) {
                debugOperation(e);
                if (!isStopped) {
                    if (alert) {
                        String exceptionMsg = e.getMessage();
                        if (exceptionMsg.contains("Handshake failed from")) {
                            ServerLogger.info("neoProxyServer.clientConnectButFail", exceptionMsg.split("from")[1]);
                        } else {
                            ServerLogger.info("neoProxyServer.clientConnectButFail", "_UNKNOWN_");
                        }
                    }
                } else {
                    break;
                }
            } catch (SlientException ignored) {
            }
        }
    }

    private static void handleNewHostClient(HostClient hostClient) {
        ThreadManager.runAsync(() -> {
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
            }
        });
    }

    public static HostClient listenAndConfigureHostClient() throws SlientException, IOException {
        SecureSocket hostServerHook = hostServerHookServerSocket.accept();
        if (hostServerHook == null) {
            SlientException.throwException();
        }
        hostServerHook.setSoTimeout(TransferSocketAdapter.SO_TIMEOUT);

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
        // 【TCP 服务线程】
        ThreadManager.runAsync(() -> {
            while (!hostClient.isStopped()) {
                Socket client;
                try {
                    client = hostClient.getClientServerSocket().accept();
                    if (IPChecker.exec(client.getInetAddress().getHostAddress(), IPChecker.CHECK_IS_BAN)) {
                        client.close();
                        continue;
                    }
                } catch (IOException | NullPointerException e) {
                    if (hostClient.isTCPEnabled()) {
                        debugOperation(e);
                    }
                    waitForTcpEnabled(hostClient);
                    continue;
                }

                ThreadManager.runAsync(() -> {
                    try {
                        // =========================================================
                        // 【核心策略：延迟 + 无损嗅探】
                        // =========================================================

                        // 1. 延迟 200ms。
                        Sleeper.sleep(200);

                        // 2. 准备 "无损嗅探"
                        PushbackInputStream pbis = new PushbackInputStream(client.getInputStream(), 1);

                        // 设置 1ms 的极短超时，用于探测
                        int originalTimeout = client.getSoTimeout();
                        client.setSoTimeout(1);

                        try {
                            int b = pbis.read();
                            if (b == -1) {
                                // 读到了 EOF，说明对方已经断开了！
                                client.close();
                                return;
                            }
                            // 读到了数据（比如 RDP 的 Hello 包）
                            pbis.unread(b);

                        } catch (SocketTimeoutException e) {
                            // 超时了：说明连接活着，但是没发数据。
                            // 无法区分 TCPing 还是被动协议，放行。
                        } catch (IOException e) {
                            // 读出错（比如 Connection Reset），说明是 TCPing
                            client.close();
                            return;
                        } finally {
                            // 恢复超时设置
                            client.setSoTimeout(originalTimeout);
                        }

                        // =========================================================
                        // 验证通过，通知内网
                        // =========================================================

                        long socketID = AtomicIdGenerator.GLOBAL.nextId();
                        // 此时调用，无需全局锁 (依赖 SecureSocket 写锁)
                        sendCommand(hostClient, "sendSocketTCP;" + socketID + ";" + getInternetAddressAndPort(client));
                        hostClient.refreshHeartbeat();

                        HostReply hostReply;
                        try {
                            hostReply = TransferSocketAdapter.getHostReply(socketID, TransferSocketAdapter.CONN_TYPE.TCP);
                        } catch (SocketTimeoutException e) {
                            ServerLogger.sayClientSuccConnectToChaSerButHostClientTimeOut(hostClient);
                            ServerLogger.sayKillingClientSideConnection(client);
                            close(client);
                            return;
                        }

                        // 【关键】把包含 "推回数据" 的流传给 Transformer
                        TCPTransformer.start(hostClient, hostReply, client, pbis);
                        ServerLogger.sayClientTCPConnectBuildUpInfo(hostClient, client);

                    } catch (Exception e) {
                        debugOperation(e);
                        close(client);
                    }
                });
            }
        });

        // 【UDP 服务线程】
        ThreadManager.runAsync(() -> {
            while (!hostClient.isStopped()) {
                byte[] buffer = new byte[BUFFER_LEN];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                DatagramSocket datagramSocket = hostClient.getClientDatagramSocket();

                try {
                    datagramSocket.receive(datagramPacket);
                } catch (IOException | NullPointerException e) {
                    if (hostClient.isUDPEnabled()) {
                        debugOperation(e);
                    }
                    waitForUDPEnabled(hostClient);
                    continue;
                }

                final String clientIP = datagramPacket.getAddress().getHostAddress();
                final int clientOutPort = datagramPacket.getPort();

                // 【核心修改】替换 synchronized (UDPTransformer.udpClientConnections) 为 ReentrantLock
                UDPTransformer existingReply = null;
                UDP_GLOBAL_LOCK.lock();
                try {
                    for (UDPTransformer reply : UDPTransformer.udpClientConnections) {
                        if (reply.getClientOutPort() == clientOutPort && reply.getClientIP().equals(clientIP) && reply.isRunning()) {
                            existingReply = reply;
                            break;
                        }
                    }

                    if (existingReply != null) {
                        // 已有会话，直接转发数据
                        byte[] serializedData = UDPTransformer.serializeDatagramPacket(datagramPacket);
                        existingReply.addPacketToSend(serializedData);
                    }
                } finally {
                    UDP_GLOBAL_LOCK.unlock();
                }

                // 如果未找到现有会话，则创建新会话（放在锁外进行 IO 操作，避免长时间持有锁）
                if (existingReply == null) {
                    ThreadManager.runAsync(() -> {
                        try {
                            long socketID = AtomicIdGenerator.GLOBAL.nextId();

                            // SecureSocket 现已线程安全，直接发送指令，互不阻塞
                            sendCommand(hostClient, "sendSocketUDP;" + socketID + ";" + getInternetAddressAndPort(datagramPacket));
                            hostClient.refreshHeartbeat();

                            HostReply hostReply;
                            try {
                                hostReply = TransferSocketAdapter.getHostReply(socketID, TransferSocketAdapter.CONN_TYPE.UDP);
                            } catch (SocketTimeoutException e) {
                                ServerLogger.sayClientSuccConnectToChaSerButHostClientTimeOut(hostClient);
                                return;
                            }

                            UDPTransformer newUdpTransformer = new UDPTransformer(hostClient, hostReply, datagramSocket, clientIP, clientOutPort);

                            // 【核心修改】再次加锁，将新连接加入列表
                            UDP_GLOBAL_LOCK.lock();
                            try {
                                UDPTransformer.udpClientConnections.add(newUdpTransformer);
                            } finally {
                                UDP_GLOBAL_LOCK.unlock();
                            }

                            ThreadManager.runAsync(newUdpTransformer);

                            byte[] firstData = UDPTransformer.serializeDatagramPacket(datagramPacket);
                            newUdpTransformer.addPacketToSend(firstData);

                            ServerLogger.sayClientUDPConnectBuildUpInfo(hostClient, datagramPacket);

                        } catch (Exception e) {
                            debugOperation(e);
                        }
                    });
                }
            }
        });
    }

    private static int getCurrentAvailableOutPort(SequenceKey sequenceKey) {
        for (int i = sequenceKey.getDyStart(); i <= sequenceKey.getDyEnd(); i++) {
            try (ServerSocket serverSocket = new ServerSocket()) {
                serverSocket.bind(new InetSocketAddress(i), 0);
            } catch (IOException ignore) {
                continue;
            }
            try {
                DatagramSocket testU = new DatagramSocket(i);
                testU.close();
            } catch (IOException ignore) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static void checkHostClientLegitimacyAndTellInfo(HostClient hostClient) throws IOException, NoMorePortException, SlientException, UnRecognizedKeyException, AlreadyBlindPortException, UnSupportHostVersionException, OutDatedKeyException {
        NeoProxyServer.checkHostClientVersionAndKeyAndLang(hostClient);

        int port;
        if (hostClient.getKey().getPort() != DYNAMIC_PORT) {
            port = hostClient.getKey().getPort();
        } else {
            port = NeoProxyServer.getCurrentAvailableOutPort(hostClient.getKey());
            if (port == -1) {
                NoMorePortException.throwException();
            }
        }

        hostClient.setOutPort(port);
        if (hostClient.isTCPEnabled()) {
            hostClient.setClientServerSocket(new ServerSocket(port));
        }
        if (hostClient.isUDPEnabled()) {
            hostClient.setClientDatagramSocket(new DatagramSocket(port));
        }

        String clientAddress = InternetOperator.getInternetAddressAndPort(hostClient.getHostServerHook());
        ServerLogger.info("neoProxyServer.hostClientRegisterSuccess", clientAddress);
        printClientRegistrationInfo(hostClient);

        // 此处不需要加锁，因为是初始化阶段，单线程执行
        InternetOperator.sendCommand(hostClient, String.valueOf(port));
        InternetOperator.sendStr(hostClient, hostClient.getLangData().THIS_ACCESS_CODE_HAVE +
                hostClient.getKey().getBalance() + hostClient.getLangData().MB_OF_FLOW_LEFT);
        InternetOperator.sendStr(hostClient, hostClient.getLangData().EXPIRE_AT +
                hostClient.getKey().getExpireTime());
        InternetOperator.sendStr(hostClient, hostClient.getLangData().USE_THE_ADDRESS +
                LOCAL_DOMAIN_NAME + ":" + port + hostClient.getLangData().TO_START_UP_CONNECTION);
        ServerLogger.info("neoProxyServer.assignedConnectionAddress", LOCAL_DOMAIN_NAME + ":" + port);
    }

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

    private static void checkHostClientVersionAndKeyAndLang(HostClient hostClient)
            throws IOException, UnSupportHostVersionException, UnRecognizedKeyException,
            AlreadyBlindPortException, IndexOutOfBoundsException, OutDatedKeyException {

        String hostClientInfo = InternetOperator.receiveStr(hostClient);
        if (hostClientInfo == null || hostClientInfo.isEmpty()) {
            UnSupportHostVersionException.throwException(hostClient.getIP(), "_NULL_");
        }

        String[] info = hostClientInfo.split(";");
        if (info.length < 3 || info.length > 4) {
            UnSupportHostVersionException.throwException(hostClient.getIP(), "_NULL_");
        }

        LanguageData languageData = "zh".equals(info[0]) ?
                LanguageData.getChineseLanguage() : new LanguageData();

        if (!availableVersions.contains(info[1])) {
            InternetOperator.sendStr(hostClient, languageData.UNSUPPORTED_VERSION_MSG + availableVersions.getLast());
            UnSupportHostVersionException.throwException(hostClient.getIP(), info[1]);
        }

        SequenceKey currentSequenceKey = SequenceKey.getEnabledKeyFromDB(info[2]);
        if (currentSequenceKey == null) {
            InternetOperator.sendStr(hostClient, languageData.ACCESS_DENIED_FORCE_EXITING);
            hostClient.close();
            UnRecognizedKeyException.throwException(info[2]);
        }

        if (info.length == 4) {
            hostClient.setTCPEnabled(info[3].startsWith("T"));
            hostClient.setUDPEnabled(info[3].endsWith("U"));
        }

        assert currentSequenceKey != null;
        if (currentSequenceKey.getPort() != DYNAMIC_PORT) {
            boolean isTCPAvailable = isTCPAvailable(currentSequenceKey.getPort());
            boolean isUDPAvailable = isUDPAvailable(currentSequenceKey.getPort());
            if (!isTCPAvailable || !isUDPAvailable) {
                InternetOperator.sendStr(hostClient, languageData.THE_PORT_HAS_ALREADY_BIND);
                AlreadyBlindPortException.throwException(currentSequenceKey.getPort());
            }
        } else {
            int i = getCurrentAvailableOutPort(currentSequenceKey);
            if (i == -1) {
                InternetOperator.sendStr(hostClient, languageData.THE_PORT_HAS_ALREADY_BIND);
                AlreadyBlindPortException.throwException(currentSequenceKey.getDyStart(), currentSequenceKey.getDyEnd());
            }
        }

        if (currentSequenceKey.isOutOfDate()) {
            InternetOperator.sendStr(hostClient, languageData.KEY + info[2] + languageData.ARE_OUT_OF_DATE);
            OutDatedKeyException.throwException(currentSequenceKey);
        }

        hostClient.setKey(currentSequenceKey);
        hostClient.setLangData(languageData);
        hostClient.enableCheckAliveThread();
        availableHostClient.add(hostClient);

        InternetOperator.sendStr(hostClient, languageData.CONNECTION_BUILD_UP_SUCCESSFULLY);
    }

    public static void debugOperation(Exception e) {
        if (IS_DEBUG_MODE) {
            ServerLogger.error("neoProxyServer.debugOperation", e, e.getMessage());
            e.printStackTrace();
        }
    }

    private static void shutdown() {
        ServerLogger.info("neoProxyServer.shuttingDown");

        isStopped = true;
        for (HostClient hostClient : availableHostClient) {
            try {
                hostClient.close();
            } catch (Exception e) {
                debugOperation(e);
            }
        }
        availableHostClient.clear();
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
        myConsole.shutdown();
    }

    public static void copyResourceToJarDirectory(String resourcePath) throws IOException {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("Resource path cannot be null or blank.");
        }

        ClassLoader classLoader = NeoProxyServer.class.getClassLoader();
        try (InputStream resourceStream = classLoader.getResourceAsStream(resourcePath)) {
            if (resourceStream == null) {
                throw new IOException("Resource not found in classpath: " + resourcePath);
            }

            Path targetDirectory = Path.of(getJarDirOrUserDir());
            Path targetFile = targetDirectory.resolve(Paths.get(resourcePath).getFileName());

            Path parentDir = targetFile.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.copy(resourceStream, targetFile, StandardCopyOption.REPLACE_EXISTING);

        }
    }

    public static String getJarDirOrUserDir() {
        String fallbackPath = System.getProperty("user.dir");

        try {
            ProtectionDomain protectionDomain = NeoProxyServer.class.getProtectionDomain();
            if (protectionDomain == null) {
                return fallbackPath;
            }

            CodeSource codeSource = protectionDomain.getCodeSource();
            if (codeSource == null) {
                return fallbackPath;
            }

            URL location = codeSource.getLocation();
            if (location == null) {
                return fallbackPath;
            }

            File file = new File(location.toURI());

            if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                File parentDir = file.getParentFile();
                if (parentDir != null) {
                    return parentDir.getAbsolutePath();
                }
                return fallbackPath;
            }
        } catch (Exception ignored) {
        }

        return fallbackPath;
    }
}