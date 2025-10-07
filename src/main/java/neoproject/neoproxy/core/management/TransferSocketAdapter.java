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
import java.util.concurrent.CopyOnWriteArrayList;

import static neoproject.neoproxy.NeoProxyServer.debugOperation;

public class TransferSocketAdapter implements Runnable {
    public static final CopyOnWriteArrayList<HostReply> hostList = new CopyOnWriteArrayList<>();
    public static int SO_TIMEOUT = 500;

    public static void startThread() {
        new Thread(new TransferSocketAdapter()).start();
    }

    @Override
    public void run() {
        try {
            NeoProxyServer.hostServerTransferServerSocket = new SecureServerSocket(NeoProxyServer.HOST_CONNECT_PORT);
        } catch (IOException e) {
            debugOperation(e);
            NeoProxyServer.sayError("TransferSocketAdapter", "Can not blind the port , it's Occupied ?");
            System.exit(-1);
        }

        while (true) {
            SecureSocket host;
            try {
                host = NeoProxyServer.hostServerTransferServerSocket.accept();
            } catch (IOException e) {//如果之间有任何io异常，则直接跳过。因为正常来说不应有任何异常。
                debugOperation(e);
                continue;
            }
            new Thread(() -> {
                try {
                    //host client告诉该连接socket的对应外网端口，服务端在连接上的 host client 列表中验证是否存在这个分配了的外网端口的 host client
                    int pretendedPort = host.receiveInt();

                    boolean isHas = false;
                    for (HostClient hostClient : NeoProxyServer.availableHostClient) {
                        if (hostClient.getOutPort() == pretendedPort) {
                            hostList.add(new HostReply(pretendedPort, host));
                            isHas = true;
                            break;//如果有，代码到这里结束，跳出循环
                        }
                    }
                    if (!isHas) {
                        InternetOperator.close(host);//如果没有，关闭连接
                    }
                } catch (Exception e) {
                    debugOperation(e);
                    InternetOperator.close(host);//如果之间有任何io异常，则直接关闭连接。因为正常来说不应有任何异常。
                }
            }).start();
        }

    }

    public static HostReply getThisHostClientHostSign(int port) throws SocketTimeoutException {
        for (int i = 0; i < SO_TIMEOUT / 10; i++) {
            for (HostReply hostReply : hostList) {
                if (hostReply.port() == port) {
                    hostList.remove(hostReply);
                    return hostReply;
                }
            }
            Sleeper.sleep(10);
        }
        throw new SocketTimeoutException();
    }
}
