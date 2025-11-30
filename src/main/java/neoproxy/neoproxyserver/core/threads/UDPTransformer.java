package neoproxy.neoproxyserver.core.threads;

import neoproxy.neoproxyserver.core.*;
import neoproxy.neoproxyserver.core.exceptions.NoMoreNetworkFlowException;
import plethora.management.bufferedFile.SizeCalculator;
import plethora.net.SecureSocket;
import plethora.thread.ThreadManager;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static neoproxy.neoproxyserver.NeoProxyServer.debugOperation;
import static neoproxy.neoproxyserver.core.InternetOperator.close;
import static neoproxy.neoproxyserver.core.threads.TCPTransformer.TELL_BALANCE_MIB;

public class UDPTransformer implements Runnable {
    // 存储所有活跃的UDP连接实例
    public static final CopyOnWriteArrayList<UDPTransformer> udpClientConnections = new CopyOnWriteArrayList<>();
    // 实例字段
    private final HostClient hostClient;
    private final HostReply hostReply;
    private final DatagramSocket sharedDatagramSocket;
    private final String clientIP;
    private final int clientOutPort;
    private final ArrayBlockingQueue<byte[]> sendQueue = new ArrayBlockingQueue<>(100);
    private volatile boolean isRunning = true;

    public UDPTransformer(HostClient hostClient, HostReply hostReply, DatagramSocket sharedDatagramSocket, String clientIP, int clientOutPort) {
        this.hostClient = hostClient;
        this.hostReply = hostReply;
        this.sharedDatagramSocket = sharedDatagramSocket;
        this.clientIP = clientIP;
        this.clientOutPort = clientOutPort;
    }

    // --- 以下静态方法保持不变 ---
    public static byte[] serializeDatagramPacket(DatagramPacket packet) {
        byte[] data = packet.getData();
        int offset = packet.getOffset();
        int length = packet.getLength();
        InetAddress address = packet.getAddress();
        int port = packet.getPort();

        byte[] ipBytes = address.getAddress();
        int ipLength = ipBytes.length;

        int totalLen = 4 + 4 + 4 + ipLength + 2 + length;
        ByteBuffer buffer = ByteBuffer.allocate(totalLen);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(0xDEADBEEF);
        buffer.putInt(length);
        buffer.putInt(ipLength);
        buffer.put(ipBytes);
        buffer.putShort((short) port);
        buffer.put(data, offset, length);

        return buffer.array();
    }

