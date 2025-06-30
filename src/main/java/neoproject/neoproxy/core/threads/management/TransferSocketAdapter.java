package neoproject.neoproxy.core.threads.management;

import neoproject.neoproxy.NeoProxyServer;
import neoproject.neoproxy.core.HostClient;
import neoproject.neoproxy.core.HostSign;
import neoproject.neoproxy.core.InternetOperator;
import plethora.print.log.LogType;
import plethora.print.log.State;
import plethora.utils.Sleeper;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CopyOnWriteArrayList;

public class TransferSocketAdapter implements Runnable {
    public static final CopyOnWriteArrayList<HostSign> hostList = new CopyOnWriteArrayList<>();
    public static int SO_TIMEOUT = 500;

    public static void startThread() {
        new Thread(new TransferSocketAdapter()).start();
    }

    @Override
    public void run() {
        try {
            NeoProxyServer.hostServerTransferServerSocket = new ServerSocket(NeoProxyServer.HOST_CONNECT_PORT);
            while (true) {
                Socket host = NeoProxyServer.hostServerTransferServerSocket.accept();
                new Thread(() -> {
                    ObjectInputStream objectInputStream;
                    try {
                        out:
                        for (int i = 1; i <= 2; i++) {

                            //初始化交流通道
                            objectInputStream = new ObjectInputStream(host.getInputStream());
                            ObjectOutputStream objectOutputStream = new ObjectOutputStream(host.getOutputStream());

                            //host client告诉该连接socket的对应外网端口，服务端在连接上的 host client 列表中验证是否存在这个分配了的外网端口的 host client
                            int pretendedPort = objectInputStream.readInt();
                            for (HostClient hostClient : NeoProxyServer.availableHostClient) {
                                if (hostClient.getOutPort() == pretendedPort) {
                                    hostList.add(new HostSign(pretendedPort, host, objectInputStream, objectOutputStream));
                                    break out;//如果有，代码到这里结束，跳出循环
                                }
                            }
                            InternetOperator.close(host);//如果没有，关闭连接
                        }
                    } catch (IOException e) {
                        InternetOperator.close(host);//如果之间有任何io异常，则直接关闭连接。因为正常来说不应有任何异常。
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
            NeoProxyServer.sayInfo(LogType.ERROR, "TransferSocketAdapter", "Can not blind the port , it's Occupied ?");
            System.exit(-1);
        }
    }

    public static HostSign getThisHostClientHostSign(int port) throws SocketTimeoutException {
        for (int i = 0; i < SO_TIMEOUT / 10; i++) {
            for (HostSign hostSign : hostList) {
                if (hostSign.port() == port) {
                    hostList.remove(hostSign);
                    return hostSign;
                }
            }
            Sleeper.sleep(10);
        }
        throw new SocketTimeoutException();
    }
}
