package neoproject.neoproxy.core.threads;

import neoproject.neoproxy.NeoProxyServer;
import neoproject.neoproxy.core.*;
import neoproject.neoproxy.core.exceptions.NoMoreNetworkFlowException;
import plethora.management.bufferedFile.SizeCalculator;
import plethora.net.SecureSocket;
import plethora.thread.ThreadManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;

import static neoproject.neoproxy.core.InternetOperator.close;
import static neoproject.neoproxy.core.SequenceKey.removeVaultOnAll;

public record Transformer(HostClient hostClient, Socket client, HostReply hostReply) implements Runnable {
    public static int BUFFER_LEN = 256;
    public static int TELL_RATE_MIB = 10;

    public static void startThread(HostClient hostClient, HostReply hostReply, Socket client) {
        new Thread(new Transformer(hostClient, client, hostReply), "new Transformer").start();
    }

    public static void ClientToHost(HostClient hostClient, Socket client, HostReply hostReply, double[] aTenMibSize) {
        try {
            BufferedInputStream bufferedInputStream = new BufferedInputStream(client.getInputStream());

            int len;
            byte[] data = new byte[BUFFER_LEN];
            while ((len = bufferedInputStream.read(data)) != -1) {
                int enLength = hostReply.host().sendByte(data, 0, len);

                hostClient.getVault().mineMib(SizeCalculator.byteToMib(enLength + 10));//real + 0.01kb
                tellRestRate(hostClient, aTenMibSize, enLength, hostClient.getLangData());//tell the host client the rest rate.
            }

            hostReply.host().sendByte(null);//告知传输完成
            hostReply.host().shutdownOutput();
            client.shutdownInput();

        } catch (IOException e) {
            NeoProxyServer.debugOperation(e);

            try {

                hostReply.host().shutdownOutput();//传输出现问题
                client.shutdownInput();

            } catch (IOException ignore) {
            }
        } catch (NoMoreNetworkFlowException e) {
            removeVaultOnAll(hostClient.getVault());
            kickAllWithMsg(hostClient, hostReply.host(), client);
        }
    }

    public static void HostToClient(HostClient hostClient, HostReply hostReply, Socket client, double[] aTenMibSize) {
        try {
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(client.getOutputStream());

            byte[] data;
            while ((data = hostReply.host().receiveByte()) != null) {
                bufferedOutputStream.write(data);
                bufferedOutputStream.flush();

                hostClient.getVault().mineMib(SizeCalculator.byteToMib(data.length));
                tellRestRate(hostClient, aTenMibSize, data.length, hostClient.getLangData());//tell the host client the rest rate.
            }

            hostReply.host().shutdownInput();
            client.shutdownOutput();

        } catch (IOException e) {
            NeoProxyServer.debugOperation(e);

            try {

                hostReply.host().shutdownInput();
                client.shutdownOutput();

            } catch (IOException ignore) {
            }
        } catch (NoMoreNetworkFlowException e) {
            removeVaultOnAll(hostClient.getVault());
            kickAllWithMsg(hostClient, hostReply.host(), client);
        }
    }

    public static void tellRestRate(HostClient hostClient, double[] aTenMibSize, int len, LanguageData languageData) throws IOException {
        if (aTenMibSize[0] < TELL_RATE_MIB) {//tell the host client the rest rate.
            aTenMibSize[0] = aTenMibSize[0] + SizeCalculator.byteToMib(len);
        } else {
            InternetOperator.sendStr(hostClient, languageData.THIS_ACCESS_CODE_HAVE + hostClient.getVault().getRate() + languageData.MB_OF_FLOW_LEFT);
            aTenMibSize[0] = 0;
        }
    }

    public static void kickAllWithMsg(HostClient hostClient, SecureSocket host, Closeable clientEle) {
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
            Runnable clientToHostClientThread = () -> ClientToHost(hostClient, client, hostReply, aTenMibSize);
            Runnable hostClientToClientThread = () -> HostToClient(hostClient, hostReply, client, aTenMibSize);
            ThreadManager threadManager = new ThreadManager(clientToHostClientThread, hostClientToClientThread);
            threadManager.startAll();
            close(client, hostReply.host());
            InfoBox.sayClientConnectDestroyInfo(hostClient, client);
        } catch (Exception ignore) {
            close(client, hostReply.host());
            InfoBox.sayClientConnectDestroyInfo(hostClient, client);
        }
    }

}
