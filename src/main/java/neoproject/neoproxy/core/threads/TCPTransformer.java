package neoproject.neoproxy.core.threads;

import neoproject.neoproxy.core.HostClient;
import neoproject.neoproxy.core.HostReply;
import neoproject.neoproxy.core.InfoBox;
import neoproject.neoproxy.core.LanguageData;
import neoproject.neoproxy.core.exceptions.IllegalWebSiteException;
import neoproject.neoproxy.core.exceptions.NoMoreNetworkFlowException;
import plethora.management.bufferedFile.SizeCalculator;
import plethora.net.SecureSocket;
import plethora.thread.ThreadManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static neoproject.neoproxy.NeoProxyServer.debugOperation;
import static neoproject.neoproxy.NeoProxyServer.myConsole;
import static neoproject.neoproxy.core.InternetOperator.*;
import static neoproject.neoproxy.core.management.SequenceKey.disableKey;

public record TCPTransformer(HostClient hostClient, Socket client, HostReply hostReply) implements Runnable {
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

    public static void startThread(HostClient hostClient, HostReply hostReply, Socket client) {
        hostClient.registerTcpSocket(client);
        new Thread(new TCPTransformer(hostClient, client, hostReply), "new Transformer").start();
    }

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
            boolean isHtmlResponseChecked = false;

            while ((data = hostReply.host().receiveByte()) != null) {
                if (!isHtmlResponseChecked && !hostClient.getKey().isHTMLEnabled()) {
                    isHtmlResponseChecked = true;
                    checkAndBlockHtmlResponse(data, bufferedOutputStream, hostReply.host().getRemoteSocketAddress().toString(), hostClient, CUSTOM_BLOCKING_MESSAGE);
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
            // 此异常已被工厂方法处理，这里只需确保线程结束
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

    private static void checkAndBlockHtmlResponse(byte[] data, BufferedOutputStream clientOutput, String remoteSocketAddress, HostClient hostClient, String customMessage) throws IllegalWebSiteException, IOException {
        if (data == null || data.length == 0) {
            return;
        }

        String response = new String(data, StandardCharsets.UTF_8);
        int headerEndIndex = response.indexOf("\r\n\r\n");
        String headerPart = (headerEndIndex != -1) ? response.substring(0, headerEndIndex) : response;

        if (headerPart.toLowerCase().contains("content-type: text/html")) {
            myConsole.log("TCPTransformer","Detected web HTML from " + remoteSocketAddress.replaceAll("/",""));

            if (FORBIDDEN_HTML_TEMPLATE==null){
                return;
            }
            String finalHtml = FORBIDDEN_HTML_TEMPLATE.replace("{{CUSTOM_MESSAGE}}", customMessage != null ? customMessage : "");
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

    @Override
    public void run() {
        final double[] aTenMibSize = {0};
        Runnable clientToHostClientThread = () -> ClientToHost(hostClient, client, hostReply, aTenMibSize);
        Runnable hostClientToClientThread = () -> HostToClient(hostClient, hostReply, client, aTenMibSize);
        ThreadManager threadManager = new ThreadManager(clientToHostClientThread, hostClientToClientThread);
        threadManager.start();

        hostClient.unregisterTcpSocket(client);
        close(client, hostReply.host());
        InfoBox.sayClientTCPConnectDestroyInfo(hostClient, client);
    }
}