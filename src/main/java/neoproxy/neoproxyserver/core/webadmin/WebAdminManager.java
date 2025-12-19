package neoproxy.neoproxyserver.core.webadmin;

import fun.ceroxe.api.thread.ThreadManager;
import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.ServerLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * WebAdminManager (Java 21 Virtual Threads Compatible)
 * <p>
 * 修改：使用 ReentrantLock + Condition 替代 synchronized + wait。
 */
public class WebAdminManager {

    private static final long TEMP_TOKEN_VALIDITY_MS = 5 * 60 * 1000;

    // 【修改】使用 ReentrantLock 替代 Object lock
    private static final ReentrantLock MANAGER_LOCK = new ReentrantLock();
    // 【修改】用于 restart 中的等待
    private static final Condition RESTART_CONDITION = MANAGER_LOCK.newCondition();

    public static int WEB_ADMIN_PORT = 44803;

    // 内部运行的真实服务器
    private static WebAdminServer internalServer;
    // 外部监听的网关 Socket
    private static ServerSocket gatewaySocket;

    private static volatile boolean isRunning = false;
    private static volatile boolean isStarting = false;
    private static String currentTempToken = null;
    private static long tempTokenCreatedAt = 0;
    private static String permanentToken = "";

    public static void setPermanentToken(String token) {
        permanentToken = token;
    }

    public static void init() {
        if (isRunning || isStarting) return;
        if (permanentToken != null && !permanentToken.isEmpty()) {
            ServerLogger.infoWithSource("WebAdmin", "webAdmin.securityEnabled");
        }
        startServer();
    }

    private static void startServer() {
        MANAGER_LOCK.lock();
        try {
            if (isRunning || isStarting) return;
            isStarting = true;
        } finally {
            MANAGER_LOCK.unlock();
        }

        ThreadManager.runAsync(() -> {
            try {
                MANAGER_LOCK.lock();
                try {
                    if (!isStarting) return;

                    // 1. 启动内部 NanoHTTPD 服务器 (绑定到 localhost 随机端口)
                    internalServer = new WebAdminServer(0); // 0 = 随机端口
                    internalServer.start(5000, false);

                    // 获取内部服务器实际绑定的端口
                    int internalPort = internalServer.getListeningPort();

                    // 2. 启动对外网关 (绑定到 WEB_ADMIN_PORT 44803)
                    gatewaySocket = new ServerSocket(WEB_ADMIN_PORT);

                    isRunning = true;
                    isStarting = false;

                    ServerLogger.infoWithSource("WebAdmin", "webAdmin.started", WEB_ADMIN_PORT);

                    // 3. 开始接收并过滤流量
                    // 注意：accept 是阻塞操作，不应持有锁
                } finally {
                    MANAGER_LOCK.unlock();
                }

                // 在锁外运行 accept 循环
                while (isRunning) {
                    try {
                        // 这里的 Socket accept 不需要锁
                        Socket client = gatewaySocket.accept();
                        // 为每个连接启动一个 Shield 处理器
                        ThreadManager.runAsync(() -> handleShieldConnection(client, internalServer.getListeningPort()));
                    } catch (IOException e) {
                        if (isRunning) ServerLogger.errorWithSource("WebAdmin", "Accept Error", e);
                    } catch (NullPointerException e) {
                        // internalServer 可能为空（shutdown时）
                        break;
                    }
                }

            } catch (IOException e) {
                // 启动失败处理
                MANAGER_LOCK.lock();
                try {
                    isRunning = false;
                    isStarting = false;
                    if (internalServer != null) internalServer.stop();
                    internalServer = null;
                    if (gatewaySocket != null) try {
                        gatewaySocket.close();
                    } catch (Exception ignored) {
                    }
                    gatewaySocket = null;
                } finally {
                    MANAGER_LOCK.unlock();
                }

                if (e.getMessage() != null && e.getMessage().contains("bind")) {
                    ServerLogger.errorWithSource("WebAdmin", "webAdmin.bindFailed", WEB_ADMIN_PORT);
                } else {
                    ServerLogger.errorWithSource("WebAdmin", "webAdmin.startFailed", e.getMessage());
                }
            }
        });
    }

