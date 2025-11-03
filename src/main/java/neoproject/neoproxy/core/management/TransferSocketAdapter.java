package neoproject.neoproxy.core.management;

import neoproject.neoproxy.NeoProxyServer;
import neoproject.neoproxy.core.HostClient;
import neoproject.neoproxy.core.HostReply;
import neoproject.neoproxy.core.ServerLogger;
import plethora.net.SecureServerSocket;
import plethora.net.SecureSocket;
import plethora.utils.Sleeper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CopyOnWriteArrayList;

import static neoproject.neoproxy.NeoProxyServer.*;
import static neoproject.neoproxy.core.InternetOperator.close;

public class TransferSocketAdapter implements Runnable {
    // TCP部分保持不变
    public static final CopyOnWriteArrayList<HostReply> tcpHostReply = new CopyOnWriteArrayList<>();
    // UDP部分：使用ConcurrentHashMap进行非阻塞匹配
    private static final CopyOnWriteArrayList<HostReply> udpHostReply = new CopyOnWriteArrayList<>();

    public static int SO_TIMEOUT = 1000;

    public static void startThread() {
        new Thread(new TransferSocketAdapter()).start();
    }

    public static HostReply getHostReply(int port, int CONN_TYPE) throws SocketTimeoutException {
        for (int i = 0; i < SO_TIMEOUT / 10; i++) {//维持 SO_TIMEOUT 的时间
            if (CONN_TYPE == TransferSocketAdapter.CONN_TYPE.TCP) {
                for (HostReply hostReply : tcpHostReply) {
                    if (hostReply.outPort() == port) {
                        tcpHostReply.remove(hostReply);
                        return hostReply;
                    }
                }
            } else if (CONN_TYPE == TransferSocketAdapter.CONN_TYPE.UDP) {
                for (HostReply hostReply : udpHostReply) {
                    if (hostReply.outPort() == port) {
                        udpHostReply.remove(hostReply);
                        return hostReply;
                    }
                }
            } else {//实际上不可能返回 null ，反正我会严格按照规范写代码
                return null;
            }
            Sleeper.sleep(10);
        }
        throw new SocketTimeoutException();
    }

    @Override
    public void run() {
        try {
            // 只绑定一个端口！
            NeoProxyServer.hostServerTransferServerSocket = new SecureServerSocket(NeoProxyServer.HOST_CONNECT_PORT);
        } catch (IOException e) {
            debugOperation(e);
            // 2. 【修改点】将原来的 sayError 替换为 ServerLogger.errorWithSource
            // 原代码: NeoProxyServer.sayError("TransferSocketAdapter", "Can not bind the outPort , it's Occupied ?");
            ServerLogger.errorWithSource("TransferSocketAdapter", "transferSocketAdapter.bindPortFailed");
            System.exit(-1);
        }

        while (!isStopped) {
            SecureSocket host;
            try {
                host = NeoProxyServer.hostServerTransferServerSocket.accept();
            } catch (IOException e) {
                if (isStopped) break;
                debugOperation(e);
                continue;
            }

            new Thread(() -> {
                try {
                    // 客户端A会先发送一个标识符，告诉我们这是TCP还是UDP连接
                    String connectionType = host.receiveStr();
                    int pretendedPort = host.receiveInt();

                    // 根据标识符进行不同的处理
                    boolean isHas = false;
                    for (HostClient hostClient : NeoProxyServer.availableHostClient) {//检验是否有host client 请求的
                        if (hostClient.getOutPort() == pretendedPort) {
                            if (connectionType.equals("TCP")) {
                                tcpHostReply.add(new HostReply(pretendedPort, host));
                            } else if (connectionType.equals("UDP")) {
                                udpHostReply.add(new HostReply(pretendedPort, host));
                            } else {//标识符无效
                                close(host);
                                break;
                            }
                            isHas = true;
                            break;
                        }
                    }
                    if (!isHas) {//没有
                        close(host);
                    }
                } catch (Exception e) {
                    debugOperation(e);
                    close(host);
                }
            }).start();
        }
    }

    public static class CONN_TYPE {
        public static final int TCP = 0;
        public static final int UDP = 1;
    }
}