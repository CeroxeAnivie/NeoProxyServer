package neoproxy.neoproxyserver;

import fun.ceroxe.api.management.bufferedFile.SizeCalculator;
import fun.ceroxe.api.net.SecureServerSocket;
import fun.ceroxe.api.net.SecureSocket;
import fun.ceroxe.api.security.AtomicIdGenerator;
import fun.ceroxe.api.thread.ThreadManager;
import fun.ceroxe.api.utils.MyConsole;
import fun.ceroxe.api.utils.Sleeper;
import neoproxy.neoproxyserver.core.*;
import neoproxy.neoproxyserver.core.exceptions.*;
import neoproxy.neoproxyserver.core.management.*;
import neoproxy.neoproxyserver.core.threads.TCPTransformer;
import neoproxy.neoproxyserver.core.threads.UDPTransformer;
import neoproxy.neoproxyserver.core.webadmin.WebAdminManager;

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
import java.util.Arrays;
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

    // 修改点 1: 这里去掉了 ArrayUtils，直接调用类内部定义的静态方法
    public static final CopyOnWriteArrayList<String> availableVersions = toCopyOnWriteArrayListWithLoop(EXPECTED_CLIENT_VERSION.split("\\|"));

    public static int HOST_HOOK_PORT = 44801;
    public static int HOST_CONNECT_PORT = 44802;
    public static String LOCAL_DOMAIN_NAME = "localhost";
    public static SecureServerSocket hostServerHookServerSocket = null;
    public static SecureServerSocket hostServerTransferServerSocket = null;
    public static boolean IS_DEBUG_MODE = false;
    public static MyConsole myConsole;
    public static boolean isStopped = false;

    // 修改点 2: 手动实现 toCopyOnWriteArrayListWithLoop 方法
    private static CopyOnWriteArrayList<String> toCopyOnWriteArrayListWithLoop(String[] array) {
        CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
        if (array != null) {
            list.addAll(Arrays.asList(array));
        }
        return list;
    }

    private static String getFromAppProperties(String name) {
        Properties props = new Properties();
        try (InputStream is = NeoProxyServer.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (is != null) props.load(is);
        } catch (IOException ignored) {
        }
        return props.getProperty(name, "1.0");
    }

    public static void initStructure() {
        Debugger.debugOperation("Entry: initStructure()");
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
        SecureSocket.setMaxAllowedPacketSize((int) SizeCalculator.mibToByte(200));
        try {
            hostServerHookServerSocket = new SecureServerSocket(HOST_HOOK_PORT);
            Debugger.debugOperation("Bound HostHookPort: " + HOST_HOOK_PORT);
            TransferSocketAdapter.startThread();
        } catch (IOException e) {
            Debugger.debugOperation(e);
            ServerLogger.error("neoProxyServer.canNotBindPort");
            System.exit(-1);
        }
        loadBannedIPs();
        WebAdminManager.init();
        Debugger.debugOperation("Exit: initStructure() completed");
    }

    private static void checkARGS(String[] args) {
        Debugger.debugOperation("Checking ARGS: " + Arrays.toString(args));
        for (String arg : args) {
            switch (arg) {
                case "--debug" -> {
                    IS_DEBUG_MODE = true;
                    Debugger.debugOperation("Debug mode enabled via argument.");
                }
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

        Debugger.debugOperation("Main loop starting...");
        while (!isStopped) {
            HostClient hostClient;
            try {
                hostClient = listenAndConfigureHostClient();
                handleNewHostClient(hostClient);
            } catch (IOException e) {
                Debugger.debugOperation(e);
                if (!isStopped && alert) ServerLogger.info("neoProxyServer.clientConnectButFail", e.getMessage());
            } catch (SilentException ignored) {
                Debugger.debugOperation("SilentException caught in main loop - likely IP ban or null socket.");
            }
        }
        Debugger.debugOperation("Main loop exited.");
    }

    private static void handleNewHostClient(HostClient hostClient) {
        Debugger.debugOperation("Entry: handleNewHostClient for IP: " + hostClient.getIP());
        ThreadManager.runAsync(() -> {
            try {
                NeoProxyServer.checkHostClientLegitimacyAndTellInfo(hostClient);
                NeoProxyServer.handleTransformerServiceWithNewThread(hostClient);
            } catch (IndexOutOfBoundsException | IOException | NoMorePortException | PortOccupiedException |
                     UnRecognizedKeyException | OutDatedKeyException e) {
                Debugger.debugOperation("Handshake failed with exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                ServerLogger.sayHostClientDiscInfo(hostClient, "NeoProxyServer");
                hostClient.close();
            } catch (UnSupportHostVersionException e) {
                Debugger.debugOperation("Unsupported version detected for client: " + hostClient.getIP());
                UpdateManager.handle(hostClient);
            } catch (SilentException ignore) {
            } catch (Exception e) {
                Debugger.debugOperation(e);
                hostClient.close();
            }
        });
    }

    public static HostClient listenAndConfigureHostClient() throws SilentException, IOException {
        Debugger.debugOperation("Waiting for HostClient connection on hook port...");
        SecureSocket hostServerHook = hostServerHookServerSocket.accept();
        if (hostServerHook == null) {
            Debugger.debugOperation("Accept returned null.");
            SilentException.throwException();
        }
        String clientAddress = hostServerHook.getInetAddress().getHostAddress();
        Debugger.debugOperation("HostClient connected from: " + clientAddress);

        if (alert) ServerLogger.info("neoProxyServer.clientTryToConnect", clientAddress);
        if (IPChecker.exec(clientAddress, IPChecker.CHECK_IS_BAN)) {
            Debugger.debugOperation("IP is banned: " + clientAddress);
            close(hostServerHook);
            if (alert) ServerLogger.info("neoProxyServer.banConnectInfo", clientAddress);
            SilentException.throwException();
        }
        return new HostClient(hostServerHook);
    }

    public static void handleTransformerServiceWithNewThread(HostClient hostClient) {
        Debugger.debugOperation("Starting Transformer threads for client: " + hostClient.getIP());

        ThreadManager.runAsync(() -> {
            Debugger.debugOperation("TCP Service Loop started for client: " + hostClient.getIP());
            while (!hostClient.isStopped()) {
                Socket client;
                try {
                    client = hostClient.getClientServerSocket().accept();
                    if (IPChecker.exec(client.getInetAddress().getHostAddress(), IPChecker.CHECK_IS_BAN)) {
                        Debugger.debugOperation("Blocked banned IP trying to use proxy: " + client.getInetAddress().getHostAddress());
                        client.close();
                        continue;
                    }
                } catch (IOException | NullPointerException e) {
                    if (hostClient.isTCPEnabled()) Debugger.debugOperation(e);
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
                                Debugger.debugOperation("TCP Probe: Client sent EOS immediately.");
                                client.close();
                                return;
                            }
                            headerBytes[0] = (byte) b;
                            readLen = 1;

                            // 往后读取8个字节看看是什么头（debug下）
                            if (IS_DEBUG_MODE) {
                                int available = pbis.available();
                                if (available > 0) {
                                    int toRead = Math.min(available, 7);
                                    int r = pbis.read(headerBytes, 1, toRead);
                                    if (r > 0) readLen += r;
                                }
                                Debugger.debugOperation("TCP Probe: Read " + readLen + " bytes: " + Arrays.toString(Arrays.copyOf(headerBytes, readLen)));
                            }

                            // 将读取的所有字节推回流中
                            pbis.unread(headerBytes, 0, readLen);

                        } catch (SocketTimeoutException e) {
                            Debugger.debugOperation("TCP Probe: Timeout while pre-reading. Continuing...");
                            // 仅超时，流可能还是有效的，继续
                        } catch (IOException e) {
                            Debugger.debugOperation(e);
                            client.close();
                            return;
                        } finally {
                            client.setSoTimeout(originalTimeout);
                        }

                        long socketID = AtomicIdGenerator.GLOBAL.nextId();
                        Debugger.debugOperation("Allocated TCP SocketID: " + socketID + " for " + client.getInetAddress());

                        sendCommand(hostClient, "sendSocketTCP;" + socketID + ";" + getInternetAddressAndPort(client));
                        hostClient.refreshHeartbeat();

                        HostReply hostReply;
                        try {
                            hostReply = TransferSocketAdapter.getHostReply(socketID, TransferSocketAdapter.CONN_TYPE.TCP);
                        } catch (SocketTimeoutException e) {
                            Debugger.debugOperation("Timeout waiting for HostReply (TCP) ID: " + socketID);
                            ServerLogger.sayClientSuccConnectToChaSerButHostClientTimeOut(hostClient);
                            ServerLogger.sayKillingClientSideConnection(client);
                            close(client);
                            return;
                        }

                        Debugger.debugOperation("Starting TCPTransformer for SocketID: " + socketID);
                        TCPTransformer.start(hostClient, hostReply, client, pbis);
                        ServerLogger.sayClientTCPConnectBuildUpInfo(hostClient, client);
                    } catch (Exception e) {
                        Debugger.debugOperation(e);
                        close(client);
                    }
                });
            }
            Debugger.debugOperation("TCP Service Loop exited for client: " + hostClient.getIP());
        });

        // ... (UDP 逻辑保持不变) ...
        ThreadManager.runAsync(() -> {
            Debugger.debugOperation("UDP Service Loop started for client: " + hostClient.getIP());
            while (!hostClient.isStopped()) {
                byte[] buffer = new byte[BUFFER_LEN];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                DatagramSocket datagramSocket = hostClient.getClientDatagramSocket();

                try {
                    datagramSocket.receive(datagramPacket);
                } catch (IOException | NullPointerException e) {
                    if (hostClient.isUDPEnabled()) Debugger.debugOperation(e);
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
                    Debugger.debugOperation("UDP: New session for " + clientIP + ":" + clientOutPort);
                    ThreadManager.runAsync(() -> {
                        try {
                            long socketID = AtomicIdGenerator.GLOBAL.nextId();
                            Debugger.debugOperation("Allocated UDP SocketID: " + socketID);

                            sendCommand(hostClient, "sendSocketUDP;" + socketID + ";" + getInternetAddressAndPort(datagramPacket));
                            hostClient.refreshHeartbeat();
                            HostReply hostReply;
                            try {
                                hostReply = TransferSocketAdapter.getHostReply(socketID, TransferSocketAdapter.CONN_TYPE.UDP);
                            } catch (SocketTimeoutException e) {
                                Debugger.debugOperation("Timeout waiting for HostReply (UDP) ID: " + socketID);
                                ServerLogger.sayClientSuccConnectToChaSerButHostClientTimeOut(hostClient);
                                return;
                            }

                            Debugger.debugOperation("Starting UDPTransformer for SocketID: " + socketID);
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
                            Debugger.debugOperation(e);
                        }
                    });
                }
            }
            Debugger.debugOperation("UDP Service Loop exited for client: " + hostClient.getIP());
        });
    }

    private static int getCurrentAvailableOutPort(SequenceKey sequenceKey) {
        Debugger.debugOperation("Searching for available port in range " + sequenceKey.getDyStart() + "-" + sequenceKey.getDyEnd());
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
            Debugger.debugOperation("Found available port: " + i);
            return i;
        }
        Debugger.debugOperation("No available ports found in range.");
        return -1;
    }

    private static void checkHostClientLegitimacyAndTellInfo(HostClient hostClient) throws Exception {
        Debugger.debugOperation("Entry: checkHostClientLegitimacyAndTellInfo");
        NeoProxyServer.checkHostClientVersionAndKeyAndLang(hostClient);
        int port;
        if (hostClient.getKey().getPort() != DYNAMIC_PORT) {
            port = hostClient.getKey().getPort();
            Debugger.debugOperation("Using static port: " + port);
        } else {
            port = NeoProxyServer.getCurrentAvailableOutPort(hostClient.getKey());
            if (port == -1) {
                Debugger.debugOperation("Dynamic port allocation failed.");
                NoMorePortException.throwException();
            }
            Debugger.debugOperation("Assigned dynamic port: " + port);
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
        Debugger.debugOperation("Exit: checkHostClientLegitimacyAndTellInfo success.");
    }

    private static void checkHostClientVersionAndKeyAndLang(HostClient hostClient) throws Exception {
        Debugger.debugOperation("Reading client info string...");
        String hostClientInfo = InternetOperator.receiveStr(hostClient);
        Debugger.debugOperation("Received client info: " + hostClientInfo);

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
        } catch (PortOccupiedException | NoMorePortException e) {
            InternetOperator.sendStr(hostClient, languageData.REMOTE_PORT_OCCUPIED);
            hostClient.close();
            SilentException.throwException();
        }
        if (currentSequenceKey == null) {
            Debugger.debugOperation("Key validation failed for: " + info[2]);
            InternetOperator.sendStr(hostClient, languageData.ACCESS_DENIED_FORCE_EXITING);
            hostClient.close();
            UnRecognizedKeyException.throwException(info[2]);
        }
        if (info.length == 4) {
            hostClient.setTCPEnabled(info[3].startsWith("T"));
            hostClient.setUDPEnabled(info[3].endsWith("U"));
            Debugger.debugOperation("Client capabilities - TCP: " + hostClient.isTCPEnabled() + ", UDP: " + hostClient.isUDPEnabled());
        }
        assert currentSequenceKey != null;
        if (currentSequenceKey.getPort() != DYNAMIC_PORT) {
            boolean isTCPAvailable = isTCPAvailable(currentSequenceKey.getPort());
            boolean isUDPAvailable = isUDPAvailable(currentSequenceKey.getPort());
            if (!isTCPAvailable || !isUDPAvailable) {
                Debugger.debugOperation("Static port " + currentSequenceKey.getPort() + " already bind.");
                InternetOperator.sendStr(hostClient, languageData.THE_PORT_HAS_ALREADY_BIND);
                NoMorePortException.throwException(currentSequenceKey.getPort());
            }
        } else {
            int i = getCurrentAvailableOutPort(currentSequenceKey);
            if (i == -1) {
                Debugger.debugOperation("No dynamic ports available for key: " + info[2]);
                InternetOperator.sendStr(hostClient, languageData.THE_PORT_HAS_ALREADY_BIND);
                NoMorePortException.throwException(currentSequenceKey.getDyStart(), currentSequenceKey.getDyEnd());
            }
        }
        // 【检测】检查过期
        if (currentSequenceKey.isOutOfDate()) {
            Debugger.debugOperation("Key expired: " + info[2]);
            InternetOperator.sendStr(hostClient, languageData.KEY + info[2] + languageData.ARE_OUT_OF_DATE);
            OutDatedKeyException.throwException(currentSequenceKey);
        }
        hostClient.setKey(currentSequenceKey);
        hostClient.setLangData(languageData);
        hostClient.enableCheckAliveThread();
        availableHostClient.add(hostClient);
        InternetOperator.sendStr(hostClient, languageData.CONNECTION_BUILD_UP_SUCCESSFULLY);
        Debugger.debugOperation("Handshake completed successfully.");
    }

    private static void shutdown() {
        Debugger.debugOperation("Shutdown hook triggered.");
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
        Debugger.debugOperation("Shutdown process finished.");
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