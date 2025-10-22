package neoproject.neoproxy.core.management;

import neoproject.neoproxy.NeoProxyServer;
import neoproject.neoproxy.core.HostClient;
import neoproject.neoproxy.core.HostReply;
import neoproject.neoproxy.core.InternetOperator;
import plethora.net.SecureServerSocket;
import plethora.net.SecureSocket;
import plethora.utils.Sleeper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static neoproject.neoproxy.NeoProxyServer.debugOperation;
import static neoproject.neoproxy.NeoProxyServer.isStopped;

public class TransferSocketAdapter implements Runnable {
    // TCP部分保持不变
    public static final CopyOnWriteArrayList<HostReply> tcpHostReply = new CopyOnWriteArrayList<>();
    // UDP部分：使用ConcurrentHashMap进行非阻塞匹配
    private static final Map<Integer, HostReply> udpHostReply = new ConcurrentHashMap<>();

    public static int SO_TIMEOUT = 1000;

    public static void startThread() {
        new Thread(new TransferSocketAdapter()).start();
    }

    // TCP的旧方法保持不变，仅为TCP服务
    public static HostReply getThisHostClientHostReply(int port) throws SocketTimeoutException {
        for (int i = 0; i < SO_TIMEOUT / 10; i++) {//维持 SO_TIMEOUT 的时间
            for (HostReply hostReply : tcpHostReply) {
                if (hostReply.outPort() == port) {
                    tcpHostReply.remove(hostReply);
                    return hostReply;
                }
            }
            Sleeper.sleep(10);
        }
        throw new SocketTimeoutException();
    }

    // UDP的非阻塞方法
    public static HostReply getUdpHostReply(int outPort) {
        return udpHostReply.remove(outPort); // 获取后立即移除，确保一对一
    }

    @Override
    public void run() {
        try {
            // 只绑定一个端口！
            NeoProxyServer.hostServerTransferServerSocket = new SecureServerSocket(NeoProxyServer.HOST_CONNECT_PORT);
        } catch (IOException e) {
            debugOperation(e);
            NeoProxyServer.sayError("TransferSocketAdapter", "Can not bind the outPort , it's Occupied ?");
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
                    if ("TCP".equals(connectionType)) {
                        // TCP逻辑：与原来完全相同
                        boolean isHas = false;
                        for (HostClient hostClient : NeoProxyServer.availableHostClient) {//检验是否有host client 请求的
                            if (hostClient.getOutPort() == pretendedPort) {
                                tcpHostReply.add(new HostReply(pretendedPort, host));
                                isHas = true;
                                break;
                            }
                        }
                        if (!isHas) {//没有
                            InternetOperator.close(host);
                        }
                    } else if ("UDP".equals(connectionType)) {
                        // UDP逻辑：直接放入map，不阻塞
                        udpHostReply.put(pretendedPort, new HostReply(pretendedPort, host));
                    } else {
                        // 未知类型，关闭连接
                        InternetOperator.close(host);
                    }
                } catch (Exception e) {
                    debugOperation(e);
                    InternetOperator.close(host);
                }
            }).start();
        }
    }
}