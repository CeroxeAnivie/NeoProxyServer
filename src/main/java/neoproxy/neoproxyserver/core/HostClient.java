package neoproxy.neoproxyserver.core;

import fun.ceroxe.api.net.SecureSocket;
import fun.ceroxe.api.thread.ThreadManager;
import fun.ceroxe.api.utils.Sleeper;
import neoproxy.neoproxyserver.core.management.SequenceKey;
import neoproxy.neoproxyserver.core.management.provider.Protocol;
import neoproxy.neoproxyserver.core.threads.RateLimiter;
import neoproxy.neoproxyserver.core.threads.UDPTransformer;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static neoproxy.neoproxyserver.core.Debugger.debugOperation;
import static neoproxy.neoproxyserver.core.ServerLogger.sayHostClientDiscInfo;
import static neoproxy.neoproxyserver.core.management.SequenceKey.saveToDB;

/**
 * HostClient - 内网穿透客户端会话管理类
 *
 * <p>每个连接到NeoProxyServer的客户端对应一个HostClient实例。
 * 负责管理客户端的生命周期、心跳检测、流量统计、TCP/UDP连接池等。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>客户端连接管理 - 维护与内网客户端的加密连接</li>
 *   <li>心跳检测 - 检测客户端是否在线</li>
 *   <li>密钥验证 - 验证客户端提供的访问密钥</li>
 *   <li>流量控制 - 统计和限制客户端流量使用</li>
 *   <li>端口分配 - 为客户端分配公网访问端口</li>
 *   <li>TCP/UDP连接池 - 管理活跃的传输连接</li>
 * </ul>
 *
 * <p>线程安全：此类是线程安全的，使用ConcurrentHashMap管理TCP连接，
 * 使用AtomicBoolean管理关闭状态。</p>
 *
 * @author Ceroxe
 * @version 6.1.0
 * @since 6.1.0
 */
public final class HostClient implements Closeable {
    private static final String EXPECTED_HEARTBEAT = "PING";
    public static int SAVE_DELAY = 3000;
    public static int DETECTION_DELAY = 1000;
    public static int AES_KEY_SIZE = 128;
    public static int HEARTBEAT_TIMEOUT = 5000;

    private final SecureSocket hostServerHook;
    // 【优化】使用 Set 替代 List，消除数组复制开销，保持线程安全
    private final Set<Socket> activeTcpSockets = ConcurrentHashMap.newKeySet();

    private final RateLimiter globalRateLimiter = new RateLimiter(0);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    // 【修复】isStopped 被多个线程读取（AutoSave、KeyDetection、AliveCheck），
    // 必须 volatile 保证跨线程可见性，与 isClosed（AtomicBoolean）的语义对齐
    private volatile boolean isStopped = false;
    private SequenceKey sequenceKey = null;
    private ServerSocket clientServerSocket = null;
    private DatagramSocket clientDatagramSocket = null;
    private LanguageData languageData = new LanguageData();
    private int outPort = -1;
    private String cachedLocation;
    private String cachedISP;
    private boolean isTCPEnabled = true;
    private boolean isUDPEnabled = true;
    private ScheduledFuture<?> remoteHeartbeatTask;
    private volatile long lastValidHeartbeatTime = System.currentTimeMillis();

    public HostClient(SecureSocket hostServerHook) throws IOException {
        Debugger.debugOperation("Creating HostClient for connection: " + InternetOperator.getInternetAddressAndPort(hostServerHook));
        this.hostServerHook = hostServerHook;
        this.lastValidHeartbeatTime = System.currentTimeMillis();

        HostClient.enableAutoSaveThread(this);
        HostClient.enableKeyDetectionTread(this);
    }

    private static void enableAutoSaveThread(HostClient hostClient) {
        ThreadManager.runAsync(() -> {
            Debugger.debugOperation("AutoSave thread started for " + hostClient.getIP());
            while (!hostClient.isStopped) {
                if (hostClient.getKey() != null) {
                    saveToDB(hostClient.getKey());
                }
                Sleeper.sleep(SAVE_DELAY);
            }
            Debugger.debugOperation("AutoSave thread stopped for " + hostClient.getIP());
        });
    }

