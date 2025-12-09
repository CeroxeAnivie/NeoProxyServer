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
import java.util.regex.Matcher;

import static neoproxy.neoproxyserver.NeoProxyServer.debugOperation;
import static neoproxy.neoproxyserver.core.InternetOperator.*;

public class TCPTransformer {

    public static int TELL_BALANCE_MIB = 10;
    public static int BUFFER_LEN = 8192;
    public static String CUSTOM_BLOCKING_MESSAGE = "如有疑问，请联系您的系统管理员。";

    private static String FORBIDDEN_HTML_TEMPLATE;

    static {
        // 既然确认资源绝对可达，这里保持原逻辑，但为了安全起见，
        // 建议在 catch 中也赋予一个包含 {{CUSTOM_MESSAGE}} 的简单模板，以防万一。
        try (InputStream inputStream = TCPTransformer.class.getResourceAsStream("/templates/forbidden.html")) {
            if (inputStream == null) {
                // 如果真的发生了不可能的情况，至少让 fallback 也能显示消息
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

                // 【修改 1】使用 replaceAll 和正则，允许占位符中存在空格 (例如 {{ CUSTOM_MESSAGE }})，提高容错率
                // 同时也使用了 Matcher.quoteReplacement 避免消息中包含 $ 或 \ 导致报错
                String finalHtml = template.replaceAll("\\{\\{\\s*CUSTOM_MESSAGE\\s*\\}\\}", Matcher.quoteReplacement(message));

                byte[] errorHtmlBytes = finalHtml.getBytes(StandardCharsets.UTF_8);

                // 【修改 2】添加 Cache-Control 头部，强制浏览器不要缓存 403 页面
                // 解决“消息修改后浏览器看不到”的问题
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
            int addrLen = srcIpBytes.length + dstIpBytes.length + 2 + 2;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(signature);
            baos.write(verCmd);
            baos.write(famTrans);
            baos.write((addrLen >> 8) & 0xFF);
            baos.write(addrLen & 0xFF);
            baos.write(srcIpBytes);
            baos.write(dstIpBytes);
            baos.write((srcPort >> 8) & 0xFF);
            baos.write(srcPort & 0xFF);
            baos.write((dstPort >> 8) & 0xFF);
            baos.write(dstPort & 0xFF);
            return baos.toByteArray();
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

        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(this.clientInputStream)) {
            RateLimiter limiter = hostClient.getGlobalRateLimiter();
            int len;
            while ((len = bufferedInputStream.read(clientToHostBuffer)) != -1) {
                if (len <= 0) continue;

                int enLength = hostReply.host().sendByte(clientToHostBuffer, 0, len);

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