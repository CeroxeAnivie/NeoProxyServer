package neoproxy.neoproxyserver.core.threads;

import fun.ceroxe.api.management.bufferedFile.SizeCalculator;
import fun.ceroxe.api.net.SecureSocket;
import fun.ceroxe.api.thread.ThreadManager;
import neoproxy.neoproxyserver.core.*;
import neoproxy.neoproxyserver.core.exceptions.NoMoreNetworkFlowException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static neoproxy.neoproxyserver.core.Debugger.debugOperation;
import static neoproxy.neoproxyserver.core.InternetOperator.close;
import static neoproxy.neoproxyserver.core.threads.TCPTransformer.TELL_BALANCE_MIB;

public class UDPTransformer implements Runnable {
    // 保持 CopyOnWriteArrayList 以兼容 Server 的调用
    public static final CopyOnWriteArrayList<UDPTransformer> udpClientConnections = new CopyOnWriteArrayList<>();

    private final HostClient hostClient;
    private final HostReply hostReply;
    private final DatagramSocket sharedDatagramSocket;
    private final String clientIP;
    private final int clientOutPort;

    // 优化：增大队列缓冲，配合批处理提升吞吐
    private final ArrayBlockingQueue<byte[]> sendQueue = new ArrayBlockingQueue<>(512);
    private volatile boolean isRunning = true;

    // 优化：缓存InetAddress，避免高频DNS解析/对象创建
    private InetAddress cachedClientAddress;

    public UDPTransformer(HostClient hostClient, HostReply hostReply, DatagramSocket sharedDatagramSocket, String clientIP, int clientOutPort) {
        this.hostClient = hostClient;
        this.hostReply = hostReply;
        this.sharedDatagramSocket = sharedDatagramSocket;
        this.clientIP = clientIP;
        this.clientOutPort = clientOutPort;
        try {
            // 在构造时解析一次IP，后续直接复用
            this.cachedClientAddress = InetAddress.getByName(clientIP);
        } catch (UnknownHostException e) {
            debugOperation(e);
        }
    }

    /**
     * 保持公开方法签名不变，兼容外部调用。
     * 内部实现未变，保证协议格式一致。
     */
    public static byte[] serializeDatagramPacket(DatagramPacket packet) {
        byte[] data = packet.getData();
        int offset = packet.getOffset();
        int length = packet.getLength();
        InetAddress address = packet.getAddress();
        int port = packet.getPort();

        byte[] ipBytes = address.getAddress();
        int ipLength = ipBytes.length;

        int totalLen = 14 + ipLength + length; // 4+4+4+ipLen+2+data
        byte[] buffer = new byte[totalLen];
        ByteBuffer bb = ByteBuffer.wrap(buffer);
        bb.order(ByteOrder.BIG_ENDIAN);

        bb.putInt(0xDEADBEEF);
        bb.putInt(length);
        bb.putInt(ipLength);
        bb.put(ipBytes);
        bb.putShort((short) port);
        bb.put(data, offset, length);

        return buffer;
    }

