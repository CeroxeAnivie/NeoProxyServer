package neoproject.neoproxy.core.threads;

import neoproject.neoproxy.core.*;
import neoproject.neoproxy.core.exceptions.NoMoreNetworkFlowException;
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

import static neoproject.neoproxy.NeoProxyServer.debugOperation;
import static neoproject.neoproxy.core.InternetOperator.close;
import static neoproject.neoproxy.core.threads.TCPTransformer.TELL_BALANCE_MIB;

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

    // 私有构造函数，防止外部直接实例化
    public UDPTransformer(HostClient hostClient, HostReply hostReply, DatagramSocket sharedDatagramSocket, String clientIP, int clientOutPort) {
        this.hostClient = hostClient;
        this.hostReply = hostReply;
        this.sharedDatagramSocket = sharedDatagramSocket;
        this.clientIP = clientIP;
        this.clientOutPort = clientOutPort;
    }

    /**
     * 将一个 {@link DatagramPacket} 序列化为字节数组。
     * 序列化格式：[Magic(4)] [DataLen(4)] [IPLen(4)] [IPBytes(n)] [Port(2)] [Data(len)]
     *
     * @param packet 要序列化的UDP数据包。
     * @return 包含完整数据包信息的字节数组。
     */
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

    /**
     * 将字节数组反序列化为一个 {@link DatagramPacket}。
     *
     * @param serializedData 序列化后的字节数组。
     * @return 重建的DatagramPacket，如果数据无效则返回null。
     */
    public static DatagramPacket deserializeToDatagramPacket(byte[] serializedData) {
        if (serializedData == null || serializedData.length < 14) { // 最小长度检查: 4+4+4+2
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

        // 防御性检查
        if (dataLen < 0 || dataLen > 65507 || ipLen != 4 && ipLen != 16) {
            debugOperation(new IllegalArgumentException("Invalid data or IP length in serialized data"));
            return null;
        }

        // 检查缓冲区是否有足够的数据
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
        int port = buffer.getShort() & 0xFFFF; // Convert unsigned short
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
        // 注意：不能关闭共享的 clientSocket，只能关闭到客户端A的连接和整个HostClient
        close(host);
        try {
            InternetOperator.sendCommand(hostClient, "exitNoFlow");
            InfoBox.sayHostClientDiscInfo(hostClient, "UDPTransformer");
        } catch (Exception e) {
            InfoBox.sayHostClientDiscInfo(hostClient, "UDPTransformer");
        }
        close(hostClient);
    }

    // --- 实例方法 ---

    public int getClientOutPort() {
        return clientOutPort;
    }

    public String getClientIP() {
        return clientIP;
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 主线程调用此方法，将序列化后的数据包放入队列
     */
    public boolean addPacketToSend(byte[] serializedPacket) {
        if (isRunning) {
            // 满了的包会被丢弃
            return sendQueue.offer(serializedPacket);
        }
        return false;
    }

    /**
     * 从队列中消费数据包并发送给客户端A (C -> B -> A)
     * 对应 TCPTransformer.HostToClient
     */
    private void outClientToHostClient(double[] aTenMibSize) {
        try {
            RateLimiter limiter = new RateLimiter(hostClient.getKey().getRate());
            byte[] data;
            while (isRunning && (data = sendQueue.poll(1, TimeUnit.SECONDS)) != null) {
                int enLength = hostReply.host().sendByte(data);

                hostClient.getKey().mineMib("UDP-Transformer", SizeCalculator.byteToMib(enLength + 10)); // +10 for protocol overhead
                tellRestBalance(hostClient, aTenMibSize, enLength, hostClient.getLangData());

                RateLimiter.setMaxMbps(limiter, hostClient.getKey().getRate());
                limiter.onBytesTransferred(enLength);
            }
        } catch (NoMoreNetworkFlowException e) {
            // 流量耗尽，踢下线
            kickAllWithMsg(hostClient, hostReply.host());
        } catch (Exception e) {
            debugOperation(e);
        } finally {
            stop();
        }
    }

    /**
     * 从客户端A接收数据，并发送给外部客户端C (A -> B -> C)
     * 对应 TCPTransformer.ClientToHost
     */
    private void hostClientToOutClient(double[] aTenMibSize) {
        try {
            RateLimiter limiter = new RateLimiter(hostClient.getKey().getRate());
            byte[] data;
            while (isRunning && (data = hostReply.host().receiveByte()) != null) {
                DatagramPacket packetToClient = deserializeToDatagramPacket(data);
                if (packetToClient != null) {
                    // 扣费和限速在发送前进行
                    int packetLength = packetToClient.getLength();
                    hostClient.getKey().mineMib("UDP-Transformer", SizeCalculator.byteToMib(packetLength + 10)); // +10 for protocol overhead
                    tellRestBalance(hostClient, aTenMibSize, packetLength, hostClient.getLangData());

                    RateLimiter.setMaxMbps(limiter, hostClient.getKey().getRate());
                    limiter.onBytesTransferred(packetLength);

                    DatagramPacket outgoingPacket = new DatagramPacket(
                            packetToClient.getData(),
                            packetToClient.getLength(),
                            InetAddress.getByName(clientIP),
                            clientOutPort
                    );
                    sharedDatagramSocket.send(outgoingPacket);
                }
            }
        } catch (NoMoreNetworkFlowException e) {
            kickAllWithMsg(hostClient, hostReply.host());
        } catch (Exception e) {
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
            List<Throwable> exceptions = threadManager.start();

            // 检查是否有流量耗尽的异常
            for (Throwable t : exceptions) {
                if (t instanceof NoMoreNetworkFlowException) {
                    kickAllWithMsg(hostClient, hostReply.host());
                    break;
                }
            }

        } catch (Exception ignore) {
            // ThreadManager 会捕获大部分异常，这里通常不会执行
        } finally {
            // 确保资源被释放
            close(hostReply.host());
            udpClientConnections.remove(this);
            InfoBox.sayClientUDPConnectDestroyInfo(hostClient, clientIP + ":" + clientOutPort);
        }
    }

    private void stop() {
        if (isRunning) {
            isRunning = false;
            udpClientConnections.remove(this);
            close(hostReply.host());
        }
    }
}