    private static void enableKeyDetectionTread(HostClient hostClient) {
        ThreadManager.runAsync(() -> {
            Debugger.debugOperation("KeyDetection thread started for " + hostClient.getIP());
            while (!hostClient.isStopped) {
                if (hostClient.getKey() != null && hostClient.getKey().isOutOfDate()) {
                    Debugger.debugOperation("Key Detection: Key out of date (" + hostClient.getKey().getName() + "). Closing client.");
                    ServerLogger.info("hostClient.keyOutOfDate", hostClient.getKey().getName());
                    try {
                        InternetOperator.sendStr(hostClient, hostClient.getLangData().THE_KEY + hostClient.getKey().getName() + hostClient.getLangData().ARE_OUT_OF_DATE);
                        InternetOperator.sendCommand(hostClient, "exit");
                        ServerLogger.sayHostClientDiscInfo(hostClient, "KeyDetectionTread");
                    } catch (Exception e2) {
                        ServerLogger.sayHostClientDiscInfo(hostClient, "KeyDetectionTread");
                    }

                    hostClient.close();
                    break;
                }

                if (hostClient.getKey() != null && !hostClient.getKey().isEnable()) {
                    Debugger.debugOperation("Key Detection: Key disabled (" + hostClient.getKey().getName() + "). Closing client.");
                    hostClient.close();
                    break;
                }

                Sleeper.sleep(DETECTION_DELAY);
            }
        });
    }

