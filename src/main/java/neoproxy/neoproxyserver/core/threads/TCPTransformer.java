package neoproxy.neoproxyserver.core.threads;

import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.HostReply;
import neoproxy.neoproxyserver.core.LanguageData;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.exceptions.IllegalWebSiteException;
import neoproxy.neoproxyserver.core.exceptions.NoMoreNetworkFlowException;
import plethora.management.bufferedFile.SizeCalculator;
import plethora.net.SecureSocket;
import plethora.thread.ThreadManager;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static neoproxy.neoproxyserver.NeoProxyServer.debugOperation;
import static neoproxy.neoproxyserver.NeoProxyServer.myConsole;
import static neoproxy.neoproxyserver.core.InternetOperator.*;
import static neoproxy.neoproxyserver.core.ServerLogger.alert;
import static neoproxy.neoproxyserver.core.management.SequenceKey.disableKey;

/**
 * 【优化版】TCP数据传输器，负责在客户端和目标主机之间双向转发数据。
 * 通过将静态方法重构为实例方法，并复用实例缓冲区，显著减少了GC压力。
 */
public class TCPTransformer implements Runnable {

    public static int TELL_BALANCE_MIB = 10;
    public static int BUFFER_LEN = 8192;
    public static String CUSTOM_BLOCKING_MESSAGE = "如有疑问，请联系您的系统管理员。";

    private static String FORBIDDEN_HTML_TEMPLATE;

    static {
        try (InputStream inputStream = TCPTransformer.class.getResourceAsStream("/templates/forbidden.html")) {
            if (inputStream == null) {
                throw new RuntimeException("Fail to find forbidden.html in ./templates/.");
            }
            FORBIDDEN_HTML_TEMPLATE = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            debugOperation(e);
            FORBIDDEN_HTML_TEMPLATE = null;
        }
    }

    // --- 实例字段 ---
    private final HostClient hostClient;
    private final Socket client;
    private final HostReply hostReply;

    // 🔥【性能优化】为每个实例创建一个独立的、可复用的缓冲区
    private final byte[] clientToHostBuffer = new byte[BUFFER_LEN];

    public TCPTransformer(HostClient hostClient, Socket client, HostReply hostReply) {
        this.hostClient = hostClient;
        this.client = client;
        this.hostReply = hostReply;
    }

    public static void startThread(HostClient hostClient, HostReply hostReply, Socket client) {
        hostClient.registerTcpSocket(client);
        // 使用平台线程是合理的，因为 run() 方法会阻塞直到连接结束
        new Thread(new TCPTransformer(hostClient, client, hostReply), "TCP-Transformer-" + client.getRemoteSocketAddress()).start();
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
            ServerLogger.sayHostClientDiscInfo(hostClient, "TCPTransformer");
        } catch (Exception e) {
            ServerLogger.sayHostClientDiscInfo(hostClient, "TCPTransformer");
        }
        close(hostClient);
    }

    // --- 静态工具方法 ---

    private static void checkAndBlockHtmlResponse(byte[] data, BufferedOutputStream clientOutput, String remoteSocketAddress, HostClient hostClient) throws IllegalWebSiteException, IOException {
        // ... (此方法保持不变，因为它不依赖实例状态)
        if (data == null || data.length == 0) {
            return;
        }

        String response = new String(data, StandardCharsets.UTF_8);
        int headerEndIndex = response.indexOf("\r\n\r\n");
        String headerPart = (headerEndIndex != -1) ? response.substring(0, headerEndIndex) : response;

        if (headerPart.toLowerCase().contains("content-type: text/html")) {
            if (alert) {
                myConsole.log("TCPTransformer", "Detected web HTML from " + remoteSocketAddress.replaceAll("/", ""));
            }

            if (FORBIDDEN_HTML_TEMPLATE == null) {
                return;
            }
            String finalHtml = FORBIDDEN_HTML_TEMPLATE.replace("{{CUSTOM_MESSAGE}}", CUSTOM_BLOCKING_MESSAGE != null ? CUSTOM_BLOCKING_MESSAGE : "");
            byte[] errorHtmlBytes = finalHtml.getBytes(StandardCharsets.UTF_8);

            String httpResponseHeader = "HTTP/1.1 403 Forbidden\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: " + errorHtmlBytes.length + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";

            clientOutput.write(httpResponseHeader.getBytes(StandardCharsets.UTF_8));
            clientOutput.write(errorHtmlBytes);
            clientOutput.flush();

            IllegalWebSiteException.throwException(hostClient.getKey().getName());
        }
    }

    /**
     * 🔥【重构】改为私有实例方法，使用实例的缓冲区。
     * 负责从客户端读取数据并发送到目标主机。
     */
    private void clientToHost(double[] aTenMibSize) {
        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(client.getInputStream())) {
            RateLimiter limiter = new RateLimiter(hostClient.getKey().getRate());

            int len;
            // 🔥 使用实例的 clientToHostBuffer，避免在循环中重复创建
            while ((len = bufferedInputStream.read(clientToHostBuffer)) != -1) {
                int enLength = hostReply.host().sendByte(clientToHostBuffer, 0, len);
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

    /**
     * 🔥【重构】改为私有实例方法。
     * 负责从目标主机接收数据并发送到客户端。
     */
    private void hostToClient(double[] aTenMibSize) {
        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(client.getOutputStream())) {
            RateLimiter limiter = new RateLimiter(hostClient.getKey().getRate());

            byte[] data; // 注意：这里的 data 是由 receiveByte() 返回的，无法复用
            boolean isHtmlResponseChecked = false;

            while ((data = hostReply.host().receiveByte()) != null) {
                if (!isHtmlResponseChecked && !hostClient.getKey().isHTMLEnabled()) {
                    isHtmlResponseChecked = true;
                    checkAndBlockHtmlResponse(data, bufferedOutputStream, hostReply.host().getRemoteSocketAddress().toString(), hostClient);
                }

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
        } catch (IllegalWebSiteException e) {
            // 此异常已被处理，只需确保线程结束
        }
    }

    @Override
    public void run() {
        final double[] aTenMibSize = {0};
        try {
            // 🔥 使用 ThreadManager 的阻塞等待，因为这是一个连接的生命周期
            Runnable clientToHostTask = () -> clientToHost(aTenMibSize);
            Runnable hostToClientTask = () -> hostToClient(aTenMibSize);
            ThreadManager threadManager = new ThreadManager(clientToHostTask, hostToClientTask);
            threadManager.start(); // 阻塞直到两个方向的数据流都结束
        } finally {
            hostClient.unregisterTcpSocket(client);
            close(client, hostReply.host());
            ServerLogger.sayClientTCPConnectDestroyInfo(hostClient, client);
        }
    }
}