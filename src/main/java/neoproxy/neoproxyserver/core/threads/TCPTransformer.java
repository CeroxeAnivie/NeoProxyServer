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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static neoproxy.neoproxyserver.NeoProxyServer.debugOperation;
import static neoproxy.neoproxyserver.NeoProxyServer.myConsole;
import static neoproxy.neoproxyserver.core.InternetOperator.*;
import static neoproxy.neoproxyserver.core.ServerLogger.alert;

public class TCPTransformer {

    public static int TELL_BALANCE_MIB = 10;
    public static int BUFFER_LEN = 8192;
    public static String CUSTOM_BLOCKING_MESSAGE = "如有疑问，请联系您的系统管理员。";

    private static String FORBIDDEN_HTML_TEMPLATE;

    static {
        try (InputStream inputStream = TCPTransformer.class.getResourceAsStream("/templates/forbidden.html")) {
            if (inputStream == null) {
                // 如果找不到模板，则 FORBIDDEN_HTML_TEMPLATE 为 null
            } else {
                FORBIDDEN_HTML_TEMPLATE = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            debugOperation(e);
            FORBIDDEN_HTML_TEMPLATE = null;
        }
    }

    private final HostClient hostClient;
    private final Socket client;
    private final HostReply hostReply;
    private final byte[] clientToHostBuffer = new byte[BUFFER_LEN];
    private final InputStream clientInputStream;

    private TCPTransformer(HostClient hostClient, Socket client, HostReply hostReply, InputStream clientInputStream) {
        this.hostClient = hostClient;
        this.client = client;
        this.hostReply = hostReply;
        try {
            this.clientInputStream = (clientInputStream != null) ? clientInputStream : client.getInputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void start(HostClient hostClient, HostReply hostReply, Socket client, InputStream preCheckedStream) {
        hostClient.registerTcpSocket(client);
        TCPTransformer transformer = new TCPTransformer(hostClient, client, hostReply, preCheckedStream);

        final double[] aTenMibSize = {0};

        Runnable clientToHostTask = () -> transformer.clientToHost(aTenMibSize);
        Runnable hostToClientTask = () -> transformer.hostToClient(aTenMibSize);

        ThreadManager threadManager = new ThreadManager(clientToHostTask, hostToClientTask);
        threadManager.startAsyncWithCallback(result -> {
            for (Throwable t : result.exceptions()) {
                if (t instanceof NoMoreNetworkFlowException) {
                    kickAllWithMsg(hostClient, hostReply.host(), client);
                    return;
                }
            }
            hostClient.unregisterTcpSocket(client);
            close(client, hostReply.host());
            ServerLogger.sayClientTCPConnectDestroyInfo(hostClient, client);
        });
    }

    public static void start(HostClient hostClient, HostReply hostReply, Socket client) {
        start(hostClient, hostReply, client, null);
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

    private static void checkAndBlockHtmlResponse(byte[] data, BufferedOutputStream clientOutput, String remoteSocketAddress, HostClient hostClient) throws IllegalWebSiteException, IOException {
        if (data == null || data.length == 0) return;
        String response = new String(data, StandardCharsets.UTF_8);
        int headerEndIndex = response.indexOf("\r\n\r\n");
        String headerPart = (headerEndIndex != -1) ? response.substring(0, headerEndIndex) : response;
        if (headerPart.toLowerCase().contains("content-type: text/html")) {
            if (alert) {
                myConsole.log("TCPTransformer", "Detected web HTML from " + remoteSocketAddress.replaceAll("/", ""));
            }
            if (FORBIDDEN_HTML_TEMPLATE == null) return;

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
     * 构建 Proxy Protocol v2 二进制头
     */
    private static byte[] createProxyProtocolV2Header(Socket clientSocket) {
        try {
            InetSocketAddress srcAddress = (InetSocketAddress) clientSocket.getRemoteSocketAddress();
            InetSocketAddress dstAddress = (InetSocketAddress) clientSocket.getLocalSocketAddress();

            if (srcAddress == null || dstAddress == null) return null;

            InetAddress srcIp = srcAddress.getAddress();
            InetAddress dstIp = dstAddress.getAddress();
            int srcPort = srcAddress.getPort();
            int dstPort = dstAddress.getPort();

            boolean isIPv4 = srcIp instanceof Inet4Address;

            // 1. 协议签名 (12 bytes)
            byte[] signature = new byte[]{
                    (byte) 0x0D, (byte) 0x0A, (byte) 0x0D, (byte) 0x0A,
                    (byte) 0x00, (byte) 0x0D, (byte) 0x0A, (byte) 0x51,
                    (byte) 0x55, (byte) 0x49, (byte) 0x54, (byte) 0x0A
            };

            // 2. Version & Command (0x21 = v2, PROXY)
            byte verCmd = (byte) 0x21;

            // 3. Family & Transport (0x11 = IPv4 TCP, 0x21 = IPv6 TCP)
            byte famTrans = isIPv4 ? (byte) 0x11 : (byte) 0x21;

            // 4. 地址数据构建
            byte[] srcIpBytes = srcIp.getAddress();
            byte[] dstIpBytes = dstIp.getAddress();

            // 计算地址部分总长度
            int addrLen = srcIpBytes.length + dstIpBytes.length + 2 + 2;

            // 5. 组合最终包
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(signature);
            baos.write(verCmd);
            baos.write(famTrans);

            // 写入长度 (Big Endian short)
            baos.write((addrLen >> 8) & 0xFF);
            baos.write(addrLen & 0xFF);

            // 写入源IP 和 目的IP
            baos.write(srcIpBytes);
            baos.write(dstIpBytes);

            // 写入源端口 (Big Endian short)
            baos.write((srcPort >> 8) & 0xFF);
            baos.write(srcPort & 0xFF);

            // 写入目的端口 (Big Endian short)
            baos.write((dstPort >> 8) & 0xFF);
            baos.write(dstPort & 0xFF);

            return baos.toByteArray();

        } catch (Exception e) {
            debugOperation(e);
            return null;
        }
    }

    private void clientToHost(double[] aTenMibSize) {
        // =========================================================================
        // 无条件发送 Proxy Protocol v2 头
        // 客户端将根据自身配置决定是否转发此头给后端
        // =========================================================================
        try {
            byte[] ppHeader = createProxyProtocolV2Header(this.client);
            if (ppHeader != null) {
                hostReply.host().sendByte(ppHeader, 0, ppHeader.length);
            }
        } catch (Exception e) {
            debugOperation(e);
        }
        // =========================================================================

        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(this.clientInputStream)) {
            // 获取全局限速器
            RateLimiter limiter = hostClient.getGlobalRateLimiter();

            int len;
            while ((len = bufferedInputStream.read(clientToHostBuffer)) != -1) {
                if (len <= 0) continue;

                int enLength = hostReply.host().sendByte(clientToHostBuffer, 0, len);

                if (enLength > 0) {
                    hostClient.getKey().mineMib("TCP-Transformer:C->H", SizeCalculator.byteToMib(enLength + 10));
                    tellRestBalance(hostClient, aTenMibSize, enLength, hostClient.getLangData());

                    // 调用共享限速器
                    limiter.setMaxMbps(hostClient.getKey().getRate());
                    limiter.onBytesTransferred(enLength);
                }
            }
            hostReply.host().sendByte(null);
            shutdownOutput(hostReply.host());
            shutdownInput(client);
        } catch (IOException e) {
            debugOperation(e);
            shutdownOutput(hostReply.host());
            shutdownInput(client);
        }
    }

    private void hostToClient(double[] aTenMibSize) {
        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(client.getOutputStream())) {
            // 获取全局限速器
            RateLimiter limiter = hostClient.getGlobalRateLimiter();

            byte[] data;
            boolean isHtmlResponseChecked = false;
            while ((data = hostReply.host().receiveByte()) != null) {
                if (data.length == 0) continue;

                if (!isHtmlResponseChecked && !hostClient.getKey().isHTMLEnabled()) {
                    isHtmlResponseChecked = true;
                    checkAndBlockHtmlResponse(data, bufferedOutputStream, hostReply.host().getRemoteSocketAddress().toString(), hostClient);
                }
                bufferedOutputStream.write(data);
                bufferedOutputStream.flush();

                hostClient.getKey().mineMib("TCP-Transformer:H->C", SizeCalculator.byteToMib(data.length));
                tellRestBalance(hostClient, aTenMibSize, data.length, hostClient.getLangData());

                // 调用共享限速器
                limiter.setMaxMbps(hostClient.getKey().getRate());
                limiter.onBytesTransferred(data.length);
            }
            shutdownInput(hostReply.host());
            shutdownOutput(client);
        } catch (IOException e) {
            debugOperation(e);
            shutdownInput(hostReply.host());
            shutdownOutput(client);
        } catch (IllegalWebSiteException ignored) {
        }
    }
}