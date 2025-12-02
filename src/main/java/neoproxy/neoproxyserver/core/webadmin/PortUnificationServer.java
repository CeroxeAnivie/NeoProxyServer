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
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

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

                    // 【阶段1：快速失败】
                    // 刚连接时，设置 5秒 超时。
                    // 针对只连接不发数据的扫描器/压测工具。
                    try {
                        clientSocket.setSoTimeout(5000);
                    } catch (Exception ignored) {
                    }

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
            // 【DNS 修复】绝对不要调用 clientSocket.getInetAddress().getHostName()
            // 否则会触发 20-50秒 的卡顿。

            PushbackInputStream pbis = new PushbackInputStream(clientSocket.getInputStream(), 4096);
            byte[] buffer = new byte[4096];

            int bytesRead;
            try {
                // 如果 5秒 内没收到数据，抛出异常关闭连接
                bytesRead = pbis.read(buffer);
            } catch (SocketTimeoutException e) {
                clientSocket.close(); // 踢掉空闲连接
                return;
            }

            if (bytesRead == -1) {
                clientSocket.close();
                return;
            }

            pbis.unread(buffer, 0, bytesRead);
            String header = new String(buffer, 0, bytesRead, StandardCharsets.ISO_8859_1);

            // === 动态策略切换 ===
            if (header.contains("Upgrade: websocket") || header.contains("Upgrade: WebSocket")) {
                // 【阶段2：WebSocket 模式】
                // 识别为 WebSocket 升级请求，这说明是自己人（或至少发了数据的）。
                // 立即移除超时限制（设为0），保证长连接不中断。
                try {
                    clientSocket.setSoTimeout(0);
                } catch (Exception ignored) {
                }

                proxyToWebSocket(clientSocket, pbis);
            } else {
                // 普通 HTTP 请求，保持原有超时或直接处理完关闭
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
                InputStream is = getClass().getClassLoader().getResourceAsStream("templates/webadmin/index.html");
                if (is == null) {
                    String error = "HTTP/1.1 404 Not Found\r\n\r\nError: index.html missing.";
                    out.write(error.getBytes(StandardCharsets.UTF_8));
                    return;
                }
                byte[] htmlBytes = is.readAllBytes();
                is.close();

                String head = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Content-Length: " + htmlBytes.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n";

                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.write(htmlBytes);
                out.flush();
            } catch (IOException ignored) {
            }
        } catch (IOException ignored) {
        }
    }

    private void proxyToWebSocket(Socket client, InputStream clientIn) {
        try {
            Socket backend = new Socket();
            backend.connect(new InetSocketAddress("127.0.0.1", internalWsPort), 3000);
            // 确保后端连接也是无限超时
            backend.setSoTimeout(0);

            ThreadManager.runAsync(() -> pipe(clientIn, backend));
            ThreadManager.runAsync(() -> pipe(backend, client));

        } catch (IOException e) {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void pipe(InputStream in, Socket dest) {
        try (dest; OutputStream out = dest.getOutputStream()) {
            try {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    out.flush();
                }
            } catch (IOException ignored) {
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
            } catch (IOException ignored) {
            }
        } catch (IOException ignored) {
        }
    }
}