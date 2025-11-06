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

/**
 * 【最终优化版】TCP数据传输器。
 * <p>
 * 优化点：
 * 1. 【架构重构】不再实现 Runnable，消除了外层对平台线程的依赖。
 * 2. 【异步启动】使用 ThreadManager.startAsyncWithCallback 来管理连接的两个数据流，实现完全的异步非阻塞处理。
 * 3. 【资源管理】将资源清理逻辑移至回调中，确保在连接生命周期结束时才执行，逻辑更清晰、更安全。
 * 4. 【异常传播】不再捕获 NoMoreNetworkFlowException，让其从 mineMib 直接传播到 ThreadManager。
 */
public class TCPTransformer {

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
    private final byte[] clientToHostBuffer = new byte[BUFFER_LEN];

    private TCPTransformer(HostClient hostClient, Socket client, HostReply hostReply) {
        this.hostClient = hostClient;
        this.client = client;
        this.hostReply = hostReply;
    }

    /**
     * 【优化】静态工厂方法，用于启动一个新的TCP连接转发。
     * 此方法是非阻塞的，它会立即返回。
     */
    public static void start(HostClient hostClient, HostReply hostReply, Socket client) {
        hostClient.registerTcpSocket(client);
        TCPTransformer transformer = new TCPTransformer(hostClient, client, hostReply);

        final double[] aTenMibSize = {0};

        Runnable clientToHostTask = () -> transformer.clientToHost(aTenMibSize);
        Runnable hostToClientTask = () -> transformer.hostToClient(aTenMibSize);

        // 【关键优化】使用 ThreadManager 管理两个数据流，并注册回调
        ThreadManager threadManager = new ThreadManager(clientToHostTask, hostToClientTask);
        threadManager.startAsyncWithCallback(result -> {
            // 【关键优化】当两个方向的数据流都结束时，此回调会被执行
            // 1. 检查是否有流量耗尽的异常
            for (Throwable t : result.exceptions()) {
                if (t instanceof NoMoreNetworkFlowException) {
                    // 异常已由 mineMib 方法处理（包括禁用密钥），这里只需踢下线
                    kickAllWithMsg(hostClient, hostReply.host(), client);
                    return; // 已经踢下线，无需继续清理
                }
            }
            // 2. 执行常规的资源清理
            hostClient.unregisterTcpSocket(client);
            close(client, hostReply.host());
            ServerLogger.sayClientTCPConnectDestroyInfo(hostClient, client);
        });
    }

    // --- 以下方法保持不变 ---

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

    private void clientToHost(double[] aTenMibSize) {
        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(client.getInputStream())) {
            RateLimiter limiter = new RateLimiter(hostClient.getKey().getRate());
            int len;
            while ((len = bufferedInputStream.read(clientToHostBuffer)) != -1) {
                int enLength = hostReply.host().sendByte(clientToHostBuffer, 0, len);
                // mineMib 会抛出 NoMoreNetworkFlowException，我们不再捕获，让它向上传播
                hostClient.getKey().mineMib("TCP-Transformer:C->H", SizeCalculator.byteToMib(enLength + 10));
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
        }
        // 【移除】 catch (NoMoreNetworkFlowException e) 块
    }

    private void hostToClient(double[] aTenMibSize) {
        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(client.getOutputStream())) {
            RateLimiter limiter = new RateLimiter(hostClient.getKey().getRate());
            byte[] data;
            boolean isHtmlResponseChecked = false;
            while ((data = hostReply.host().receiveByte()) != null) {
                if (!isHtmlResponseChecked && !hostClient.getKey().isHTMLEnabled()) {
                    isHtmlResponseChecked = true;
                    checkAndBlockHtmlResponse(data, bufferedOutputStream, hostReply.host().getRemoteSocketAddress().toString(), hostClient);
                }
                bufferedOutputStream.write(data);
                bufferedOutputStream.flush();
                // mineMib 会抛出 NoMoreNetworkFlowException，我们不再捕获，让它向上传播
                hostClient.getKey().mineMib("TCP-Transformer:H->C", SizeCalculator.byteToMib(data.length));
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
        } catch (IllegalWebSiteException e) {
            // 此异常已被处理，只需确保线程结束
        }
        // 【移除】 catch (NoMoreNetworkFlowException e) 块
    }
}