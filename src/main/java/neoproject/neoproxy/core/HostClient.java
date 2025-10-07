package neoproject.neoproxy.core;

import neoproject.neoproxy.core.exceptions.IllegalConnectionException;
import plethora.net.SecureSocket;
import plethora.utils.Sleeper;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;

import static neoproject.neoproxy.NeoProxyServer.availableHostClient;
import static neoproject.neoproxy.NeoProxyServer.sayInfo;
import static neoproject.neoproxy.core.SequenceKey.saveToDB;

public final class HostClient implements Closeable {
    public static int SAVE_DELAY = 3000;//3s
    public static int DETECTION_DELAY = 1000;
    private boolean isStopped = false;
    private SequenceKey sequenceKey = null;
    private final SecureSocket hostServerHook;
    private ServerSocket clientServerSocket = null;
    private LanguageData languageData = new LanguageData();
    private int outPort = -1;
    public static int AES_KEY_SIZE = 128;

    public HostClient(SecureSocket hostServerHook) throws IOException, IllegalConnectionException {
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
                        InfoBox.sayHostClientDiscInfo(hostClient, "VaultDetectionTread");
                    } catch (Exception e2) {
                        InfoBox.sayHostClientDiscInfo(hostClient, "VaultDetectionTread");
                    }

//                    removeKey(hostClient.getVault());
//                    hostClient.getVault().getFile().delete();
                    //改策略了，不删 Key 了
                    hostClient.close();
                    break;
                }

                //不能通过判断 keyfile 是否存在来断定是否有效，因为 keyfile 刷新的时候会删除文件再创建

                if (hostClient.isStopped) {
                    break;
                }

                Sleeper.sleep(DETECTION_DELAY);
            }
        });
        a.start();
    }

    public void close() {
        availableHostClient.remove(this);

        InternetOperator.close(hostServerHook);

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
