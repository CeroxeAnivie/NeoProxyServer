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

        // 如果设置了永久Token，输出安全已启用的日志
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
                    // 0 = 无限超时
                    server.start(0, false);
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

                // 检查是否是端口绑定失败
                if (e.getMessage() != null &&
                        (e.getMessage().contains("bind") ||
                                e.getMessage().contains("Address already in use") ||
                                e.getMessage().contains("Permission denied"))) {

                    // 端口绑定失败
                    if (NeoProxyServer.IS_DEBUG_MODE) {
                        ServerLogger.errorWithSource("WebAdmin", "webAdmin.bindFailedDebug", e.getMessage());
                    } else {
                        ServerLogger.errorWithSource("WebAdmin", "webAdmin.bindFailed", WEB_ADMIN_PORT);
                    }
                } else {
                    // 其他启动失败
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
        // 仅重置临时 Token 的连接
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

    /**
     * 验证 Token 并返回 Token 类型
     *
     * @return 0=无效, 1=临时Token, 2=永久Token
     */
    public static int verifyTokenAndGetType(String token) {
        if (!isRunning || token == null) return 0;

        // 1. 检查永久 Token
        if (permanentToken != null && !permanentToken.isEmpty() && permanentToken.equals(token)) {
            return 2;
        }

        // 2. 检查临时 Token
        if (currentTempToken != null && currentTempToken.equals(token)) {
            long now = System.currentTimeMillis();
            if ((now - tempTokenCreatedAt) <= TEMP_TOKEN_VALIDITY_MS) {
                return 1;
            }
        }

        return 0;
    }

    // 为了兼容 HTTP 接口保留的方法
    public static boolean verifyToken(String token) {
        return verifyTokenAndGetType(token) > 0;
    }

    // 【修复】添加缺失的公共访问方法，供 ConfigOperator 调用
    public static boolean isRunning() {
        return isRunning;
    }
}