    public static void waitForTcpEnabled(HostClient hostClient) {
        Debugger.debugOperation("Waiting for TCP to be enabled for " + hostClient.getIP());
        CountDownLatch latch = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            try {
                final long parkTimeNanos = 1_000_000L;
                while (!hostClient.isTCPEnabled()) {
                    LockSupport.parkNanos(parkTimeNanos);
                }
                latch.countDown();
            } catch (Exception e) {
                debugOperation(e);
            }
        });
        try {
            latch.await();
            Debugger.debugOperation("TCP now enabled for " + hostClient.getIP());
        } catch (InterruptedException e) {
            debugOperation(e);
        }
    }

    public static void waitForUDPEnabled(HostClient hostClient) {
        Debugger.debugOperation("Waiting for UDP to be enabled for " + hostClient.getIP());
        CountDownLatch latch = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            try {
                final long parkTimeNanos = 1_000_000L;
                while (!hostClient.isUDPEnabled()) {
                    LockSupport.parkNanos(parkTimeNanos);
                }
                latch.countDown();
            } catch (Exception e) {
                debugOperation(e);
            }
        });
        try {
            latch.await();
            Debugger.debugOperation("UDP now enabled for " + hostClient.getIP());
        } catch (InterruptedException e) {
            debugOperation(e);
        }
    }

    public void applyDynamicUpdates() {
        if (this.sequenceKey != null) {
            Debugger.debugOperation("Applying dynamic update. Rate limit: " + this.sequenceKey.getRate());
            this.globalRateLimiter.setMaxMbps(this.sequenceKey.getRate());
        }
    }

    public void startRemoteHeartbeat() {
        Debugger.debugOperation("Starting remote heartbeat task.");
        if (this.sequenceKey == null || this.remoteHeartbeatTask != null) {
            return;
        }

        Runnable task = () -> {
            if (isStopped() || this.sequenceKey == null) {
                return;
            }

            try {
                Protocol.HeartbeatPayload payload = new Protocol.HeartbeatPayload();

                payload.serial = this.sequenceKey.getName();
                payload.nodeId = ConfigOperator.NODE_ID;
                payload.port = String.valueOf(this.outPort);
                payload.timestamp = System.currentTimeMillis();
                payload.currentConnections = this.activeTcpSockets.size(); // 保持兼容旧字段

                // [新增] 获取外部连接的详细字符串 (T:X U:X)
                payload.connectionDetail = getExternalConnectionsStr();

                boolean keepAlive = SequenceKey.PROVIDER.sendHeartbeat(payload);

                if (!keepAlive) {
                    ServerLogger.warn("hostClient.kickedByManager", this.sequenceKey.getName());
                    Debugger.debugOperation("Client kicked by NKM manager.");
                    this.close();
                }
            } catch (Exception e) {
                ServerLogger.warn("hostClient.heartbeatError", e.getMessage());
                Debugger.debugOperation("Remote heartbeat failed: " + e.getMessage());
            }
        };

        this.remoteHeartbeatTask = ThreadManager.getScheduledExecutor().scheduleAtFixedRate(
                task,
                0,
                Protocol.HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    public RateLimiter getGlobalRateLimiter() {
        return globalRateLimiter;
    }

    public void refreshHeartbeat() {
        this.lastValidHeartbeatTime = System.currentTimeMillis();
    }

    public boolean isStopped() {
        return isStopped;
    }

    public void enableCheckAliveThread() {
        HostClient hostClient = this;
        Debugger.debugOperation("AliveCheck thread started for " + hostClient.getIP());

        ThreadManager.runAsync(() -> {
            while (!hostClient.isStopped) {
                try {
                    String message = hostClient.hostServerHook.receiveStr(1000);

                    if (message == null) {
                        Debugger.debugOperation("Received null heartbeat from " + hostClient.getIP() + ". Closing.");
                        sayHostClientDiscInfo(hostClient, "HC-Checker:" + getKey().getName());
                        hostClient.close();
                        break;
                    } else if (EXPECTED_HEARTBEAT.equals(message)) {
                        hostClient.refreshHeartbeat();
                    } else {
                        Debugger.debugOperation("Received command from " + hostClient.getIP() + ": " + message);
                        hostClient.refreshHeartbeat();
                        handleHostClientCommand(message);
                    }

                } catch (SocketTimeoutException e) {
                    long currentTime = System.currentTimeMillis();
                    long timeSinceLastValidHeartbeat = currentTime - hostClient.lastValidHeartbeatTime;

                    if (timeSinceLastValidHeartbeat >= HEARTBEAT_TIMEOUT) {
                        boolean isBusy = !hostClient.activeTcpSockets.isEmpty();
                        if (isBusy) {
                            hostClient.refreshHeartbeat();
                            continue;
                        }

                        Debugger.debugOperation("Heartbeat timeout (" + timeSinceLastValidHeartbeat + "ms). Closing client " + hostClient.getIP());
                        sayHostClientDiscInfo(hostClient, "HC-Checker:Timeout:" + getKey().getName());
                        hostClient.close();
                        break;
                    }

                } catch (Exception e) {
                    debugOperation(e);
                    sayHostClientDiscInfo(hostClient, "HC-Checker:Exception:" + getKey().getName());
                    hostClient.close();
                    break;
                }
            }
            Debugger.debugOperation("AliveCheck thread stopped for " + hostClient.getIP());
        });
    }

    private void handleHostClientCommand(String message) {
        Debugger.debugOperation("Handling client command: " + message);
        String flags = message == null ? "" : message.trim();
        if (!flags.isEmpty() && !"T".equals(flags) && !"U".equals(flags) && !"TU".equals(flags)) {
            Debugger.debugOperation("Ignoring invalid protocol flags from client: " + message);
            return;
        }

        boolean enableTcp = flags.contains("T");
        boolean enableUdp = flags.contains("U");
        if (!canSwitchProtocolState(enableTcp, enableUdp)) {
            Debugger.debugOperation("Protocol switch rejected because port " + getOutPort() + " is not available for both TCP and UDP.");
            try {
                InternetOperator.sendStr(this, languageData.THE_PORT_HAS_ALREADY_BIND);
            } catch (IOException e) {
                debugOperation(e);
            }
            return;
        }

        ServerSocket openedTcpSocket = null;
        DatagramSocket openedUdpSocket = null;
        try {
            if (enableTcp && clientServerSocket == null) {
                openedTcpSocket = new ServerSocket(getOutPort());
            }
            if (enableUdp && clientDatagramSocket == null) {
                openedUdpSocket = new DatagramSocket(getOutPort());
            }

            if (!enableTcp && clientServerSocket != null) {
                Debugger.debugOperation("Client-Side TCP Socket closing.");
                cleanActiveTcpSockets();
                clientServerSocket.close();
                clientServerSocket = null;
            }
            if (!enableUdp && clientDatagramSocket != null) {
                Debugger.debugOperation("Client-Side UDP Socket closing.");
                clientDatagramSocket.close();
                clientDatagramSocket = null;
            }

            if (openedTcpSocket != null) {
                clientServerSocket = openedTcpSocket;
                openedTcpSocket = null;
                Debugger.debugOperation("Client-Side TCP Socket opened on port " + getOutPort());
            }
            if (openedUdpSocket != null) {
                clientDatagramSocket = openedUdpSocket;
                openedUdpSocket = null;
                Debugger.debugOperation("Client-Side UDP Socket opened on port " + getOutPort());
            }

            setTCPEnabled(enableTcp);
            setUDPEnabled(enableUdp);
        } catch (IOException e) {
            debugOperation(e);
        } finally {
            InternetOperator.close(openedTcpSocket, openedUdpSocket);
        }
    }

    private boolean canSwitchProtocolState(boolean enableTcp, boolean enableUdp) {
        if (!enableTcp && !enableUdp) {
            return true;
        }
        int port = getOutPort();
        boolean tcpOwned = clientServerSocket != null && !clientServerSocket.isClosed();
        boolean udpOwned = clientDatagramSocket != null && !clientDatagramSocket.isClosed();
        if (tcpOwned && udpOwned) {
            return true;
        }
        if (tcpOwned) {
            return InternetOperator.isUDPAvailable(port);
        }
        if (udpOwned) {
            return InternetOperator.isTCPAvailable(port);
        }
        return InternetOperator.isTCPAndUDPAvailable(port);
    }

    public void registerTcpSocket(Socket socket) {
        activeTcpSockets.add(socket);
    }

    public void unregisterTcpSocket(Socket socket) {
        activeTcpSockets.remove(socket);
    }

    // 【优化】返回 Collection 接口，兼容 ConsoleManager 的 for 循环
    public Collection<Socket> getActiveTcpSockets() {
        return activeTcpSockets;
    }

    public String[] formatAsTableRow(int count, boolean isRepresentative, List<HostClient> clientsInThisGroup) {
        String hostClientIP = this.getIP();
        String accessCode = this.getKey() != null ? this.getKey().getName() : "Unknown";
        String displayHostClientIP = hostClientIP;

        if (isRepresentative && count > 1) {
            displayHostClientIP += " (" + count + ")";
        }

        String location = this.getCachedLocation() != null ? this.getCachedLocation() : "Unknown";
        String isp = this.getCachedISP() != null ? this.getCachedISP() : "Unknown";

        // 新增：获取物理端口
        String port = String.valueOf(this.getOutPort());

        Map<String, Integer> tcpCounts = new HashMap<>();
        Map<String, Integer> udpCounts = new HashMap<>();

        for (HostClient hc : clientsInThisGroup) {
            for (Socket socket : hc.activeTcpSockets) {
                String clientIP = socket.getInetAddress().getHostAddress();
                tcpCounts.put(clientIP, tcpCounts.getOrDefault(clientIP, 0) + 1);
            }
        }

        try {
            for (UDPTransformer udp : UDPTransformer.udpClientConnections) {
                if (clientsInThisGroup.contains(udp.getHostClient())) {
                    String clientIP = udp.getClientIP();
                    udpCounts.put(clientIP, udpCounts.getOrDefault(clientIP, 0) + 1);
                }
            }
        } catch (Exception e) {
            // ignore
        }

        Set<String> allIPs = new HashSet<>();
        allIPs.addAll(tcpCounts.keySet());
        allIPs.addAll(udpCounts.keySet());

        StringBuilder sb = new StringBuilder();
        for (String ip : allIPs) {
            if (sb.length() > 0) sb.append("\n");
            int tcpCount = tcpCounts.getOrDefault(ip, 0);
            int udpCount = udpCounts.getOrDefault(ip, 0);
            sb.append(ip).append(" (T:").append(tcpCount).append(" U:").append(udpCount).append(")");
        }
        String externalClientIPs = sb.toString();
        if (externalClientIPs.isEmpty()) {
            externalClientIPs = "None";
        }

        // 修改返回值，在 ISP 和 externalClientIPs 之间插入 Port
        return new String[]{
                displayHostClientIP,
                accessCode,
                location,
                isp,
                port, // 新增的列
                externalClientIPs
        };
    }

    private void cleanActiveTcpSockets() {
        Debugger.debugOperation("Cleaning " + activeTcpSockets.size() + " active TCP sockets.");
        for (Socket socket : activeTcpSockets) {
            InternetOperator.close(socket);
        }
        activeTcpSockets.clear();
    }

    public void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }

        Debugger.debugOperation("Closing HostClient " + getIP());
        this.isStopped = true;

        if (this.remoteHeartbeatTask != null) {
            this.remoteHeartbeatTask.cancel(true);
            this.remoteHeartbeatTask = null;
        }

        cleanActiveTcpSockets();
        neoproxy.neoproxyserver.NeoProxyServer.availableHostClient.remove(this);
        neoproxy.neoproxyserver.core.InternetOperator.close(hostServerHook);

        this.setTCPEnabled(false);
        neoproxy.neoproxyserver.core.InternetOperator.close(clientServerSocket);

        this.setUDPEnabled(false);
        neoproxy.neoproxyserver.core.InternetOperator.close(clientDatagramSocket);

        if (this.sequenceKey != null) {
            Debugger.debugOperation("Releasing key " + this.sequenceKey.getName() + " on close.");
            neoproxy.neoproxyserver.core.management.SequenceKey.releaseKey(this.sequenceKey.getName());
        }
        Debugger.debugOperation("HostClient closed: " + getIP());
    }

    /**
     * [修改] 获取外部连接详情字符串
     * 变化：不再遍历 IP，而是直接统计该 HostClient 下的总连接数。
     * 格式：(T:5 U:2) 或 (T:1)
     * 目的：不在 NKM 列表中显示杂乱的 IP 地址，只显示负载情况。
     */
    public String getExternalConnectionsStr() {
        // 1. 统计 TCP 总连接数 (直接获取 Set 大小)
        int tcpCount = this.activeTcpSockets.size();

        // 2. 统计 UDP 总连接数 (遍历全局列表筛选属于自己的)
        int udpCount = 0;
        try {
            for (UDPTransformer udp : UDPTransformer.udpClientConnections) {
                if (udp.getHostClient() == this && udp.isRunning()) {
                    udpCount++;
                }
            }
        } catch (Exception e) {
            // 忽略并发遍历异常
        }

        // 如果没有连接，返回 None (NKM 端会处理为空白)
        if (tcpCount == 0 && udpCount == 0) {
            return "None";
        }

        // 3. 格式化输出 (只显示数量，不显示 IP)
        StringBuilder sb = new StringBuilder();
        boolean hasT = tcpCount > 0;
        boolean hasU = udpCount > 0;

        if (hasT && hasU) {
            sb.append("(T:").append(tcpCount).append(" U:").append(udpCount).append(")");
        } else if (hasT) {
            sb.append("(T:").append(tcpCount).append(")");
        } else if (hasU) {
            sb.append("(U:").append(udpCount).append(")");
        }

        return sb.toString();
    }

    public SequenceKey getKey() {
        return sequenceKey;
    }

    public void setKey(SequenceKey sequenceKey) {
        this.sequenceKey = sequenceKey;
        if (sequenceKey != null) {
            this.globalRateLimiter.setMaxMbps(sequenceKey.getRate());
        }
    }

    public SecureSocket getHostServerHook() {
        return hostServerHook;
    }

    public ServerSocket getClientServerSocket() {
        return clientServerSocket;
    }

    public void setClientServerSocket(ServerSocket clientServerSocket) {
        if (clientServerSocket != null) {
            this.clientServerSocket = clientServerSocket;
            setTCPEnabled(true);
        } else {
            this.clientServerSocket = null;
            setTCPEnabled(false);
        }
    }

    public DatagramSocket getClientDatagramSocket() {
        return clientDatagramSocket;
    }

    public void setClientDatagramSocket(DatagramSocket clientDatagramSocket) {
        if (clientDatagramSocket != null) {
            this.clientDatagramSocket = clientDatagramSocket;
            setUDPEnabled(true);
        } else {
            this.clientDatagramSocket = null;
            setUDPEnabled(false);
        }
    }

    public String getAddressAndPort() {
        return InternetOperator.getInternetAddressAndPort(hostServerHook);
    }

    public String getIP() {
        return InternetOperator.getIP(hostServerHook);
    }

    public LanguageData getLangData() {
        return languageData;
    }

    public void setLangData(LanguageData languageData) {
        this.languageData = languageData;
    }

    public int getOutPort() {
        return outPort;
    }

    public void setOutPort(int outPort) {
        this.outPort = outPort;
    }

    public String getCachedLocation() {
        return cachedLocation;
    }

    public void setCachedLocation(String cachedLocation) {
        this.cachedLocation = cachedLocation;
    }

    public String getCachedISP() {
        return cachedISP;
    }

    public void setCachedISP(String cachedISP) {
        this.cachedISP = cachedISP;
    }

    public boolean isTCPEnabled() {
        return isTCPEnabled;
    }

    public void setTCPEnabled(boolean TCPEnabled) {
        isTCPEnabled = TCPEnabled;
    }

    public boolean isUDPEnabled() {
        return isUDPEnabled;
    }

    public void setUDPEnabled(boolean UDPEnabled) {
        isUDPEnabled = UDPEnabled;
    }
}