    /**
     * 网关核心逻辑：首包探测 + IP 注入
     */
    private static void handleShieldConnection(Socket client, int internalPort) {
        try {
            // A. 设置 200ms 超极速超时
            client.setSoTimeout(200);

            String remoteIp = client.getInetAddress().getHostAddress(); // 提前获取IP，避免DNS解析

            PushbackInputStream pbis = new PushbackInputStream(client.getInputStream(), 4096);
            byte[] buffer = new byte[4096];

            // B. 尝试读取首包 (Peek)
            int bytesRead;
            try {
                bytesRead = pbis.read(buffer);
            } catch (SocketTimeoutException e) {
                // 读取超时 -> 说明是只连不发的 TCPing -> 直接断开
                client.close();
                return;
            }

            if (bytesRead == -1) {
                client.close();
                return;
            }

            // C. 验证通过，恢复正常超时 (30秒)
            client.setSoTimeout(30000);

            // D. 连接内部服务器
            Socket backend = new Socket();
            backend.connect(new InetSocketAddress("127.0.0.1", internalPort), 1000);
            backend.setSoTimeout(0); // 内部连接无限超时

            // E. 注入 X-Neo-Real-IP 头 (针对 HTTP 流量)
            OutputStream backendOut = backend.getOutputStream();

            // 寻找请求行的结束位置 (\r\n)
            int firstLineEnd = -1;
            for (int i = 0; i < bytesRead - 1; i++) {
                if (buffer[i] == '\r' && buffer[i + 1] == '\n') {
                    firstLineEnd = i;
                    break;
                }
            }

            if (firstLineEnd != -1) {
                // 是 HTTP 请求 -> 注入 Header
                backendOut.write(buffer, 0, firstLineEnd + 2);
                String header = "X-Neo-Real-IP: " + remoteIp + "\r\n";
                backendOut.write(header.getBytes(StandardCharsets.UTF_8));
                backendOut.write(buffer, firstLineEnd + 2, bytesRead - (firstLineEnd + 2));
            } else {
                backendOut.write(buffer, 0, bytesRead);
            }
            backendOut.flush();

            // F. 启动全双工管道
            ThreadManager.runAsync(() -> pipe(pbis, backend));
            ThreadManager.runAsync(() -> pipe(backend, client));

        } catch (Exception e) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void pipe(InputStream in, Socket dest) {
        try (dest; OutputStream out = dest.getOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
                out.flush();
            }
        } catch (Exception ignored) {
        }
    }

    private static void pipe(Socket src, Socket dest) {
        try (dest; InputStream in = src.getInputStream(); OutputStream out = dest.getOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
                out.flush();
            }
        } catch (Exception ignored) {
        }
    }

    public static void shutdown() {
        MANAGER_LOCK.lock();
        try {
            isStarting = false;
            isRunning = false;
            if (internalServer != null) {
                internalServer.stop();
                internalServer = null;
            }
            if (gatewaySocket != null) {
                try {
                    gatewaySocket.close();
                } catch (Exception ignored) {
                }
                gatewaySocket = null;
            }
        } finally {
            MANAGER_LOCK.unlock();
        }
    }

    public static void restart() {
        MANAGER_LOCK.lock();
        try {
            shutdown();
            try {
                // 使用 Condition 等待，释放锁，给操作系统一点时间释放端口
                RESTART_CONDITION.await(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
            startServer();
        } finally {
            MANAGER_LOCK.unlock();
        }
    }

    public static void broadcastLog(String message) {
        if (isRunning && internalServer != null) {
            WebAdminServer.broadcastLog(message);
        }
    }

    public static String generateNewSessionUrl() {
        if (isRunning && internalServer != null) {
            internalServer.closeTempConnections();
        }

        currentTempToken = UUID.randomUUID().toString();
        tempTokenCreatedAt = System.currentTimeMillis();

        String host = NeoProxyServer.LOCAL_DOMAIN_NAME;
        if (host == null || host.trim().isEmpty() || "0.0.0.0".equals(host)) {
            host = "127.0.0.1";
        }

        return "http://" + host + ":" + WEB_ADMIN_PORT + "/?token=" + currentTempToken;
    }

    public static int verifyTokenAndGetType(String token) {
        if (!isRunning || token == null) return 0;
        if (permanentToken != null && !permanentToken.isEmpty() && permanentToken.equals(token)) {
            return 2;
        }
        if (currentTempToken != null && currentTempToken.equals(token)) {
            long now = System.currentTimeMillis();
            if ((now - tempTokenCreatedAt) <= TEMP_TOKEN_VALIDITY_MS) {
                return 1;
            }
        }
        return 0;
    }

    public static boolean verifyToken(String token) {
        return verifyTokenAndGetType(token) > 0;
    }

    public static boolean isRunning() {
        return isRunning;
    }
}