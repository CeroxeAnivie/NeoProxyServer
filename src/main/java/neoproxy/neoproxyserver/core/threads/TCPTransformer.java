package neoproxy.neoproxyserver.core.threads;

import fun.ceroxe.api.management.bufferedFile.SizeCalculator;
import fun.ceroxe.api.net.SecureSocket;
import fun.ceroxe.api.thread.ThreadManager;
import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.HostReply;
import neoproxy.neoproxyserver.core.LanguageData;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.exceptions.IllegalWebSiteException;
import neoproxy.neoproxyserver.core.exceptions.NoMoreNetworkFlowException;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

import static neoproxy.neoproxyserver.core.Debugger.debugOperation;
import static neoproxy.neoproxyserver.core.InternetOperator.*;

public class TCPTransformer {

    public static int TELL_BALANCE_MIB = 10;
    public static int BUFFER_LEN = 8192;
    public static String CUSTOM_BLOCKING_MESSAGE = "如有疑问，请联系您的系统管理员。";

    private static String FORBIDDEN_HTML_TEMPLATE;

    static {
        try (InputStream inputStream = TCPTransformer.class.getResourceAsStream("/templates/forbidden.html")) {
            if (inputStream == null) {
                FORBIDDEN_HTML_TEMPLATE = "<html><body><h1>403 Forbidden</h1><p>{{CUSTOM_MESSAGE}}</p></body></html>";
            } else {
                FORBIDDEN_HTML_TEMPLATE = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            debugOperation(e);
            FORBIDDEN_HTML_TEMPLATE = "<html><body><h1>403 Forbidden</h1><p>IO Error: {{CUSTOM_MESSAGE}}</p></body></html>";
        }
    }

    private final HostClient hostClient;
    private final Socket client;
    private final HostReply hostReply;
    private final InputStream clientInputStream;

    // 【优化】移除了 clientToHostBuffer 字段，内存更节省

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

    private static boolean checkAndBlockHtmlResponse(byte[] data, Socket clientSocket, String remoteSocketAddress, HostClient hostClient) throws IOException {
        if (data == null || data.length == 0) return false;

        int limit = Math.min(data.length, 8192);
        String headerRaw = new String(data, 0, limit, StandardCharsets.ISO_8859_1);

        int headerEndIndex = headerRaw.indexOf("\r\n\r\n");
        if (headerEndIndex == -1) return false;

        String headersSection = headerRaw.substring(0, headerEndIndex).toLowerCase();
        if (!headersSection.startsWith("http/")) return false;

        boolean isHtml = false;
        boolean isAttachment = false;

        String[] lines = headersSection.split("\r\n");
        for (String line : lines) {
            String cleanLine = line.trim();
            if (cleanLine.startsWith("content-type:")) {
                if (cleanLine.contains("text/html")) {
                    isHtml = true;
                }
            } else if (cleanLine.startsWith("content-disposition:")) {
                if (cleanLine.contains("attachment")) {
                    isAttachment = true;
                }
            }
        }

        if (isHtml && !isAttachment) {
            try (BufferedOutputStream out = new BufferedOutputStream(clientSocket.getOutputStream())) {
                String template = (FORBIDDEN_HTML_TEMPLATE != null) ? FORBIDDEN_HTML_TEMPLATE : "<h1>403 Forbidden</h1><p>{{CUSTOM_MESSAGE}}</p>";
                String message = CUSTOM_BLOCKING_MESSAGE != null ? CUSTOM_BLOCKING_MESSAGE : "";
                String finalHtml = template.replaceAll("\\{\\{\\s*CUSTOM_MESSAGE\\s*\\}\\}", Matcher.quoteReplacement(message));
                byte[] errorHtmlBytes = finalHtml.getBytes(StandardCharsets.UTF_8);

                String httpResponseHeader = "HTTP/1.1 403 Forbidden\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: " + errorHtmlBytes.length + "\r\n" +
                        "Connection: close\r\n" +
                        "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                        "Pragma: no-cache\r\n" +
                        "Expires: 0\r\n" +
                        "\r\n";

                out.write(httpResponseHeader.getBytes(StandardCharsets.UTF_8));
                out.write(errorHtmlBytes);
                out.flush();

                clientSocket.shutdownOutput();
                Thread.sleep(800);
            } catch (Exception e) {
                // ignore
            }

            try {
                IllegalWebSiteException.throwException(hostClient.getKey().getName());
            } catch (IllegalWebSiteException e) {
                // ignore
            }
            return true;
        }

        return false;
    }

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

            byte[] signature = new byte[]{
                    (byte) 0x0D, (byte) 0x0A, (byte) 0x0D, (byte) 0x0A,
                    (byte) 0x00, (byte) 0x0D, (byte) 0x0A, (byte) 0x51,
                    (byte) 0x55, (byte) 0x49, (byte) 0x54, (byte) 0x0A
            };
            byte verCmd = (byte) 0x21;
            byte famTrans = isIPv4 ? (byte) 0x11 : (byte) 0x21;
            byte[] srcIpBytes = srcIp.getAddress();
            byte[] dstIpBytes = dstIp.getAddress();

            // 计算 PPv2 地址段长度: SRC_IP + DST_IP + SRC_PORT(2) + DST_PORT(2)
            int addrLen = srcIpBytes.length + dstIpBytes.length + 2 + 2;

            // 【严谨优化】计算精确的 Buffer 大小: Header(16) + addrLen
            // Header: Sig(12) + Ver(1) + Fam(1) + Len(2) = 16 bytes
            int totalLen = 16 + addrLen;
            ByteBuffer buffer = ByteBuffer.allocate(totalLen);

            buffer.put(signature);
            buffer.put(verCmd);
            buffer.put(famTrans);
            buffer.putShort((short) addrLen);
            buffer.put(srcIpBytes);
            buffer.put(dstIpBytes);
            buffer.putShort((short) srcPort);
            buffer.putShort((short) dstPort);

            return buffer.array();
        } catch (Exception e) {
            debugOperation(e);
            return null;
        }
    }

    private void clientToHost(double[] aTenMibSize) {
        try {
            byte[] ppHeader = createProxyProtocolV2Header(this.client);
            if (ppHeader != null) {
                hostReply.host().sendByte(ppHeader, 0, ppHeader.length);
            }
        } catch (Exception e) {
            debugOperation(e);
        }

        // 【优化】局部变量分配，随着方法结束自动回收
        byte[] buffer = new byte[BUFFER_LEN];

        // 【优化】直接使用 Socket 的 InputStream，移除冗余的 BufferedInputStream
        try (InputStream input = this.clientInputStream) {
            RateLimiter limiter = hostClient.getGlobalRateLimiter();
            int len;
            while ((len = input.read(buffer)) != -1) {
                if (len <= 0) continue;

                int enLength = hostReply.host().sendByte(buffer, 0, len);

                if (enLength > 0) {
                    hostClient.getKey().mineMib("TCP-Transformer:C->H", SizeCalculator.byteToMib(enLength + 10));
                    tellRestBalance(hostClient, aTenMibSize, enLength, hostClient.getLangData());
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
            RateLimiter limiter = hostClient.getGlobalRateLimiter();

            byte[] data;
            boolean isHtmlResponseChecked = false;

            while ((data = hostReply.host().receiveByte()) != null) {
                if (data.length == 0) continue;

                if (!isHtmlResponseChecked && !hostClient.getKey().isHTMLEnabled()) {
                    isHtmlResponseChecked = true;
                    if (checkAndBlockHtmlResponse(data, client, hostReply.host().getRemoteSocketAddress().toString(), hostClient)) {
                        return;
                    }
                }

                bufferedOutputStream.write(data);
                bufferedOutputStream.flush();

                hostClient.getKey().mineMib("TCP-Transformer:H->C", SizeCalculator.byteToMib(data.length));
                tellRestBalance(hostClient, aTenMibSize, data.length, hostClient.getLangData());
                limiter.setMaxMbps(hostClient.getKey().getRate());
                limiter.onBytesTransferred(data.length);
            }
            shutdownInput(hostReply.host());
            shutdownOutput(client);
        } catch (IOException e) {
            debugOperation(e);
            shutdownInput(hostReply.host());
            shutdownOutput(client);
        }
    }
}