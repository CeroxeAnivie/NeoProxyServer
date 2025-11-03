package neoproject.neoproxy.core;

import neoproject.neoproxy.core.management.SequenceKey;
import neoproject.neoproxy.core.threads.UDPTransformer;
import plethora.net.SecureSocket;
import plethora.utils.Sleeper;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static neoproject.neoproxy.NeoProxyServer.*;
import static neoproject.neoproxy.core.ServerLogger.alert;
import static neoproject.neoproxy.core.ServerLogger.sayHostClientDiscInfo;
import static neoproject.neoproxy.core.management.SequenceKey.saveToDB;

public final class HostClient implements Closeable {
    // 心跳验证相关常量
    private static final String EXPECTED_HEARTBEAT = "PING";
    public static int SAVE_DELAY = 3000;//3s
    public static int DETECTION_DELAY = 1000;
    public static int AES_KEY_SIZE = 128;
    public static int HEARTBEAT_TIMEOUT = 5000; // 5秒超时，可配置
    private final SecureSocket hostServerHook;
    // 用于跟踪所有由该 HostClient 创建的活跃 TCP 连接
    private final CopyOnWriteArrayList<Socket> activeTcpSockets = new CopyOnWriteArrayList<>();
    private boolean isStopped = false;
    private SequenceKey sequenceKey = null;
    private ServerSocket clientServerSocket = null;
    private DatagramSocket clientDatagramSocket = null;
    private LanguageData languageData = new LanguageData();
    private int outPort = -1;
    // 用于缓存地理位置信息的字段
    private String cachedLocation;
    private String cachedISP;

    public HostClient(SecureSocket hostServerHook) throws IOException {
        this.hostServerHook = hostServerHook;

        HostClient.enableAutoSaveThread(this);
        HostClient.enableKeyDetectionTread(this);
    }

    private static void enableAutoSaveThread(HostClient hostClient) {
        Thread a = new Thread(() -> {
            while (!hostClient.isStopped) {
                if (hostClient.getKey() != null) {
                    saveToDB(hostClient.getKey());
                }
                Sleeper.sleep(SAVE_DELAY);
            }
        });
        a.start();
    }

