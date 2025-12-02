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
import java.util.concurrent.locks.LockSupport;

import static neoproxy.neoproxyserver.NeoProxyServer.*;
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

    // 【核心修改】全局唯一的限速器，默认 0 (不限速)
    private final RateLimiter globalRateLimiter = new RateLimiter(0);

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

    private volatile long lastValidHeartbeatTime = System.currentTimeMillis();

    public HostClient(SecureSocket hostServerHook) throws IOException {
        this.hostServerHook = hostServerHook;
        this.lastValidHeartbeatTime = System.currentTimeMillis();

        HostClient.enableAutoSaveThread(this);
        HostClient.enableKeyDetectionTread(this);
    }

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

    // 【新增方法】供 Transformer 获取共享限速器
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
                        if (IS_DEBUG_MODE) {
                            myConsole.log("HostClient-" + getKey().getName(), "Receive message: " + message);
                        }
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
            for (UDPTransformer udp : UDPTransformer.udpClientConnections) {
                if (allHostClientsForAccessCode.contains(udp.getHostClient())) {
                    String clientIP = udp.getClientIP();
                    udpCounts.put(clientIP, udpCounts.getOrDefault(clientIP, 0) + 1);
                }
            }
        } catch (Exception e) {
        }

        Set<String> allIPs = new HashSet<>();
        allIPs.addAll(tcpCounts.keySet());
        allIPs.addAll(udpCounts.keySet());

        StringBuilder sb = new StringBuilder();
        for (String ip : allIPs) {
            if (!sb.isEmpty()) sb.append("\n");
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

    @Override
    public void close() {
        cleanActiveTcpSockets();

        availableHostClient.remove(this);
        InternetOperator.close(hostServerHook);

        this.setTCPEnabled(false);
        InternetOperator.close(clientServerSocket);
        this.setUDPEnabled(false);
        InternetOperator.close(clientDatagramSocket);

        this.isStopped = true;
    }

    public SequenceKey getKey() {
        return sequenceKey;
    }

    public void setKey(SequenceKey sequenceKey) {
        this.sequenceKey = sequenceKey;
        // 【核心修改】当 Key 更新时，同步更新限速器的速率
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