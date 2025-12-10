package neoproxy.neoproxyserver.core;

import neoproxy.neoproxyserver.core.management.SequenceKey;
import neoproxy.neoproxyserver.core.threads.RateLimiter;
import neoproxy.neoproxyserver.core.threads.UDPTransformer;
import plethora.net.SecureSocket;
import plethora.thread.ThreadManager;
import plethora.utils.Sleeper;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static neoproxy.neoproxyserver.NeoProxyServer.debugOperation;
import static neoproxy.neoproxyserver.core.ServerLogger.sayHostClientDiscInfo;
import static neoproxy.neoproxyserver.core.management.SequenceKey.saveToDB;

public final class HostClient implements Closeable {
    private static final String EXPECTED_HEARTBEAT = "PING";
    public static int SAVE_DELAY = 3000;
    public static int DETECTION_DELAY = 1000;
    public static int AES_KEY_SIZE = 128;
    public static int HEARTBEAT_TIMEOUT = 30000;

    private final SecureSocket hostServerHook;
    private final CopyOnWriteArrayList<Socket> activeTcpSockets = new CopyOnWriteArrayList<>();
    private final RateLimiter globalRateLimiter = new RateLimiter(0);
    // 使用 AtomicBoolean 防止 close 重复调用
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private boolean isStopped = false;
    private SequenceKey sequenceKey = null;
    private ServerSocket clientServerSocket = null;
    private DatagramSocket clientDatagramSocket = null;
    private LanguageData languageData = new LanguageData();
    private int outPort = -1;
    private String cachedLocation;
    private String cachedISP;
    private boolean isTCPEnabled = true;
    private boolean isUDPEnabled = true;
    // NKM 远程心跳任务
    private ScheduledFuture<?> remoteHeartbeatTask;
    private volatile long lastValidHeartbeatTime = System.currentTimeMillis();

    public HostClient(SecureSocket hostServerHook) throws IOException {
        this.hostServerHook = hostServerHook;
        this.lastValidHeartbeatTime = System.currentTimeMillis();

        HostClient.enableAutoSaveThread(this);
        HostClient.enableKeyDetectionTread(this);
    }

    // ... 在 HostClient 类中添加 ...

    private static void enableAutoSaveThread(HostClient hostClient) {
        ThreadManager.runAsync(() -> {
            while (!hostClient.isStopped) {
                if (hostClient.getKey() != null) {
                    saveToDB(hostClient.getKey());
                }
                Sleeper.sleep(SAVE_DELAY);
            }
        });
    }

    private static void enableKeyDetectionTread(HostClient hostClient) {
        ThreadManager.runAsync(() -> {
            while (!hostClient.isStopped) {
                if (hostClient.getKey() != null && hostClient.getKey().isOutOfDate()) {
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
                    hostClient.close();
                    break;
                }

                Sleeper.sleep(DETECTION_DELAY);
            }
        });
    }