    // MODIFIED: Use ServerLogger
    private static void enableKeyDetectionTread(HostClient hostClient) {
        Thread a = new Thread(() -> {
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
        a.start();
    }

    public boolean isStopped() {
        return isStopped;
    }

    public void enableCheckAliveThread() {
        HostClient hostClient = this;

        Thread heartbeatCheckThread = new Thread(() -> {
            // 记录最后一次收到有效心跳（"PING"）的时间戳。
            // 初始化为0，表示尚未收到任何有效心跳。
            long lastValidHeartbeatTime = 0;
            // 记录连接建立的时间，用于在收到第一个心跳前计算超时。
            long connectionEstablishedTime = System.currentTimeMillis();

            while (!hostClient.isStopped) {
                try {
                    // 使用固定的1秒超时来接收消息。这既是轮询间隔，也避免了CPU空转。
                    // 客户端也是1秒发一次，这个频率很匹配。
                    String message = hostClient.hostServerHook.receiveStr(1000);

                    if (message == null) {
                        // receiveStr() 返回 null，表示客户端正常关闭了连接
                        sayHostClientDiscInfo(hostClient, Thread.currentThread().getName());
                        hostClient.close();
                        break;
                    } else if (EXPECTED_HEARTBEAT.equals(message)) {
                        // 1. 收到的是有效心跳包 "PING"
                        lastValidHeartbeatTime = System.currentTimeMillis(); // 更新最后有效心跳时间
                    } else {
                        // 2. 收到的是无效数据包，按需求丢弃，什么都不做（除了记录日志）
                        if (alert) {
                            myConsole.warn(Thread.currentThread().getName(), "Detected invalid packet, discarding: " + message);
                        }
                        // 注意：这里绝对不能更新 lastValidHeartbeatTime
                    }

                } catch (SocketTimeoutException e) {
                    // 这个超时是预期的！它表示1秒内没有收到任何消息。
                    // 现在是检查心跳是否真正超时的最佳时机。
                    long currentTime = System.currentTimeMillis();

                    // 确定计算超时的起点：如果还没收到过心跳，就从连接建立时算起
                    long startTime = (lastValidHeartbeatTime == 0) ? connectionEstablishedTime : lastValidHeartbeatTime;

                    long timeSinceLastValidHeartbeat = currentTime - startTime;

                    if (timeSinceLastValidHeartbeat >= HEARTBEAT_TIMEOUT) {
                        // 已经超过允许的最大心跳间隔，判断客户端离线
//                        myConsole.warn(Thread.currentThread().getName(),
//                                "Client heartbeat timeout. No PING received for " + timeSinceLastValidHeartbeat + "ms.");
                        debugOperation(e);
                        sayHostClientDiscInfo(hostClient, Thread.currentThread().getName());
                        hostClient.close();
                        break;
                    }
                    // 如果未超时，则继续循环，等待下一次1秒超时或消息到来

                } catch (Exception e) {
                    // 发生其他IO异常（如网络中断、Broken pipe等），连接不可靠，直接关闭
                    debugOperation(e);
                    sayHostClientDiscInfo(hostClient, Thread.currentThread().getName());
                    hostClient.close();
                    break;
                }
            }
        });

        heartbeatCheckThread.setName("HeartbeatCheck-" + hostClient.getKey().getName());
        heartbeatCheckThread.setDaemon(true); // 设置为守护线程，不阻止JVM退出
        heartbeatCheckThread.start();
    }

    /**
     * 【新增】注册一个新的TCP连接。
     * 当一个新的TCP连接被接受时，TCPTransformer应调用此方法。
     *
     * @param socket 新建立的TCP连接Socket。
     */
    public void registerTcpSocket(Socket socket) {
        activeTcpSockets.add(socket);
    }

    /**
     * 【新增】注销一个TCP连接。
     * 当一个TCP连接的转发线程结束时，TCPTransformer应调用此方法。
     *
     * @param socket 已结束的TCP连接Socket。
     */
    public void unregisterTcpSocket(Socket socket) {
        activeTcpSockets.remove(socket);
    }

    /**
     * 【新增】获取活跃的TCP连接列表，用于list命令。
     *
     * @return 活跃TCP连接的列表
     */
    public CopyOnWriteArrayList<Socket> getActiveTcpSockets() {
        return activeTcpSockets;
    }

    /**
     * 【新增】格式化HostClient信息为表格行数组，用于list命令。
     * 注意：此方法返回的数据用于表格显示，其内容（IP, Location等）不应被翻译。
     *
     * @param accessCodeCounts            Access Code计数映射，用于显示相同Access Code的数量
     * @param isRepresentative            是否为该Access Code的代表实例
     * @param allHostClientsForAccessCode 该Access Code的所有HostClient实例
     * @return 包含格式化信息的字符串数组
     */
    public String[] formatAsTableRow(Map<String, Integer> accessCodeCounts, boolean isRepresentative, List<HostClient> allHostClientsForAccessCode) {
        String hostClientIP = this.getHostServerHook().getInetAddress().getHostAddress();

        // 【新增】获取Access Code
        String accessCode = this.getKey() != null ? this.getKey().getName() : "Unknown";

        // 【修正】只对代表实例显示括号数字
        String displayHostClientIP = hostClientIP;
        if (isRepresentative) {
            int count = accessCodeCounts.getOrDefault(accessCode, 0);
            if (count > 1) {
                displayHostClientIP += " (" + count + ")";
            }
        }

        // 获取或使用缓存的位置和ISP信息
        String location = this.getCachedLocation() != null ? this.getCachedLocation() : "Unknown";
        String isp = this.getCachedISP() != null ? this.getCachedISP() : "Unknown";

        // 【修正】统计该Access Code所有实例的外部连接信息
        Map<String, Integer> tcpCounts = new HashMap<>();
        Map<String, Integer> udpCounts = new HashMap<>();

        // 收集该Access Code所有实例的外部客户端IP并计数
        for (HostClient hc : allHostClientsForAccessCode) {
            for (Socket socket : hc.activeTcpSockets) {
                String clientIP = socket.getInetAddress().getHostAddress();
                tcpCounts.put(clientIP, tcpCounts.getOrDefault(clientIP, 0) + 1);
            }
        }

        // 收集该Access Code所有实例的UDP连接
        try {
            for (UDPTransformer udp : UDPTransformer.udpClientConnections) {
                if (allHostClientsForAccessCode.contains(udp.getHostClient())) {
                    String clientIP = udp.getClientIP();
                    udpCounts.put(clientIP, udpCounts.getOrDefault(clientIP, 0) + 1);
                }
            }
        } catch (Exception e) {
            // 忽略异常
        }

        // 合并所有IP地址
        Set<String> allIPs = new HashSet<>();
        allIPs.addAll(tcpCounts.keySet());
        allIPs.addAll(udpCounts.keySet());

        // 格式化外部客户端IP显示，使用换行符分隔
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

        // 【修正】去掉序列号列
        return new String[]{
                displayHostClientIP,
                accessCode,  // Access Code列
                location,
                isp,
                externalClientIPs
        };
    }

    @Override
    public void close() {
        // 先关闭所有活跃的TCP连接
        for (Socket socket : activeTcpSockets) {
            InternetOperator.close(socket);
        }
        activeTcpSockets.clear();

        // 然后执行原有的关闭逻辑
        availableHostClient.remove(this);
        InternetOperator.close(hostServerHook);
        InternetOperator.close(clientDatagramSocket);
        InternetOperator.close(clientServerSocket);

        this.isStopped = true;
    }


    public SequenceKey getKey() {
        return sequenceKey;
    }

    public void setKey(SequenceKey sequenceKey) {
        this.sequenceKey = sequenceKey;
    }

    public SecureSocket getHostServerHook() {
        return hostServerHook;
    }

    public ServerSocket getClientServerSocket() {
        return clientServerSocket;
    }

    public void setClientServerSocket(ServerSocket clientServerSocket) {
        this.clientServerSocket = clientServerSocket;
    }

    public DatagramSocket getClientDatagramSocket() {
        return clientDatagramSocket;
    }

    public void setClientDatagramSocket(DatagramSocket clientDatagramSocket) {
        this.clientDatagramSocket = clientDatagramSocket;
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

    // 【新增】缓存位置和ISP信息的getter和setter
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
}