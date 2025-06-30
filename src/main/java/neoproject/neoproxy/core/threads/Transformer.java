package neoproject.neoproxy.core.threads;

import neoproject.neoproxy.NeoProxyServer;
import neoproject.neoproxy.core.*;
import neoproject.neoproxy.core.exceptions.NoMoreNetworkFlowException;
import neoproject.publicInstance.DataPacket;
import plethora.management.bufferedFile.SizeCalculator;
import plethora.thread.ThreadManager;

import java.io.*;
import java.net.Socket;

import static neoproject.neoproxy.core.InternetOperator.close;
import static neoproject.neoproxy.core.SequenceKey.removeVaultOnAll;

public class Transformer implements Runnable {
    public static int BUFFER_LEN = 256;
    public static int TELL_RATE_MIB = 10;
    private final HostClient hostClient;
    private final Socket client;
    private HostSign hostSign;


    private Transformer(HostClient hostClient, Socket client, HostSign hostSign) {
        this.hostClient = hostClient;
        this.client = client;
        this.hostSign = hostSign;
    }

    public static void startThread(HostClient hostClient, HostSign hostSign, Socket client) {
        new Thread(new Transformer(hostClient, client, hostSign)).start();
    }

    public static void transferDataToNeoServer(HostClient hostClient, Socket client, HostSign hostSign, double[] aTenMibSize) {
        try {
            BufferedInputStream bufferedInputStream = new BufferedInputStream(client.getInputStream());
            ObjectOutputStream objectOutputStream = hostSign.objectOutputStream();

            int len;
            byte[] data = new byte[BUFFER_LEN];
            while ((len = bufferedInputStream.read(data)) != -1) {

                byte[] enData = hostClient.getAESUtil().encrypt(data, 0, len);
                objectOutputStream.writeObject(new DataPacket(len, enData));
                objectOutputStream.flush();

                hostClient.getVault().mineMib(SizeCalculator.byteToMib(enData.length + 100));//real + 0.1kb
                tellRestRate(hostClient, aTenMibSize, enData.length, hostClient.getLangData());//tell the host client the rest rate.
            }

            objectOutputStream.writeObject(null);//tell host client is end!
            hostSign.host().shutdownOutput();
            client.shutdownInput();

        } catch (IOException e) {
            NeoProxyServer.debugOperation(e);

            try {

                hostSign.objectOutputStream().writeObject(null);//tell host client is end!
                hostSign.host().shutdownOutput();
                client.shutdownInput();

            } catch (IOException ignore) {
            }

        } catch (NoMoreNetworkFlowException e) {
            removeVaultOnAll(hostClient.getVault());
            kickAllWithMsg(hostClient, hostSign.host(), client);
        }
        System.gc();
    }

    public static void transferDataToOuterClient(HostClient hostClient, HostSign hostSign, Socket client, double[] aTenMibSize) {
        try {
            ObjectInputStream objectInputStream = hostSign.objectInputStream();
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(client.getOutputStream());

            DataPacket dataPacket;
            while ((dataPacket = (DataPacket) objectInputStream.readObject()) != null) {


                byte[] deData = hostClient.getAESUtil().decrypt(dataPacket.enData);
                bufferedOutputStream.write(deData, 0, dataPacket.realLen);
                bufferedOutputStream.flush();

                hostClient.getVault().mineMib(SizeCalculator.byteToMib(deData.length));
                tellRestRate(hostClient, aTenMibSize, deData.length, hostClient.getLangData());//tell the host client the rest rate.
            }

            hostSign.host().shutdownInput();
            client.shutdownOutput();

        } catch (IOException | ClassNotFoundException e) {
            NeoProxyServer.debugOperation(e);

            try {

                hostSign.host().shutdownInput();
                client.shutdownOutput();

            } catch (IOException ignore) {
            }
        } catch (NoMoreNetworkFlowException e) {
            removeVaultOnAll(hostClient.getVault());
            kickAllWithMsg(hostClient, hostSign.host(), client);
        }
        System.gc();
    }

    public static void tellRestRate(HostClient hostClient, double[] aTenMibSize, int len, LanguageData languageData) throws IOException {
        if (aTenMibSize[0] < TELL_RATE_MIB) {//tell the host client the rest rate.
            aTenMibSize[0] = aTenMibSize[0] + SizeCalculator.byteToMib(len);
        } else {
            InternetOperator.sendStr(hostClient, languageData.THIS_ACCESS_CODE_HAVE + hostClient.getVault().getRate() + languageData.MB_IF_FLOW_LEFT);
            aTenMibSize[0] = 0;
        }
    }

    public static void kickAllWithMsg(HostClient hostClient, Socket host, Closeable clientEle) {
        close(clientEle, host);
        try {
            InternetOperator.sendCommand(hostClient, "exit");
            InfoBox.sayHostClientDiscInfo(hostClient, "Transformer");
        } catch (Exception e) {
            InfoBox.sayHostClientDiscInfo(hostClient, "Transformer");
        }
        close(hostClient);
    }

    @Override
    public void run() {
        try {
            final double[] aTenMibSize = {0};//Just for code! No function.
            Runnable clientToHostClientThread = () -> transferDataToNeoServer(hostClient, client, hostSign, aTenMibSize);
            Runnable hostClientToClientThread = () -> transferDataToOuterClient(hostClient, hostSign, client, aTenMibSize);
            ThreadManager threadManager = new ThreadManager(clientToHostClientThread, hostClientToClientThread);
            threadManager.startAll();
            close(client, hostSign.host());
            InfoBox.sayClientConnectDestroyInfo(hostClient, client);
        } catch (Exception ignore) {
            close(client, hostSign.host());
            InfoBox.sayClientConnectDestroyInfo(hostClient, client);
        }
        System.gc();
    }

}
