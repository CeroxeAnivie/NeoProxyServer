package neoproxy.neoproxyserver.core.webadmin;

import neoproxy.neoproxyserver.core.ServerLogger;
import plethora.thread.ThreadManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 端口统一网关
 * 监听公开端口 (8888)，根据请求头自动分流：
 * 1. WebSocket 升级请求 -> 转发给内部 WS 服务 (localhost:8889)
 * 2. 普通 HTTP 请求 -> 直接返回 HTML 页面
 */
public class PortUnificationServer {

    private final int publicPort;
    private final int internalWsPort;
    private ServerSocket serverSocket;
    private volatile boolean isRunning = false;

    public PortUnificationServer(int publicPort, int internalWsPort) {
        this.publicPort = publicPort;
        this.internalWsPort = internalWsPort;
    }

    public void start() {
        isRunning = true;
        ThreadManager.runAsync(() -> {
            try {
                serverSocket = new ServerSocket(publicPort);
                ServerLogger.info("WebAdmin", "Unified Gateway listening on port " + publicPort);

                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    // 为每个连接启动一个虚拟线程处理
                    ThreadManager.runAsync(() -> handleConnection(clientSocket));
                }
            } catch (IOException e) {
                if (isRunning) {
                    ServerLogger.errorWithSource("WebAdmin", "Gateway Error", e);
                }
            }
        });
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    private void handleConnection(Socket clientSocket) {
        try {
            // 读取请求头（预读模式，最大读取 4KB 头信息）
            PushbackInputStream pbis = new PushbackInputStream(clientSocket.getInputStream(), 4096);
            byte[] buffer = new byte[4096];

            // 尝试读取一部分数据进行分析
            int bytesRead = pbis.read(buffer);
            if (bytesRead == -1) {
                clientSocket.close();
                return;
            }

            // 将读取的数据推回流中，保证后续处理能读到完整数据
            pbis.unread(buffer, 0, bytesRead);

            String header = new String(buffer, 0, bytesRead, StandardCharsets.ISO_8859_1);

            // === 核心判断逻辑 ===
            if (header.contains("Upgrade: websocket") || header.contains("Upgrade: WebSocket")) {
                // 1. WebSocket 请求 -> 建立隧道转发给内部 8889
                proxyToWebSocket(clientSocket, pbis);
            } else {
                // 2. 普通 HTTP 请求 -> 直接响应 HTML
                serveHtml(clientSocket);
            }

        } catch (Exception e) {
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void serveHtml(Socket client) {
        try (client; OutputStream out = client.getOutputStream()) {
            try {
                // 读取 HTML 模板
                InputStream is = getClass().getClassLoader().getResourceAsStream("templates/webadmin/index.html");
                if (is == null) {
                    String error = "HTTP/1.1 404 Not Found\r\n\r\nError: index.html missing.";
                    out.write(error.getBytes(StandardCharsets.UTF_8));
                    return;
                }
                byte[] htmlBytes = is.readAllBytes();
                is.close();

                // 手动构建 HTTP 响应
                String head = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Content-Length: " + htmlBytes.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n";

                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.write(htmlBytes);
                out.flush();
            } catch (IOException e) {
                // ignore
            }
        } catch (IOException ignored) {
        }
    }

    private void proxyToWebSocket(Socket client, InputStream clientIn) {
        try {
            // 连接内部 WS 服务
            Socket backend = new Socket();
            backend.connect(new InetSocketAddress("127.0.0.1", internalWsPort), 1000);

            // 启动双向数据泵 (虚拟线程非常适合这种阻塞 IO)
            // 1. Client -> Backend
            ThreadManager.runAsync(() -> pipe(clientIn, backend));

            // 2. Backend -> Client
            ThreadManager.runAsync(() -> pipe(backend, client));

        } catch (IOException e) {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    // 辅助方法：因为 clientIn 是 PushbackInputStream，不能直接用 client.getInputStream()
    private void pipe(InputStream in, Socket dest) {
        try (dest; OutputStream out = dest.getOutputStream()) {
            try {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    out.flush();
                }
            } catch (IOException e) {
                // 管道断开是正常的
            }
        } catch (IOException ignored) {
        }
    }

    private void pipe(Socket source, Socket dest) {
        try (dest; InputStream in = source.getInputStream();
             OutputStream out = dest.getOutputStream()) {
            try {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    out.flush();
                }
            } catch (IOException e) {
                // 管道断开是正常的
            }
        } catch (IOException ignored) {
        }
    }
}