    public static void waitForTcpEnabled(HostClient hostClient) {
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
        } catch (InterruptedException e) {
            debugOperation(e);
        }
    }

    public static void waitForUDPEnabled(HostClient hostClient) {
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
        } catch (InterruptedException e) {
            debugOperation(e);
        }
    }

    /**
     * 【新增】应用动态更新
     * 当 RemoteKeyProvider 拉取到最新 Key 信息后调用此方法
     */
    public void applyDynamicUpdates() {
        if (this.sequenceKey != null) {
            // 立即更新全局限速器的速率
            // RateLimiter 内部 setMaxMbps 应该是线程安全的，或者只是简单的 volatile 赋值
            this.globalRateLimiter.setMaxMbps(this.sequenceKey.getRate());

            // 如果有其他需要动态调整的参数（如日志级别、特殊标记），也可以在此处处理
        }
    }

    public void startRemoteHeartbeat() {
        if (this.sequenceKey == null || this.remoteHeartbeatTask != null) {
            return;
        }

        Runnable task = () -> {
            if (isStopped() || this.sequenceKey == null) {
                return;
            }

            try {
                neoproxy.neoproxyserver.core.management.provider.Protocol.HeartbeatPayload payload =
                        new neoproxy.neoproxyserver.core.management.provider.Protocol.HeartbeatPayload();

                payload.serial = this.sequenceKey.getName();
                payload.nodeId = neoproxy.neoproxyserver.core.ConfigOperator.NODE_ID;
                payload.port = String.valueOf(this.outPort);
                payload.timestamp = System.currentTimeMillis();
                payload.currentConnections = this.activeTcpSockets.size();

                boolean keepAlive = neoproxy.neoproxyserver.core.management.SequenceKey.PROVIDER.sendHeartbeat(payload);

                if (!keepAlive) {
                    // Log: Key {0} was kicked by manager (Kill signal received).
                    neoproxy.neoproxyserver.core.ServerLogger.warn("hostClient.kickedByManager", this.sequenceKey.getName());
                    this.close();
                }
            } catch (Exception e) {
                // Log: Heartbeat failed: {0}
                neoproxy.neoproxyserver.core.ServerLogger.warn("hostClient.heartbeatError", e.getMessage());
            }
        };

        this.remoteHeartbeatTask = plethora.thread.ThreadManager.getScheduledExecutor().scheduleAtFixedRate(
                task,
                0,
                neoproxy.neoproxyserver.core.management.provider.Protocol.HEARTBEAT_INTERVAL_MS,
                java.util.concurrent.TimeUnit.MILLISECONDS
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

        ThreadManager.runAsync(() -> {
            while (!hostClient.isStopped) {
                try {
                    String message = hostClient.hostServerHook.receiveStr(1000);

                    if (message == null) {
                        sayHostClientDiscInfo(hostClient, "HC-Checker:" + getKey().getName());
                        hostClient.close();
                        break;
                    } else if (EXPECTED_HEARTBEAT.equals(message)) {
                        hostClient.refreshHeartbeat();
                    } else {
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

                        debugOperation(e);
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
        });
    }

    private void handleHostClientCommand(String message) {
        if (message.startsWith("T")) {
            if (clientServerSocket == null) {
                try {
                    clientServerSocket = new ServerSocket(getOutPort());
                } catch (IOException e) {
                    debugOperation(e);
                }
            }
            setTCPEnabled(true);
        } else {
            setTCPEnabled(false);
            if (clientServerSocket != null) {
                try {
                    cleanActiveTcpSockets();
                    clientServerSocket.close();
                    clientServerSocket = null;
                } catch (IOException e) {
                    debugOperation(e);
                }
            }
        }
        if (message.endsWith("U")) {
            if (clientDatagramSocket == null) {
                try {
                    clientDatagramSocket = new DatagramSocket(getOutPort());
                } catch (IOException e) {
                    debugOperation(e);
                }
            }
            setUDPEnabled(true);
        } else {
            setUDPEnabled(false);
            if (clientDatagramSocket != null) {
                clientDatagramSocket.close();
                clientDatagramSocket = null;
            }
        }
    }

    public void registerTcpSocket(Socket socket) {
        activeTcpSockets.add(socket);
    }

    public void unregisterTcpSocket(Socket socket) {
        activeTcpSockets.remove(socket);
    }

    public CopyOnWriteArrayList<Socket> getActiveTcpSockets() {
        return activeTcpSockets;
    }

    // 【新增】格式化方法，供 ConsoleManager 使用
    public String[] formatAsTableRow(Map<String, Integer> accessCodeCounts, boolean isRepresentative, List<HostClient> allHostClientsForAccessCode) {
        String hostClientIP = this.getHostServerHook().getInetAddress().getHostAddress();
        String accessCode = this.getKey() != null ? this.getKey().getName() : "Unknown";
        String displayHostClientIP = hostClientIP;
        if (isRepresentative) {
            int count = accessCodeCounts.getOrDefault(accessCode, 0);
            if (count > 1) {
                displayHostClientIP += " (" + count + ")";
            }
        }

        String location = this.getCachedLocation() != null ? this.getCachedLocation() : "Unknown";
        String isp = this.getCachedISP() != null ? this.getCachedISP() : "Unknown";

        Map<String, Integer> tcpCounts = new HashMap<>();
        Map<String, Integer> udpCounts = new HashMap<>();

        for (HostClient hc : allHostClientsForAccessCode) {
            for (Socket socket : hc.activeTcpSockets) {
                String clientIP = socket.getInetAddress().getHostAddress();
                tcpCounts.put(clientIP, tcpCounts.getOrDefault(clientIP, 0) + 1);
            }
        }

        try {
            // 注意：这里引用了 UDPTransformer，请确保该类存在且 public 字段可访问
            for (UDPTransformer udp : UDPTransformer.udpClientConnections) {
                if (allHostClientsForAccessCode.contains(udp.getHostClient())) {
                    String clientIP = udp.getClientIP();
                    udpCounts.put(clientIP, udpCounts.getOrDefault(clientIP, 0) + 1);
                }
            }
        } catch (Exception e) {
            // 忽略并发修改异常
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

        return new String[]{
                displayHostClientIP,
                accessCode,
                location,
                isp,
                externalClientIPs
        };
    }

    private void cleanActiveTcpSockets() {
        for (Socket socket : activeTcpSockets) {
            InternetOperator.close(socket);
        }
        activeTcpSockets.clear();
    }

    public void close() {
        // 使用 CAS 原子操作确保只执行一次关闭逻辑，防止递归调用或多线程重复关闭
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }

        this.isStopped = true;

        // 1. 停止远程心跳任务，防止内存泄漏和无意义的网络请求
        if (this.remoteHeartbeatTask != null) {
            this.remoteHeartbeatTask.cancel(true); // true 表示如果正在运行则强制中断
            this.remoteHeartbeatTask = null;
        }

        // 2. 清理所有活跃的 TCP 连接
        cleanActiveTcpSockets();

        // 3. 从全局列表中移除自己
        neoproxy.neoproxyserver.NeoProxyServer.availableHostClient.remove(this);

        // 4. 关闭与 Host 的控制通道 (Hook Socket)
        neoproxy.neoproxyserver.core.InternetOperator.close(hostServerHook);

        // 5. 关闭监听的 ServerSocket (TCP)
        this.setTCPEnabled(false);
        neoproxy.neoproxyserver.core.InternetOperator.close(clientServerSocket);

        // 6. 关闭监听的 DatagramSocket (UDP)
        this.setUDPEnabled(false);
        neoproxy.neoproxyserver.core.InternetOperator.close(clientDatagramSocket);

        // 7. 通知 KeyProvider 释放 Key (发送 /api/release)
        // 注意：如果是 Remote 模式，这将告诉 NKM 该节点已下线
        if (this.sequenceKey != null) {
            neoproxy.neoproxyserver.core.management.SequenceKey.releaseKey(this.sequenceKey.getName());
        }
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