    public static DatagramPacket deserializeToDatagramPacket(byte[] serializedData) {
        if (serializedData == null || serializedData.length < 14) {
            debugOperation(new IllegalArgumentException("Serialized data is too short or null"));
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(serializedData);
        buffer.order(ByteOrder.BIG_ENDIAN);

        int magic = buffer.getInt();
        if (magic != 0xDEADBEEF) {
            debugOperation(new IllegalArgumentException("Invalid magic number in serialized data"));
            return null;
        }

        int dataLen = buffer.getInt();
        int ipLen = buffer.getInt();

        if (dataLen < 0 || dataLen > 65507 || ipLen != 4 && ipLen != 16) {
            debugOperation(new IllegalArgumentException("Invalid data or IP length in serialized data"));
            return null;
        }

        int expectedLength = 4 + 4 + 4 + ipLen + 2 + dataLen;
        if (serializedData.length < expectedLength) {
            debugOperation(new IllegalArgumentException("Incomplete serialized data"));
            return null;
        }

        byte[] ipBytes = new byte[ipLen];
        buffer.get(ipBytes);
        InetAddress address;
        try {
            address = InetAddress.getByAddress(ipBytes);
        } catch (Exception e) {
            debugOperation(e);
            return null;
        }
        int port = buffer.getShort() & 0xFFFF;
        byte[] data = new byte[dataLen];
        buffer.get(data);

        return new DatagramPacket(data, data.length, address, port);
    }

    public static void tellRestBalance(HostClient hostClient, double[] aTenMibSize, int len, LanguageData languageData) {
        if (aTenMibSize[0] < TELL_BALANCE_MIB) {
            aTenMibSize[0] = aTenMibSize[0] + SizeCalculator.byteToMib(len);
        } else {
            try {
                InternetOperator.sendStr(hostClient, languageData.THIS_ACCESS_CODE_HAVE + hostClient.getKey().getBalance() + languageData.MB_OF_FLOW_LEFT);
            } catch (IOException e) {
                debugOperation(e);
            }
            aTenMibSize[0] = 0;
        }
    }

    public static void kickAllWithMsg(HostClient hostClient, SecureSocket host) {
        close(host);
        try {
            InternetOperator.sendCommand(hostClient, "exitNoFlow");
            ServerLogger.sayHostClientDiscInfo(hostClient, "UDPTransformer");
        } catch (Exception e) {
            ServerLogger.sayHostClientDiscInfo(hostClient, "UDPTransformer");
        }
        close(hostClient);
    }

    // --- 实例方法 ---
    public HostClient getHostClient() {
        return hostClient;
    }

    public int getClientOutPort() {
        return clientOutPort;
    }

    public String getClientIP() {
        return clientIP;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean addPacketToSend(byte[] serializedPacket) {
        if (isRunning) {
            return sendQueue.offer(serializedPacket);
        }
        return false;
    }

    /**
     * 从队列中消费数据包并发送给客户端A (C -> B -> A)
     */
    private void outClientToHostClient(double[] aTenMibSize) {
        try {
            RateLimiter limiter = new RateLimiter(hostClient.getKey().getRate());
            byte[] data;
            while (isRunning && (data = sendQueue.poll(1, TimeUnit.SECONDS)) != null) {
                int enLength = hostReply.host().sendByte(data);
                // 【关键】mineMib 会抛出 NoMoreNetworkFlowException
                hostClient.getKey().mineMib("UDP-Transformer", SizeCalculator.byteToMib(enLength + 10));
                tellRestBalance(hostClient, aTenMibSize, enLength, hostClient.getLangData());
                RateLimiter.setMaxMbps(limiter, hostClient.getKey().getRate());
                limiter.onBytesTransferred(enLength);
            }
        } catch (Exception e) {
            // 【关键】捕获所有异常，包括 NoMoreNetworkFlowException 和 IOException
            // 不在这里处理，而是让 ThreadManager 收集，在 run 方法中统一处理
            debugOperation(e);
        } finally {
            stop();
        }
    }

    /**
     * 从客户端A接收数据，并发送给外部客户端C (A -> B -> C)
     */
    private void hostClientToOutClient(double[] aTenMibSize) {
        try {
            RateLimiter limiter = new RateLimiter(hostClient.getKey().getRate());
            byte[] data;
            while (isRunning && (data = hostReply.host().receiveByte()) != null) {
                DatagramPacket packetToClient = deserializeToDatagramPacket(data);
                if (packetToClient != null) {
                    int packetLength = packetToClient.getLength();
                    // 【关键】mineMib 会抛出 NoMoreNetworkFlowException
                    hostClient.getKey().mineMib("UDP-Transformer", SizeCalculator.byteToMib(packetLength + 10));
                    tellRestBalance(hostClient, aTenMibSize, packetLength, hostClient.getLangData());
                    RateLimiter.setMaxMbps(limiter, hostClient.getKey().getRate());
                    limiter.onBytesTransferred(packetLength);

                    DatagramPacket outgoingPacket = new DatagramPacket(
                            packetToClient.getData(),
                            packetToClient.getLength(),
                            InetAddress.getByName(clientIP),
                            clientOutPort
                    );
                    sharedDatagramSocket.send(outgoingPacket); // 【关键】这里也会抛出 IOException
                }
            }
        } catch (Exception e) {
            // 【关键】捕获所有异常，包括 NoMoreNetworkFlowException 和 IOException
            debugOperation(e);
        } finally {
            stop();
        }
    }

    @Override
    public void run() {
        try {
            final double[] aTenMibSize = {0};
            Runnable clientToHostClientThread = () -> outClientToHostClient(aTenMibSize);
            Runnable hostClientToClientThread = () -> hostClientToOutClient(aTenMibSize);

            ThreadManager threadManager = new ThreadManager(clientToHostClientThread, hostClientToClientThread);
            // 【关键】阻塞等待，直到两个数据流任务都结束（无论是正常结束还是异常结束）
            List<Throwable> exceptions = threadManager.start();

            // 【关键】检查 ThreadManager 收集到的异常
            for (Throwable t : exceptions) {
                if (t instanceof NoMoreNetworkFlowException) {
                    // 如果是流量耗尽异常，执行踢下线操作
                    kickAllWithMsg(hostClient, hostReply.host());
                    // 注意：kickAllWithMsg 已经包含了 close(hostClient) 的逻辑，所以可以提前返回
                    return;
                }
            }
        } catch (Exception ignore) {
            // ThreadManager.start() 本身很少会抛异常，这里通常不会执行
        } finally {
            // 【关键】无论何种原因结束，都执行常规的资源清理
            // 如果不是因为流量耗尽而退出，这里的清理是必要的
            close(hostReply.host());
            udpClientConnections.remove(this);
            ServerLogger.sayClientUDPConnectDestroyInfo(hostClient, clientIP + ":" + clientOutPort);
        }
    }

    private void stop() {
        if (isRunning) {
            isRunning = false;
            InternetOperator.close(hostReply.host());
        }
    }
}