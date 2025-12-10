package neoproxy.neoproxyserver;

import neoproxy.neoproxyserver.core.*;
import neoproxy.neoproxyserver.core.exceptions.*;
import neoproxy.neoproxyserver.core.management.*;
import neoproxy.neoproxyserver.core.threads.TCPTransformer;
import neoproxy.neoproxyserver.core.threads.UDPTransformer;
import neoproxy.neoproxyserver.core.webadmin.WebAdminManager;
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
import static neoproxy.neoproxyserver.core.threads.TCPTransformer.BUFFER_LEN;

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
        try (InputStream is = NeoProxyServer.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (is != null) props.load(is);
        } catch (IOException ignored) {
        }
        return props.getProperty(name, "1.0");
    }

    public static void initStructure() {
        try {
            copyResourceToJarDirectory("eula.txt");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-2);
        }
        ConsoleManager.init();
        ConfigOperator.readAndSetValue();
        WebAdminManager.init();
        printLogo();
        SequenceKey.initProvider();
        UpdateManager.init();
        SecureSocket.setMaxAllowedPacketSize((int) plethora.management.bufferedFile.SizeCalculator.mibToByte(200));
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

        // Logs...
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
                if (!isStopped && alert) ServerLogger.info("neoProxyServer.clientConnectButFail", e.getMessage());
            } catch (SlientException ignored) {
            }
        }
    }

    private static void handleNewHostClient(HostClient hostClient) {
        ThreadManager.runAsync(() -> {
            try {
                NeoProxyServer.checkHostClientLegitimacyAndTellInfo(hostClient);
                NeoProxyServer.handleTransformerServiceWithNewThread(hostClient);
            } catch (IndexOutOfBoundsException | IOException | NoMorePortException | AlreadyBlindPortException |
                     UnRecognizedKeyException | OutDatedKeyException e) {
                ServerLogger.sayHostClientDiscInfo(hostClient, "NeoProxyServer");
                hostClient.close();
            } catch (UnSupportHostVersionException e) {
                UpdateManager.handle(hostClient);
            } catch (SlientException ignore) {
            } catch (Exception e) {
                hostClient.close();
            }
        });
    }

    public static HostClient listenAndConfigureHostClient() throws SlientException, IOException {
        SecureSocket hostServerHook = hostServerHookServerSocket.accept();
        if (hostServerHook == null) SlientException.throwException();
        String clientAddress = hostServerHook.getInetAddress().getHostAddress();
        if (alert) ServerLogger.info("neoProxyServer.clientTryToConnect", clientAddress);
        if (IPChecker.exec(clientAddress, IPChecker.CHECK_IS_BAN)) {
            close(hostServerHook);
            if (alert) ServerLogger.info("neoProxyServer.banConnectInfo", clientAddress);
            SlientException.throwException();
        }
        return new HostClient(hostServerHook);
    }

    public static void handleTransformerServiceWithNewThread(HostClient hostClient) {
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
                    if (hostClient.isTCPEnabled()) debugOperation(e);
                    waitForTcpEnabled(hostClient);
                    continue;
                }

                ThreadManager.runAsync(() -> {
                    try {
                        // 1. 反扫描：强制延迟 200ms
                        Sleeper.sleep(200);

                        // 2. 预读并检查
                        PushbackInputStream pbis = new PushbackInputStream(client.getInputStream(), 8); // 增加缓冲区以容纳 HTTP 方法检查
                        int originalTimeout = client.getSoTimeout();
                        client.setSoTimeout(1000); // 临时超时，防止读取阻塞过久

                        byte[] headerBytes = new byte[8];
                        int readLen = 0;
                        try {
                            // 尝试读取最多 8 个字节
                            int b = pbis.read();
                            if (b == -1) {
                                client.close();
                                return;
                            }
                            headerBytes[0] = (byte) b;
                            readLen = 1;

                            // 尽力读取后续字节用于 Web 检查
                            int available = pbis.available();
                            if (available > 0) {
                                int toRead = Math.min(available, 7);
                                int r = pbis.read(headerBytes, 1, toRead);
                                if (r > 0) readLen += r;
                            }

                            // 将读取的所有字节推回流中
                            pbis.unread(headerBytes, 0, readLen);

                        } catch (SocketTimeoutException e) {
                            // 仅超时，流可能还是有效的，继续
                        } catch (IOException e) {
                            debugOperation(e);
                            client.close();
                            return;
                        } finally {
                            client.setSoTimeout(originalTimeout);
                        }

                        long socketID = AtomicIdGenerator.GLOBAL.nextId();
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

                        TCPTransformer.start(hostClient, hostReply, client, pbis);
                        ServerLogger.sayClientTCPConnectBuildUpInfo(hostClient, client);
                    } catch (Exception e) {
                        debugOperation(e);
                        close(client);
                    }
                });
            }
        });

        // ... (UDP 逻辑保持不变) ...
        ThreadManager.runAsync(() -> {
            while (!hostClient.isStopped()) {
                byte[] buffer = new byte[BUFFER_LEN];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                DatagramSocket datagramSocket = hostClient.getClientDatagramSocket();

                try {
                    datagramSocket.receive(datagramPacket);
                } catch (IOException | NullPointerException e) {
                    if (hostClient.isUDPEnabled()) debugOperation(e);
                    waitForUDPEnabled(hostClient);
                    continue;
                }

                // ... (UDP existing logic) ...
                final String clientIP = datagramPacket.getAddress().getHostAddress();
                final int clientOutPort = datagramPacket.getPort();
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
                        byte[] serializedData = UDPTransformer.serializeDatagramPacket(datagramPacket);
                        existingReply.addPacketToSend(serializedData);
                    }
                } finally {
                    UDP_GLOBAL_LOCK.unlock();
                }

                if (existingReply == null) {
                    ThreadManager.runAsync(() -> {
                        try {
                            long socketID = AtomicIdGenerator.GLOBAL.nextId();
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

    // ... (Retention of helper methods: getCurrentAvailableOutPort, checkHostClientLegitimacyAndTellInfo, checkHostClientVersionAndKeyAndLang, debugOperation, shutdown, copyResourceToJarDirectory, getJarDirOrUserDir) ...

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

    private static void checkHostClientLegitimacyAndTellInfo(HostClient hostClient) throws Exception {
        NeoProxyServer.checkHostClientVersionAndKeyAndLang(hostClient);
        int port;
        if (hostClient.getKey().getPort() != DYNAMIC_PORT) {
            port = hostClient.getKey().getPort();
        } else {
            port = NeoProxyServer.getCurrentAvailableOutPort(hostClient.getKey());
            if (port == -1) NoMorePortException.throwException();
        }
        hostClient.setOutPort(port);
        if (hostClient.isTCPEnabled()) hostClient.setClientServerSocket(new ServerSocket(port));
        if (hostClient.isUDPEnabled()) hostClient.setClientDatagramSocket(new DatagramSocket(port));

        String clientAddress = InternetOperator.getInternetAddressAndPort(hostClient.getHostServerHook());
        ServerLogger.info("neoProxyServer.hostClientRegisterSuccess", clientAddress);
        // 端口已准备好，Key 已设置，启动心跳
        hostClient.startRemoteHeartbeat();

        InternetOperator.sendCommand(hostClient, String.valueOf(port));
        InternetOperator.sendStr(hostClient, hostClient.getLangData().THIS_ACCESS_CODE_HAVE + hostClient.getKey().getBalance() + hostClient.getLangData().MB_OF_FLOW_LEFT);
        InternetOperator.sendStr(hostClient, hostClient.getLangData().EXPIRE_AT + hostClient.getKey().getExpireTime());
        InternetOperator.sendStr(hostClient, hostClient.getLangData().USE_THE_ADDRESS + LOCAL_DOMAIN_NAME + ":" + port + hostClient.getLangData().TO_START_UP_CONNECTION);
        ServerLogger.info("neoProxyServer.assignedConnectionAddress", LOCAL_DOMAIN_NAME + ":" + port);
    }

    private static void checkHostClientVersionAndKeyAndLang(HostClient hostClient) throws Exception {
        String hostClientInfo = InternetOperator.receiveStr(hostClient);
        if (hostClientInfo == null || hostClientInfo.isEmpty())
            UnSupportHostVersionException.throwException(hostClient.getIP(), "_NULL_");
        String[] info = hostClientInfo.split(";");
        if (info.length < 3 || info.length > 4)
            UnSupportHostVersionException.throwException(hostClient.getIP(), "_NULL_");
        LanguageData languageData = "zh".equals(info[0]) ? LanguageData.getChineseLanguage() : new LanguageData();
        if (!availableVersions.contains(info[1])) {
            InternetOperator.sendStr(hostClient, languageData.UNSUPPORTED_VERSION_MSG + availableVersions.getLast());
            UnSupportHostVersionException.throwException(hostClient.getIP(), info[1]);
        }
        SequenceKey currentSequenceKey = null;
        try {
            currentSequenceKey = SequenceKey.getEnabledKeyFromDB(info[2]);
        } catch (PortOccupiedException e) {
            InternetOperator.sendStr(hostClient, languageData.REMOTE_PORT_OCCUPIED);
            hostClient.close();
            SlientException.throwException();
        }
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
        // 【检测】检查过期
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

    // ... (Rest of utils) ...
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
            } catch (Exception ignored) {
            }
        }
        availableHostClient.clear();
        try {
            if (hostServerHookServerSocket != null) hostServerHookServerSocket.close();
        } catch (Exception ignored) {
        }
        try {
            if (hostServerTransferServerSocket != null) hostServerTransferServerSocket.close();
        } catch (Exception ignored) {
        }
        ServerLogger.info("neoProxyServer.shutdownCompleted");
        if (myConsole != null) myConsole.shutdown();
    }

    public static void copyResourceToJarDirectory(String resourcePath) throws IOException {
        if (resourcePath == null || resourcePath.isBlank()) throw new IllegalArgumentException();
        ClassLoader classLoader = NeoProxyServer.class.getClassLoader();
        try (InputStream resourceStream = classLoader.getResourceAsStream(resourcePath)) {
            if (resourceStream == null) throw new IOException("Resource not found: " + resourcePath);
            Path targetFile = Path.of(getJarDirOrUserDir()).resolve(Paths.get(resourcePath).getFileName());
            Files.copy(resourceStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String getJarDirOrUserDir() {
        String fallbackPath = System.getProperty("user.dir");
        try {
            ProtectionDomain protectionDomain = NeoProxyServer.class.getProtectionDomain();
            if (protectionDomain == null) return fallbackPath;
            CodeSource codeSource = protectionDomain.getCodeSource();
            if (codeSource == null) return fallbackPath;
            URL location = codeSource.getLocation();
            if (location == null) return fallbackPath;
            File file = new File(location.toURI());
            if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                File parentDir = file.getParentFile();
                if (parentDir != null) return parentDir.getAbsolutePath();
            }
        } catch (Exception ignored) {
        }
        return fallbackPath;
    }
}