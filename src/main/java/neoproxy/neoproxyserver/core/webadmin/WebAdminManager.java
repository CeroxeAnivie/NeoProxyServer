package neoproxy.neoproxyserver.core.webadmin;

import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.ServerLogger;
import plethora.thread.ThreadManager;

import java.io.IOException;
import java.util.UUID;

public class WebAdminManager {

    private static final long TEMP_TOKEN_VALIDITY_MS = 5 * 60 * 1000;
    private static final Object MANAGER_LOCK = new Object();
    public static int WEB_ADMIN_PORT = 44803;
    private static WebAdminServer server;
    private static volatile boolean isRunning = false;
    private static volatile boolean isStarting = false;
    // 临时 Token (通过 webadmin 命令生成)
    private static String currentTempToken = null;
    private static long tempTokenCreatedAt = 0;
    // 永久 Token (从配置文件读取)
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
        synchronized (MANAGER_LOCK) {
            if (isRunning || isStarting) return;
            isStarting = true;
        }

        ThreadManager.runAsync(() -> {
            try {
                synchronized (MANAGER_LOCK) {
                    if (!isStarting) return;
                    server = new WebAdminServer(WEB_ADMIN_PORT);

                    // 【安全修复】设置为 30000ms (30秒) 超时
                    // 1. 为什么不是 5秒？因为前端心跳间隔是 5秒，如果网络抖动，5秒超时会导致误杀。
                    // 2. 为什么不是 0 (无限)？防止 TCPing 或僵尸连接耗尽线程资源。
                    // 3. 30秒足够容纳多次心跳重试，同时能保证死连接会被回收。
                    server.start(30000, false);

                    isRunning = true;
                    isStarting = false;
                }
                ServerLogger.infoWithSource("WebAdmin", "webAdmin.started", WEB_ADMIN_PORT);
            } catch (IOException e) {
                synchronized (MANAGER_LOCK) {
                    isRunning = false;
                    isStarting = false;
                    server = null;
                }

                if (e.getMessage() != null &&
                        (e.getMessage().contains("bind") ||
                                e.getMessage().contains("Address already in use") ||
                                e.getMessage().contains("Permission denied"))) {
                    if (NeoProxyServer.IS_DEBUG_MODE) {
                        ServerLogger.errorWithSource("WebAdmin", "webAdmin.bindFailedDebug", e.getMessage());
                    } else {
                        ServerLogger.errorWithSource("WebAdmin", "webAdmin.bindFailed", WEB_ADMIN_PORT);
                    }
                } else {
                    ServerLogger.errorWithSource("WebAdmin", "webAdmin.startFailed", e.getMessage());
                }
            } catch (Exception e) {
                synchronized (MANAGER_LOCK) {
                    isRunning = false;
                    isStarting = false;
                    server = null;
                }
                ServerLogger.errorWithSource("WebAdmin", "webAdmin.startFailed", e.getMessage());
            }
        });
    }

    public static void shutdown() {
        synchronized (MANAGER_LOCK) {
            isStarting = false;
            isRunning = false;
            if (server != null) {
                server.stop();
                server = null;
            }
        }
    }

    public static void restart() {
        synchronized (MANAGER_LOCK) {
            shutdown();
            try {
                MANAGER_LOCK.wait(200);
            } catch (InterruptedException ignored) {
            }
            startServer();
        }
    }

    public static void broadcastLog(String message) {
        if (isRunning && server != null) {
            WebAdminServer.broadcastLog(message);
        }
    }

    public static String generateNewSessionUrl() {
        if (isRunning && server != null) {
            server.closeTempConnections();
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