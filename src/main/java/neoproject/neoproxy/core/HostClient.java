package neoproject.neoproxy.core;

import neoproject.neoproxy.core.management.SequenceKey;
import plethora.net.SecureSocket;
import plethora.utils.Sleeper;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

import static neoproject.neoproxy.NeoProxyServer.availableHostClient;
import static neoproject.neoproxy.NeoProxyServer.sayInfo;
import static neoproject.neoproxy.core.management.SequenceKey.saveToDB;

public final class HostClient implements Closeable {
    public static int SAVE_DELAY = 3000;//3s
    public static int DETECTION_DELAY = 1000;
    public static int AES_KEY_SIZE = 128;
    private final SecureSocket hostServerHook;
    // 用于跟踪所有由该 HostClien t创建的活跃TCP连接
    private final CopyOnWriteArrayList<Socket> activeTcpSockets = new CopyOnWriteArrayList<>();
    private boolean isStopped = false;
    private SequenceKey sequenceKey = null;
    private ServerSocket clientServerSocket = null;
    private DatagramSocket clientDatagramSocket = null;
    private LanguageData languageData = new LanguageData();
    private int outPort = -1;

    // ... (HostClient 的构造函数和其他现有方法保持不变)

    public HostClient(SecureSocket hostServerHook) throws IOException {
        this.hostServerHook = hostServerHook;

        HostClient.enableAutoSaveThread(this);
        HostClient.enableKeyDetectionTread(this);
    }

    private static void enableAutoSaveThread(HostClient hostClient) {
        Thread a = new Thread(() -> {
            while (true) {
                if (hostClient.getKey() != null && !hostClient.isStopped) {
                    saveToDB(hostClient.getKey());
                }
                Sleeper.sleep(SAVE_DELAY);
            }
        });
        a.start();
    }

    private static void enableKeyDetectionTread(HostClient hostClient) {
        Thread a = new Thread(() -> {
            while (true) {
                if (hostClient.getKey() != null && !hostClient.isStopped && hostClient.getKey().isOutOfDate()) {
                    sayInfo("The key " + hostClient.getKey().getName() + " is out of date !");
                    try {
                        InternetOperator.sendStr(hostClient, hostClient.getLangData().THE_KEY + hostClient.getKey().getName() + hostClient.getLangData().ARE_OUT_OF_DATE);
                        InternetOperator.sendCommand(hostClient, "exit");
                        InfoBox.sayHostClientDiscInfo(hostClient, "KeyDetectionTread");
                    } catch (Exception e2) {
                        InfoBox.sayHostClientDiscInfo(hostClient, "KeyDetectionTread");
                    }

//                    removeKey(hostClient.getVault());
//                    hostClient.getVault().getFile().delete();
                    //改策略了，不删 Key 了
                    hostClient.close();
                    break;
                }

                //不能通过判断 keyfile 是否存在来断定是否有效，因为 keyfile 刷新的时候会删除文件再创建

                if (hostClient.getKey() != null && !hostClient.getKey().isEnable()) {
                    hostClient.close();
                    break;
                }

                if (hostClient.isStopped) {
                    break;
                }

                Sleeper.sleep(DETECTION_DELAY);
            }
        });
        a.start();
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
     * 【修改】重写close方法，在关闭自身时，也关闭所有由它管理的TCP连接。
     */
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

        if (clientServerSocket != null) {
            try {
                clientServerSocket.close();
            } catch (IOException ignored) {
            }
        }

        this.isStopped = true;
    }

    public void enableCheckAliveThread() {
        HostClient hostClient = this;

        new Thread(() -> {
            while (true) {
                try {
                    byte[] bytes = hostClient.hostServerHook.receiveRaw();
                    if (bytes.length == 0) {
                        hostClient.close();
                        sayInfo("CheckAliveThread", "Detected hostClient on " + hostClient.getAddressAndPort() + " has been disconnected !");
                        break;
                    }
                } catch (Exception e) {
                    hostClient.close();
                    sayInfo("CheckAliveThread", "Detected hostClient on " + hostClient.getAddressAndPort() + " has been disconnected !");
                    break;
                }
            }
        }).start();

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

}
