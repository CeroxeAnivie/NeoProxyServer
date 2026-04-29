package neoproxy.neoproxyserver;

import top.ceroxe.api.management.bufferedFile.SizeCalculator;
import top.ceroxe.api.net.SecureServerSocket;
import top.ceroxe.api.net.SecureSocket;
import top.ceroxe.api.security.AtomicIdGenerator;
import top.ceroxe.api.thread.ThreadManager;
import top.ceroxe.api.utils.MyConsole;
import top.ceroxe.api.utils.Sleeper;
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
    public static final java.util.concurrent.atomic.LongAdder TOTAL_BYTES_COUNTER = new java.util.concurrent.atomic.LongAdder();
    private static final ReentrantLock UDP_GLOBAL_LOCK = new ReentrantLock();
    public static String VERSION = getFromAppProperties("app.version");
    public static String EXPECTED_CLIENT_VERSION = getFromAppProperties("app.expected.client.version");
    public static final CopyOnWriteArrayList<String> availableVersions = toCopyOnWriteArrayListWithLoop(EXPECTED_CLIENT_VERSION.split("\\|"));
    public static int HOST_HOOK_PORT = 44801;
    public static int HOST_CONNECT_PORT = 44802;
    public static String LOCAL_DOMAIN_NAME = "localhost";
    public static SecureServerSocket hostServerHookServerSocket = null;
    public static SecureServerSocket hostServerTransferServerSocket = null;
    public static boolean IS_DEBUG_MODE = false;
    public static MyConsole myConsole;
    public static boolean isStopped = false;

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
            copyResourceToJarDirectory("templates/eula.txt");
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
                // 执行握手和合法性检查
                NeoProxyServer.checkHostClientLegitimacyAndTellInfo(hostClient);
                // 握手成功后，启动传输服务
                NeoProxyServer.handleTransformerServiceWithNewThread(hostClient);
            } catch (IndexOutOfBoundsException | IOException | NoMorePortException | PortOccupiedException |
                     UnRecognizedKeyException | OutDatedKeyException e) {
                // 这些是常规业务异常，记录日志并断开
                Debugger.debugOperation("Handshake failed with exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                ServerLogger.sayHostClientDiscInfo(hostClient, "NeoProxyServer");
                hostClient.close();
            } catch (UnSupportHostVersionException e) {
                // 版本不支持
                Debugger.debugOperation("Unsupported version detected for client: " + hostClient.getIP());
                UpdateManager.handle(hostClient);
            } catch (SilentException ignore) {
                // 静默异常（如IP封禁），不处理
            } catch (Exception e) {
                // [重点修改] 捕获由 RemoteKeyProvider 抛出的包含 BlockingMessageException 的运行时异常
                if (e instanceof RuntimeException && e.getCause() instanceof BlockingMessageException) {
                    String msg = ((BlockingMessageException) e.getCause()).getCustomMessage();
                    Debugger.debugOperation("Blocking client with message: " + msg);
                    try {
                        // 将 NKM 返回的自定义消息发送给 HostClient
                        InternetOperator.sendStr(hostClient, msg);
                        // 发送 exit 指令确保客户端知道要退出了
                        InternetOperator.sendCommand(hostClient, "exit");
                    } catch (Exception ignoreSend) {
                        // 忽略发送失败
                    }
                    hostClient.close();
                    return;
                }

                // 其他未知异常
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
                        Sleeper.sleep(200);
                        PushbackInputStream pbis = new PushbackInputStream(client.getInputStream(), 8);
                        int originalTimeout = client.getSoTimeout();
                        client.setSoTimeout(1000);

                        byte[] headerBytes = new byte[8];
                        int readLen;
                        try {
                            int b = pbis.read();
                            if (b == -1) {
                                Debugger.debugOperation("TCP Probe: Client sent EOS immediately.");
                                client.close();
                                return;
                            }
                            headerBytes[0] = (byte) b;
                            readLen = 1;

                            if (IS_DEBUG_MODE) {
                                int available = pbis.available();
                                if (available > 0) {
                                    int toRead = Math.min(available, 7);
                                    int r = pbis.read(headerBytes, 1, toRead);
                                    if (r > 0) readLen += r;
                                }
                                Debugger.debugOperation("TCP Probe: Read " + readLen + " bytes: " + Arrays.toString(Arrays.copyOf(headerBytes, readLen)));
                            }
                            pbis.unread(headerBytes, 0, readLen);

                        } catch (SocketTimeoutException e) {
                            Debugger.debugOperation("TCP Probe: Timeout while pre-reading. Continuing...");
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
                } finally {
                    UDP_GLOBAL_LOCK.unlock();
                }

                if (existingReply != null) {
                    byte[] serializedData = UDPTransformer.serializeDatagramPacket(datagramPacket);
                    if (!existingReply.addPacketToSend(serializedData)) {
                        Debugger.debugOperation("UDP: Dropped packet because send queue is full or session stopped: " + clientIP + ":" + clientOutPort);
                    }
                    continue;
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
                            byte[] firstData = UDPTransformer.serializeDatagramPacket(datagramPacket);
                            if (!newUdpTransformer.addPacketToSend(firstData)) {
                                UDPTransformer.udpClientConnections.remove(newUdpTransformer);
                                close(hostReply.host());
                                Debugger.debugOperation("UDP: Failed to enqueue first packet for " + clientIP + ":" + clientOutPort);
                                return;
                            }
                            ThreadManager.runAsync(newUdpTransformer);
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
            if (isTCPAndUDPAvailable(i)) {
                Debugger.debugOperation("Found available port: " + i);
                return i;
            }
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

        String[] info = hostClientInfo.split(";", -1);
        if (info.length < 3 || info.length > 4 || info[0].isBlank() || info[1].isBlank() || info[2].isBlank())
            UnSupportHostVersionException.throwException(hostClient.getIP(), "_NULL_");

        // 提取语言信息
        LanguageData languageData = "zh".equals(info[0]) ? LanguageData.getChineseLanguage() : new LanguageData();

        // [修改点] 使用 VersionChecker 进行版本合法性判定
        String clientVersion = info[1];
        boolean isVersionSupported = VersionChecker.isVersionSupported(clientVersion, availableVersions);

        SequenceKey currentSequenceKey = null;
        try {
            // 向 NKM (或者本地 KeyProvider) 验证密钥合法性
            currentSequenceKey = SequenceKey.getEnabledKeyFromDB(info[2]);
        } catch (PortOccupiedException | NoMorePortException e) {
            InternetOperator.sendStr(hostClient, languageData.REMOTE_PORT_OCCUPIED);
            hostClient.close();
            SilentException.throwException();
        } catch (OutDatedKeyException e) {
            InternetOperator.sendStr(hostClient, languageData.KEY + info[2] + languageData.ARE_OUT_OF_DATE);
            hostClient.close();
            SilentException.throwException();
        } catch (UnRecognizedKeyException e) {
            InternetOperator.sendStr(hostClient, languageData.ACCESS_DENIED_FORCE_EXITING);
            hostClient.close();
            SilentException.throwException();
        } catch (NoMoreNetworkFlowException e) {
            InternetOperator.sendStr(hostClient, languageData.THIS_KEY_HAVE_NO_NETWORK_FLOW_LEFT);
            hostClient.close();
            SilentException.throwException();
        }

        if (currentSequenceKey == null) {
            InternetOperator.sendStr(hostClient, languageData.ACCESS_DENIED_FORCE_EXITING);
            hostClient.close();
            UnRecognizedKeyException.throwException(info[2]);
        }

        // 绑定关键数据到 hostClient 对象，确保后续 UpdateManager 能够识别用户身份
        hostClient.setKey(currentSequenceKey);
        hostClient.setLangData(languageData);

        // [修改点] 密钥通过后，如果版本不匹配，触发更新逻辑
        if (!isVersionSupported) {
            Debugger.debugOperation("Version unsupported (including wildcard check), but key is valid. Triggering update.");
            // 告知客户端当前服务器支持的版本范围。
            InternetOperator.sendStr(hostClient, languageData.UNSUPPORTED_VERSION_MSG + getClientVersionUpdateHint());
            UnSupportHostVersionException.throwException(hostClient.getIP(), clientVersion);
        }

        // --- 以下为版本匹配成功后的逻辑 ---

        // 处理 TCP/UDP 开启状态 (T/U 标志位)
        if (info.length == 4) {
            applyProtocolFlags(hostClient, info[3]);
        }

        // 检查端口可用性 (如果是固定端口)
        if (currentSequenceKey.getPort() != DYNAMIC_PORT) {
            if (!isTCPAndUDPAvailable(currentSequenceKey.getPort())) {
                InternetOperator.sendStr(hostClient, languageData.THE_PORT_HAS_ALREADY_BIND);
                NoMorePortException.throwException(currentSequenceKey.getPort());
            }
        }

        hostClient.enableCheckAliveThread();
        availableHostClient.add(hostClient);
        InternetOperator.sendStr(hostClient, languageData.CONNECTION_BUILD_UP_SUCCESSFULLY);
        Debugger.debugOperation("Handshake completed successfully with version: " + clientVersion);
    }

    private static void applyProtocolFlags(HostClient hostClient, String flags) throws UnSupportHostVersionException {
        /*
         * 客户端允许发送空协议标志，表示当前不监听 TCP/UDP，但保留后续通过控制命令开启的能力。
         * 这里必须保留 split(..., -1) 得到的尾部空字段，否则 Java 会吞掉最后一个空段并回落到默认双开。
         */
        String normalizedFlags = flags == null ? "" : flags.trim();
        if (!normalizedFlags.isEmpty()
                && !"T".equals(normalizedFlags)
                && !"U".equals(normalizedFlags)
                && !"TU".equals(normalizedFlags)) {
            UnSupportHostVersionException.throwException(hostClient.getIP(), normalizedFlags);
        }
        hostClient.setTCPEnabled(normalizedFlags.contains("T"));
        hostClient.setUDPEnabled(normalizedFlags.contains("U"));
    }

    private static String getClientVersionUpdateHint() {
        return String.join("|", availableVersions);
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
