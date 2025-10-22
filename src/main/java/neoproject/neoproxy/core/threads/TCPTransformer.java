package neoproject.neoproxy.core.threads;

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

import static neoproject.neoproxy.NeoProxyServer.debugOperation;
import static neoproject.neoproxy.core.InternetOperator.*;
import static neoproject.neoproxy.core.management.SequenceKey.disableKey;

/**
 * @param hostClient 实例字段，用于存储构造时传入的HostClient
 */
public record TCPTransformer(HostClient hostClient, Socket client, HostReply hostReply) implements Runnable {
    public static int TELL_BALANCE_MIB = 10;
    public static int BUFFER_LEN = 10;

    // 私有构造函数，防止外部直接实例化

    /**
     * 【修改】启动方法，现在需要传入HostClient实例
     */
    public static void startThread(HostClient hostClient, HostReply hostReply, Socket client) {
        // 【关键】在启动线程前，先注册连接
        hostClient.registerTcpSocket(client);
        // 创建实例并启动线程
        new Thread(new TCPTransformer(hostClient, client, hostReply), "new Transformer").start();
    }

    // 以下所有静态方法都需要修改，以接收HostClient实例
    public static void ClientToHost(HostClient hostClient, Socket client, HostReply hostReply, double[] aTenMibSize) {
        try {
            BufferedInputStream bufferedInputStream = new BufferedInputStream(client.getInputStream());
            RateLimiter limiter = new RateLimiter(hostClient.getKey().getRate());

            int len;
            byte[] data = new byte[BUFFER_LEN];
            while ((len = bufferedInputStream.read(data)) != -1) {
                int enLength = hostReply.host().sendByte(data, 0, len);

                hostClient.getKey().mineMib("TCP-Transformer", SizeCalculator.byteToMib(enLength + 10));
                tellRestBalance(hostClient, aTenMibSize, enLength, hostClient.getLangData());

                RateLimiter.setMaxMbps(limiter, hostClient.getKey().getRate());
                limiter.onBytesTransferred(enLength);
            }

            hostReply.host().sendByte(null);
            shutdownOutput(hostReply.host());
            shutdownInput(client);

        } catch (IOException e) {
            debugOperation(e);
            shutdownOutput(hostReply.host());
            shutdownInput(client);
        } catch (NoMoreNetworkFlowException e) {
            disableKey(hostClient.getKey().getName());
            kickAllWithMsg(hostClient, hostReply.host(), client);
        }
    }

    public static void HostToClient(HostClient hostClient, HostReply hostReply, Socket client, double[] aTenMibSize) {
        try {
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(client.getOutputStream());
            RateLimiter limiter = new RateLimiter(hostClient.getKey().getRate());

            byte[] data;
            while ((data = hostReply.host().receiveByte()) != null) {
                bufferedOutputStream.write(data);
                bufferedOutputStream.flush();

                hostClient.getKey().mineMib("TCP-Transformer", SizeCalculator.byteToMib(data.length));
                tellRestBalance(hostClient, aTenMibSize, data.length, hostClient.getLangData());

                RateLimiter.setMaxMbps(limiter, hostClient.getKey().getRate());
                limiter.onBytesTransferred(data.length);
            }

            shutdownInput(hostReply.host());
            shutdownOutput(client);
        } catch (IOException e) {
            debugOperation(e);
            shutdownInput(hostReply.host());
            shutdownOutput(client);
        } catch (NoMoreNetworkFlowException e) {
            disableKey(hostClient.getKey().getName());
            kickAllWithMsg(hostClient, hostReply.host(), client);
        }
    }

    public static void tellRestBalance(HostClient hostClient, double[] aTenMibSize, int len, LanguageData languageData) throws IOException {
        if (aTenMibSize[0] < TELL_BALANCE_MIB) {
            aTenMibSize[0] = aTenMibSize[0] + SizeCalculator.byteToMib(len);
        } else {
            sendStr(hostClient, languageData.THIS_ACCESS_CODE_HAVE + hostClient.getKey().getBalance() + languageData.MB_OF_FLOW_LEFT);
            aTenMibSize[0] = 0;
        }
    }

    public static void kickAllWithMsg(HostClient hostClient, SecureSocket host, Closeable client) {
        close(client, host);
        try {
            sendCommand(hostClient, "exitNoFlow");
            InfoBox.sayHostClientDiscInfo(hostClient, "TCPTransformer");
        } catch (Exception e) {
            InfoBox.sayHostClientDiscInfo(hostClient, "TCPTransformer");
        }
        close(hostClient);
    }

    @Override
    public void run() {
        try {
            final double[] aTenMibSize = {0};
            Runnable clientToHostClientThread = () -> ClientToHost(hostClient, client, hostReply, aTenMibSize);
            Runnable hostClientToClientThread = () -> HostToClient(hostClient, hostReply, client, aTenMibSize);
            ThreadManager threadManager = new ThreadManager(clientToHostClientThread, hostClientToClientThread);
            threadManager.start();
        } catch (Exception ignore) {
            // ThreadManager 会捕获异常，这里通常不会执行
        } finally {
            // 【修改】在线程结束后，注销连接并关闭资源
            hostClient.unregisterTcpSocket(client);
            close(client, hostReply.host());
            InfoBox.sayClientTCPConnectDestroyInfo(hostClient, client);
        }
    }
}