    /**
     * 保持公开方法签名不变。
     * 优化后的内部循环中不再调用此方法，而是使用内联解析以提升性能。
     * 但保留此方法以防其他类有偶发调用。
     */
    public static DatagramPacket deserializeToDatagramPacket(byte[] serializedData) {
        if (serializedData == null || serializedData.length < 14) return null;

        ByteBuffer buffer = ByteBuffer.wrap(serializedData);
        buffer.order(ByteOrder.BIG_ENDIAN);

        if (buffer.getInt() != 0xDEADBEEF) return null;

        int dataLen = buffer.getInt();
        int ipLen = buffer.getInt();

        if (dataLen < 0 || dataLen > 65507 || (ipLen != 4 && ipLen != 16)) return null;
        if (serializedData.length < 14 + ipLen + dataLen) return null;

        byte[] ipBytes = new byte[ipLen];
        buffer.get(ipBytes);
        int port = buffer.getShort() & 0xFFFF;
        byte[] data = new byte[dataLen];
        buffer.get(data);

        try {
            return new DatagramPacket(data, dataLen, InetAddress.getByAddress(ipBytes), port);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public static void tellRestBalance(HostClient hostClient, double[] aTenMibSize, int len, LanguageData languageData) {
        if (aTenMibSize[0] < TELL_BALANCE_MIB) {
            aTenMibSize[0] = aTenMibSize[0] + SizeCalculator.byteToMib(len);
        } else {
            try {
                // 异步通知，减少对数据线程的阻塞
                ThreadManager.runAsync(() -> {
                    try {
                        InternetOperator.sendStr(hostClient, languageData.THIS_ACCESS_CODE_HAVE + hostClient.getKey().getBalance() + languageData.MB_OF_FLOW_LEFT);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception e) {
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

    // 优化：用户 -> 代理 -> 内网 (User -> HostClient)
    // 引入批处理 (drainTo)
    private void outClientToHostClient(double[] aTenMibSize) {
        List<byte[]> batchBuffer = new ArrayList<>(64);
        try {
            RateLimiter limiter = hostClient.getGlobalRateLimiter();

            while (isRunning) {
                // 1. 阻塞等待第一个包，避免空转
                byte[] first = sendQueue.poll(1, TimeUnit.SECONDS);
                if (first == null) continue;

                batchBuffer.add(first);
                // 2. 将队列中剩余的包一次性取出（最多63个），大幅减少锁竞争
                sendQueue.drainTo(batchBuffer, 63);

                int batchTotalBytes = 0;

                // 3. 循环发送
                for (byte[] data : batchBuffer) {
                    try {
                        int enLength = hostReply.host().sendByte(data);
                        if (enLength > 0) {
                            batchTotalBytes += (enLength + 10);
                            tellRestBalance(hostClient, aTenMibSize, enLength, hostClient.getLangData());
                        }
                    } catch (IOException e) {
                        throw e; // 抛出异常以终止线程
                    }
                }

                // 4. 批量提交计费和限速（实时获取最新限速值）
                if (batchTotalBytes > 0) {
                    hostClient.getKey().mineMib("UDP-Transformer", SizeCalculator.byteToMib(batchTotalBytes));
                    limiter.setMaxMbps(hostClient.getKey().getRate()); // 确保实时更新
                    limiter.onBytesTransferred(batchTotalBytes);
                }

                batchBuffer.clear();
            }
        } catch (Exception e) {
            debugOperation(e);
        } finally {
            stop();
        }
    }

    // 优化：内网 -> 代理 -> 用户 (HostClient -> User)
    // 引入内联解析 (Zero-Copy 思想) 和 对象复用
    private void hostClientToOutClient(double[] aTenMibSize) {
        try {
            RateLimiter limiter = hostClient.getGlobalRateLimiter();
            byte[] data;

            // 复用 Packet 对象，避免在 while 循环中疯狂创建对象
            byte[] dummy = new byte[0];
            DatagramPacket reusePacket = new DatagramPacket(dummy, 0, cachedClientAddress, clientOutPort);

            while (isRunning && (data = hostReply.host().receiveByte()) != null) {
                // 基础长度校验
                if (data.length < 14) continue;

                // --- 内联解析开始 (替代 deserializeToDatagramPacket 以减少对象分配) ---

                // Magic Check (Big Endian)
                int magic = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) |
                        ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                if (magic != 0xDEADBEEF) continue;

                // Read Lengths
                int dataLen = ((data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16) |
                        ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
                int ipLen = ((data[8] & 0xFF) << 24) | ((data[9] & 0xFF) << 16) |
                        ((data[10] & 0xFF) << 8) | (data[11] & 0xFF);

                // 校验
                if (dataLen <= 0 || dataLen > 65507 || (ipLen != 4 && ipLen != 16)) continue;
                if (data.length < 14 + ipLen + dataLen) continue;

                // 计算数据段的起始位置：14 (header) + ipLen + 2 (port)
                // 这里跳过了IP和Port的解析，因为我们知道要发给谁 (cachedClientAddress:clientOutPort)
                int dataStartIndex = 14 + ipLen + 2;

                // --- 内联解析结束 ---

                // 计费与限速（实时获取最新限速值）
                hostClient.getKey().mineMib("UDP-Transformer", SizeCalculator.byteToMib(dataLen + 10));
                tellRestBalance(hostClient, aTenMibSize, dataLen, hostClient.getLangData());
                limiter.setMaxMbps(hostClient.getKey().getRate()); // 确保实时更新
                limiter.onBytesTransferred(dataLen);

                // 直接设置数据，发送
                reusePacket.setData(data, dataStartIndex, dataLen);
                sharedDatagramSocket.send(reusePacket);
            }
        } catch (Exception e) {
            debugOperation(e);
        } finally {
            stop();
        }
    }

    @Override
    public void run() {
        try {
            // 使用数组作为简单的引用容器
            final double[] aTenMibSize = {0};

            Runnable clientToHostClientThread = () -> outClientToHostClient(aTenMibSize);
            Runnable hostClientToClientThread = () -> hostClientToOutClient(aTenMibSize);

            ThreadManager threadManager = new ThreadManager(clientToHostClientThread, hostClientToClientThread);
            List<Throwable> exceptions = threadManager.start();

            for (Throwable t : exceptions) {
                if (t instanceof NoMoreNetworkFlowException) {
                    kickAllWithMsg(hostClient, hostReply.host());
                    return;
                }
            }
        } catch (Exception ignore) {
        } finally {
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