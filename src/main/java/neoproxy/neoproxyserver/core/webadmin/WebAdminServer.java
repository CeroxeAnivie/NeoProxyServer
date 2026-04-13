package neoproxy.neoproxyserver.core.webadmin;

import fun.ceroxe.api.thread.ThreadManager;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import io.javalin.websocket.WsCloseContext;
import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.Debugger;
import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.management.IPGeolocationHelper;
import neoproxy.neoproxyserver.core.threads.UDPTransformer;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class WebAdminServer {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LinkedList<String> logHistory = new LinkedList<>();
    private static final int MAX_HISTORY_SIZE = 1000;
    private static final ReentrantLock GLOBAL_LOCK = new ReentrantLock();
    private static final long ZOMBIE_TIMEOUT_MS = 10000;
    private static final Map<String, Long> lastConflictWarning = new HashMap<>();
    private static volatile AdminWebSocket activeTempSocket = null;
    private static volatile String activeTempSessionId = null;
    private static volatile AdminWebSocket activePermSocket = null;
    private static volatile String activePermSessionId = null;
    private static long lastTotalBytes = 0;
    private static long lastCalcTime = System.currentTimeMillis();
    private static double currentSpeedBps = 0;

    private final int port;
    private Javalin app;
    private volatile boolean isRunning = false;
    private String sslCertPath = "";
    private String sslKeyPath = "";
    private String sslPassword = "";

    public WebAdminServer(int port) {
        this.port = port;
    }

    public void setSslConfig(String certPath, String keyPath, String password) {
        this.sslCertPath = certPath != null ? certPath : "";
        this.sslKeyPath = keyPath != null ? keyPath : "";
        this.sslPassword = password != null ? password : "";
    }

    private boolean isSslEnabled() {
        return !sslCertPath.isEmpty() && !sslKeyPath.isEmpty();
    }

    public void start() {
        if (isRunning) return;
        app = Javalin.create(this::configureJavalin);
        setupRoutes();
        setupWebSocket();
        // SSL 模式下使用 modifyServer 配置了端口，不需要传入 port
        if (isSslEnabled()) {
            app.start();
        } else {
            app.start(port);
        }
        isRunning = true;
    }

    // 兼容旧 API 的重载方法
    public void start(int timeout, boolean daemon) {
        start();
    }

    private void configureJavalin(JavalinConfig config) {
        config.showJavalinBanner = false;
        config.useVirtualThreads = true;

        // 配置 SSL（如果启用）
        if (isSslEnabled()) {
            config.jetty.modifyServer(server -> {
                // 使用 BouncyCastle 加载 PEM 证书
                KeyStore keyStore = loadPemToKeyStore();
                if (keyStore == null) {
                    throw new RuntimeException("Failed to load SSL certificates");
                }

                SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
                sslContextFactory.setKeyStore(keyStore);
                sslContextFactory.setKeyStorePassword("");

                ServerConnector sslConnector = new ServerConnector(server, sslContextFactory);
                sslConnector.setPort(port);

                server.setConnectors(new org.eclipse.jetty.server.Connector[]{sslConnector});
                ServerLogger.infoWithSource("WebAdmin", "webAdmin.sslStarted", port);
            });
        }
    }

    /**
     * 使用 BouncyCastle 将 PEM 格式证书转换为 KeyStore
     */
    private KeyStore loadPemToKeyStore() {
        try {
            // 注册 BouncyCastle Provider
            if (Security.getProvider("BC") == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            // 读取证书文件
            X509Certificate certificate;
            try (PEMParser pemParser = new PEMParser(new FileReader(sslCertPath))) {
                Object object = pemParser.readObject();
                if (object instanceof X509CertificateHolder) {
                    JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
                    converter.setProvider("BC");
                    certificate = converter.getCertificate((X509CertificateHolder) object);
                } else {
                    ServerLogger.errorWithSource("WebAdmin", "webAdmin.sslCertInvalid", sslCertPath);
                    return null;
                }
            }

            // 读取私钥文件
            java.security.PrivateKey privateKey;
            try (PEMParser pemParser = new PEMParser(new FileReader(sslKeyPath))) {
                Object object = pemParser.readObject();
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
                converter.setProvider("BC");

                if (object instanceof PEMKeyPair) {
                    privateKey = converter.getKeyPair((PEMKeyPair) object).getPrivate();
                } else if (object instanceof PrivateKeyInfo) {
                    privateKey = converter.getPrivateKey((PrivateKeyInfo) object);
                } else {
                    ServerLogger.errorWithSource("WebAdmin", "webAdmin.sslKeyInvalid", sslKeyPath);
                    return null;
                }
            }

            // 创建 KeyStore
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setKeyEntry("default", privateKey, new char[0], new Certificate[]{certificate});

            ServerLogger.infoWithSource("WebAdmin", "webAdmin.sslCertLoaded");
            return keyStore;

        } catch (Exception e) {
            ServerLogger.errorWithSource("WebAdmin", "webAdmin.sslCertFailed", e.getMessage());
            return null;
        }
    }

    private void setupRoutes() {
        app.before(this::handleBefore);
        app.get("/", this::serveIndexPage);
        app.get("/check_exists", this::handleCheckExists);
        app.post("/upload", this::handleFileUpload);
        app.get("/download", this::handleFileDownload);
    }

    private void setupWebSocket() {
        app.ws("/ws", wsConfig -> {
            wsConfig.onConnect(this::onWebSocketConnect);
            wsConfig.onMessage(this::onWebSocketMessage);
            wsConfig.onClose(this::onWebSocketClose);
            wsConfig.onError(this::onWebSocketError);
        });
    }

    private void onWebSocketConnect(WsContext ctx) {
        String token = ctx.queryParam("token");
        String remoteIp = getRealRemoteIp(ctx);
        AdminWebSocket socket = new AdminWebSocket(ctx, token, remoteIp);
        ctx.attribute("socket", socket);
    }

    private void onWebSocketMessage(WsMessageContext ctx) {
        AdminWebSocket socket = ctx.attribute("socket");
        if (socket != null) socket.onMessage(ctx);
    }

    private void onWebSocketClose(WsCloseContext ctx) {
        AdminWebSocket socket = ctx.attribute("socket");
        if (socket != null) socket.onClose(ctx);
    }

    private void onWebSocketError(WsContext ctx) {
        AdminWebSocket socket = ctx.attribute("socket");
        if (socket != null) socket.onError(ctx);
    }

    private void handleBefore(Context ctx) {
        ctx.attribute("remoteIp", getRealRemoteIp(ctx));
    }

    private String getRealRemoteIp(Context ctx) {
        String remoteIp = ctx.ip();
        if (remoteIp.equals("127.0.0.1") || remoteIp.equals("0:0:0:0:0:0:0:1")) {
            String realIp = ctx.header("x-neo-real-ip");
            if (realIp != null && !realIp.isEmpty()) return realIp;
        }
        return remoteIp;
    }

    private String getRealRemoteIp(WsContext ctx) {
        String remoteIp = ctx.session.getRemoteAddress().toString();
        if (remoteIp.startsWith("/")) remoteIp = remoteIp.substring(1);
        int portIndex = remoteIp.indexOf(":");
        if (portIndex > 0) remoteIp = remoteIp.substring(0, portIndex);
        if (remoteIp.equals("127.0.0.1") || remoteIp.equals("0:0:0:0:0:0:0:1")) {
            String realIp = ctx.header("x-neo-real-ip");
            if (realIp != null && !realIp.isEmpty()) return realIp;
        }
        return remoteIp;
    }

    private void serveIndexPage(Context ctx) {
        String token = ctx.queryParam("token");
        String remoteIp = ctx.attribute("remoteIp");
        int tokenType = WebAdminManager.verifyTokenAndGetType(token);
        if (tokenType == 0) {
            serveErrorPage(ctx);
            return;
        }
        if (checkConflictWithZombieDetection(tokenType, remoteIp)) {
            serveErrorPage(ctx);
            return;
        }
        String html = loadResourceString("templates/webadmin/index.html");
        if (html == null) {
            ctx.status(500).result("Error: index.html missing");
            return;
        }
        ctx.contentType("text/html; charset=utf-8").result(html);
    }

    private void serveErrorPage(Context ctx) {
        String html = loadResourceString("templates/webadmin/403.html");
        if (html == null) html = loadResourceString("templates/webadmin/index.html");
        if (html == null) {
            ctx.status(403).result("403 Forbidden");
            return;
        }
        ctx.status(403).contentType("text/html; charset=utf-8")
           .header("Cache-Control", "no-cache, no-store, must-revalidate")
           .result(html);
    }

    private void handleCheckExists(Context ctx) {
        String token = ctx.queryParam("token");
        if (WebAdminManager.verifyTokenAndGetType(token) == 0) {
            ctx.status(403).result("Forbidden");
            return;
        }
        String relPath = ctx.queryParam("path");
        String filename = ctx.queryParam("filename");
        if (relPath == null) relPath = "";
        if (filename == null || filename.trim().isEmpty()) {
            ctx.status(400).result("No filename");
            return;
        }
        if (relPath.contains("..") || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            ctx.status(403).result("Invalid Path");
            return;
        }
        File target = new File(new File(NeoProxyServer.CURRENT_DIR_PATH, relPath), filename);
        ctx.result(String.valueOf(target.exists()));
    }

    private void handleFileUpload(Context ctx) {
        String token = ctx.queryParam("token");
        if (WebAdminManager.verifyTokenAndGetType(token) == 0) {
            ctx.status(403).json("{\"status\":\"error\",\"msg\":\"Forbidden\"}");
            return;
        }
        File targetFile = null;
        boolean isUploadSuccessful = false;
        try {
            String filenameEncoded = ctx.queryParam("filename");
            String relPath = ctx.queryParam("path");
            Debugger.debugOperation("WebAdmin Upload Request: " + filenameEncoded + " -> " + relPath);
            if (filenameEncoded == null) {
                ctx.status(400).json("{\"status\":\"error\",\"msg\":\"Missing filename\"}");
                return;
            }
            if (relPath == null) relPath = "";
            if (relPath.contains("..") || filenameEncoded.contains("..") || filenameEncoded.contains("/") || filenameEncoded.contains("\\")) {
                ctx.status(403).json("{\"status\":\"error\",\"msg\":\"Invalid Path\"}");
                return;
            }
            String filename = new File(filenameEncoded).getName();
            if (filename.trim().isEmpty()) {
                ctx.status(400).json("{\"status\":\"error\",\"msg\":\"Empty filename\"}");
                return;
            }
            File targetDir = new File(NeoProxyServer.CURRENT_DIR_PATH, relPath);
            if (!targetDir.exists()) targetDir.mkdirs();
            targetFile = new File(targetDir, filename);
            try (InputStream in = ctx.bodyInputStream(); OutputStream out = new FileOutputStream(targetFile)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
                isUploadSuccessful = true;
                Debugger.debugOperation("WebAdmin Upload Success: " + targetFile.getAbsolutePath());
            }
            broadcastJson("{\"type\":\"action\", \"payload\":\"refresh_files\"}");
            ctx.json("{\"status\":\"success\"}");
        } catch (Exception e) {
            Debugger.debugOperation("WebAdmin Upload Failed: " + e.getMessage());
            if (!isUploadSuccessful && targetFile != null && targetFile.exists()) {
                try { if (!targetFile.delete()) targetFile.deleteOnExit(); } catch (Exception ignored) {}
            }
            ctx.status(500).json("{\"status\":\"error\",\"msg\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleFileDownload(Context ctx) {
        String token = ctx.queryParam("token");
        if (WebAdminManager.verifyTokenAndGetType(token) == 0) {
            ctx.status(403).result("Forbidden");
            return;
        }
        String relPath = ctx.queryParam("file");
        Debugger.debugOperation("WebAdmin Download Request: " + relPath);
        if (relPath == null || relPath.contains("..")) {
            ctx.status(403).result("Invalid File");
            return;
        }
        File f = new File(NeoProxyServer.CURRENT_DIR_PATH, relPath);
        if (!f.exists()) {
            ctx.status(404).result("Not Found");
            return;
        }
        try {
            String encodedFileName = URLEncoder.encode(f.getName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            if (f.isDirectory()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ZipOutputStream zos = new ZipOutputStream(baos)) { zipFile(f, f.getName(), zos); }
                ctx.contentType("application/zip")
                   .header("Content-Disposition", "attachment; filename=\"" + encodedFileName + ".zip\"")
                   .result(baos.toByteArray());
            } else {
                String mime = determineMimeType(f.getName());
                ctx.contentType(mime);
                if (mime.equals("application/octet-stream")) {
                    ctx.header("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
                } else {
                    ctx.header("Content-Disposition", "inline; filename=\"" + encodedFileName + "\"");
                }
                ctx.result(Files.readAllBytes(f.toPath()));
            }
        } catch (Exception e) {
            Debugger.debugOperation("Download Error: " + e.getMessage());
            ctx.status(500).result("Error");
        }
    }

    private String determineMimeType(String name) {
        name = name.toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".cfg") || name.endsWith(".properties"))
            return "text/plain; charset=utf-8";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) return;
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) zipOut.putNextEntry(new ZipEntry(fileName));
            else zipOut.putNextEntry(new ZipEntry(fileName + "/"));
            zipOut.closeEntry();
            File[] children = fileToZip.listFiles();
            if (children != null)
                for (File childFile : children) zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
            return;
        }
        try (FileInputStream fis = new FileInputStream(fileToZip)) {
            ZipEntry zipEntry = new ZipEntry(fileName);
            zipOut.putNextEntry(zipEntry);
            byte[] bytes = new byte[4096];
            int length;
            while ((length = fis.read(bytes)) >= 0) zipOut.write(bytes, 0, length);
        }
    }

    private boolean checkConflictWithZombieDetection(int tokenType, String remoteIp) {
        GLOBAL_LOCK.lock();
        try {
            AdminWebSocket targetSocket = (tokenType == 1) ? activeTempSocket : activePermSocket;
            if (targetSocket == null) return false;
            long lastActive = targetSocket.getLastActiveTime();
            long now = System.currentTimeMillis();
            if ((now - lastActive) > ZOMBIE_TIMEOUT_MS) {
                ServerLogger.warnWithSource("WebAdmin", "webAdmin.clientOffline", targetSocket.getRemoteIp());
                Debugger.debugOperation("WebAdmin: Zombie session detected for " + targetSocket.getRemoteIp());
                final AdminWebSocket socketToClose = targetSocket;
                ThreadManager.runAsync(() -> { try { socketToClose.close(); } catch (Exception ignored) {} });
                if (tokenType == 1) { activeTempSocket = null; activeTempSessionId = null; }
                else { activePermSocket = null; activePermSessionId = null; }
                return false;
            }
            if (!targetSocket.getRemoteIp().equals(remoteIp)) {
                Long lastWarning = lastConflictWarning.get(remoteIp);
                if (lastWarning == null || (now - lastWarning) > 5000) {
                    ServerLogger.warnWithSource("WebAdmin", "webAdmin.conflict", remoteIp);
                    lastConflictWarning.put(remoteIp, now);
                }
                return true;
            }
            return false;
        } finally { GLOBAL_LOCK.unlock(); }
    }

    private String loadResourceString(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { return null; }
    }

    public void stop() {
        if (app != null) { app.stop(); isRunning = false; }
    }

    public int getListeningPort() { return port; }

    public void closeTempConnections() {
        GLOBAL_LOCK.lock();
        try {
            if (activeTempSocket != null) {
                try { activeTempSocket.close(); } catch (Exception ignored) {}
                activeTempSocket = null; activeTempSessionId = null;
            }
        } finally { GLOBAL_LOCK.unlock(); }
    }

    public static void broadcastLog(String message) {
        if (message.contains("____") || message.contains("/  /")) {
            saveAndBroadcast(String.format("{\"type\": \"logo\", \"payload\": \"%s\"}", escapeJson(message)));
            return;
        }
        String finalMsg = message;
        if (!message.startsWith(">") && !message.startsWith("[")) finalMsg = formatLog(message);
        saveAndBroadcast(String.format("{\"type\": \"log\", \"payload\": \"%s\"}", escapeJson(finalMsg)));
    }

    private static void broadcastJson(String json) {
        GLOBAL_LOCK.lock();
        try {
            if (activeTempSocket != null && activeTempSocket.isOpen()) {
                try { activeTempSocket.send(json); } catch (Exception ignored) {}
            }
            if (activePermSocket != null && activePermSocket.isOpen()) {
                try { activePermSocket.send(json); } catch (Exception ignored) {}
            }
        } finally { GLOBAL_LOCK.unlock(); }
    }

    private static void saveAndBroadcast(String json) {
        GLOBAL_LOCK.lock();
        try {
            if (logHistory.size() >= MAX_HISTORY_SIZE) logHistory.removeFirst();
            logHistory.add(json);
            if (activeTempSocket != null && activeTempSocket.isOpen()) {
                try { activeTempSocket.send(json); } catch (Exception ignored) {}
            }
            if (activePermSocket != null && activePermSocket.isOpen()) {
                try { activePermSocket.send(json); } catch (Exception ignored) {}
            }
        } finally { GLOBAL_LOCK.unlock(); }
    }

    private static String formatLog(String msg) {
        return String.format("[%s] [%s]: %s", TIME_FORMATTER.format(LocalDateTime.now()), "WebAdmin", msg);
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\b", "\\b")
                   .replace("\f", "\\f").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private boolean isBinaryFile(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1024];
            int read = in.read(buf);
            if (read == -1) return false;
            for (int i = 0; i < read; i++) if (buf[i] == 0) return true;
            return false;
        } catch (IOException e) { return false; }
    }

    private class AdminWebSocket {
        private final String myId = UUID.randomUUID().toString();
        private final String remoteIp;
        private final WsContext wsContext;
        private int sessionType = 0;
        private volatile long lastActiveTime = System.currentTimeMillis();
        private volatile boolean isConnected = false;

        public AdminWebSocket(WsContext ctx, String token, String remoteIp) {
            this.wsContext = ctx;
            this.remoteIp = remoteIp;
            sessionType = WebAdminManager.verifyTokenAndGetType(token);
            GLOBAL_LOCK.lock();
            try {
                if (sessionType == 1) {
                    if (activeTempSocket != null && activeTempSocket != this) {
                        if (System.currentTimeMillis() - activeTempSocket.getLastActiveTime() > ZOMBIE_TIMEOUT_MS) {
                            AdminWebSocket deadSocket = activeTempSocket;
                            ThreadManager.runAsync(() -> { try { deadSocket.close(); } catch (Exception ignored) {} });
                            activeTempSocket = null;
                        } else {
                            if (!activeTempSocket.getRemoteIp().equals(this.remoteIp))
                                ServerLogger.warnWithSource("WebAdmin", "webAdmin.conflict", remoteIp);
                            close(); return;
                        }
                    }
                    activeTempSocket = this; activeTempSessionId = myId;
                } else if (sessionType == 2) {
                    if (activePermSocket != null && activePermSocket != this) {
                        if (System.currentTimeMillis() - activePermSocket.getLastActiveTime() > ZOMBIE_TIMEOUT_MS) {
                            AdminWebSocket deadSocket = activePermSocket;
                            ThreadManager.runAsync(() -> { try { deadSocket.close(); } catch (Exception ignored) {} });
                            activePermSocket = null;
                        } else {
                            if (!activePermSocket.getRemoteIp().equals(this.remoteIp))
                                ServerLogger.warnWithSource("WebAdmin", "webAdmin.conflict", remoteIp);
                            close(); return;
                        }
                    }
                    activePermSocket = this; activePermSessionId = myId;
                } else { close(); return; }
                lastConflictWarning.remove(remoteIp);
                ServerLogger.infoWithSource("WebAdmin", "webAdmin.session.connected", remoteIp);
                sendJsonRaw("{\"type\": \"logo\", \"payload\": \"" + escapeJson(NeoProxyServer.ASCII_LOGO) + "\"}");
                String msg = ServerLogger.getMessage("webAdmin.connected", remoteIp + (sessionType == 2 ? " (Perm)" : " (Temp)"));
                sendJsonRaw("{\"type\": \"log\", \"payload\": \"" + escapeJson(formatLog(msg)) + "\"}");
                for (String json : logHistory) sendJsonRaw(json);
                isConnected = true;
            } finally { GLOBAL_LOCK.unlock(); }
            lastActiveTime = System.currentTimeMillis();
        }

        public void onMessage(WsMessageContext messageCtx) {
            lastActiveTime = System.currentTimeMillis();
            String text = messageCtx.message().trim();
            if (text.equalsIgnoreCase("PING") || text.equalsIgnoreCase("P")) return;
            if (!text.startsWith("#GET_DASHBOARD") && !text.startsWith("#GET_PERFORMANCE") && !text.startsWith("#GET_PORTS"))
                Debugger.debugOperation("WebAdmin Command: " + text);
            GLOBAL_LOCK.lock();
            try {
                if (sessionType == 1 && !myId.equals(activeTempSessionId)) return;
                if (sessionType == 2 && !myId.equals(activePermSessionId)) return;
            } finally { GLOBAL_LOCK.unlock(); }
            if (text.isEmpty()) return;
            ThreadManager.runAsync(() -> processCommand(text));
        }

        private void processCommand(String text) {
            try {
                if (text.startsWith("#GET_PERFORMANCE")) {
                    sendJsonRaw("{\"type\":\"perf_sys\",\"payload\":" + SystemInfoHelper.getSystemSnapshotJson() + "}");
                    return;
                }
                if (text.startsWith("#GET_PORTS")) {
                    sendJsonRaw("{\"type\":\"perf_ports\",\"payload\":" + SystemInfoHelper.getPortUsageJson() + "}");
                    return;
                }
                if (text.startsWith("#GET_FILES:")) { handleListFiles(text.substring(11)); return; }
                if (text.startsWith("#READ_FILE:")) { handleReadFile(text.substring(11)); return; }
                if (text.startsWith("#SAVE_FILE:")) {
                    int split = text.indexOf('|', 11);
                    if (split > 0) handleSaveFile(text.substring(11, split), text.substring(split + 1));
                    return;
                }
                if (text.startsWith("#DELETE_FILE:")) { handleDeleteFile(text.substring(13)); return; }
                if (text.startsWith("#RENAME_FILE:")) { handleRenameFile(text.substring(13)); return; }
                if (text.startsWith("#CREATE_FILE:")) {
                    int split = text.indexOf('|', 13);
                    if (split > 0) handleCreateFile(text.substring(13, split), text.substring(split + 1), false);
                    return;
                }
                if (text.startsWith("#CREATE_DIR:")) {
                    int split = text.indexOf('|', 12);
                    if (split > 0) handleCreateFile(text.substring(12, split), text.substring(split + 1), true);
                    return;
                }
                if (text.startsWith("#MOVE_FILES:")) { handleMoveFiles(text.substring(12)); return; }
                if (text.startsWith("#GET_DASHBOARD")) { handleGetDashboard(); return; }
                if (text.startsWith("#REFRESH_LOC:")) { handleRefreshLocation(text.substring(13)); return; }
                if (text.startsWith("#REFRESH_BAN_LOC:")) { handleRefreshBanLocation(text.substring(17)); return; }
                String result = NeoProxyServer.myConsole.execute(text);
                if (result != null && !result.isEmpty()) sendJson("cmd_result", result);
            } catch (Exception e) { Debugger.debugOperation(e); }
        }

        private void handleRenameFile(String payload) {
            Debugger.debugOperation("Rename File: " + payload);
            try {
                String[] parts = payload.split("\\|");
                if (parts.length < 3) return;
                String relPath = parts[0]; String oldName = parts[1]; String newName = parts[2];
                if (relPath.contains("..") || oldName.contains("..") || newName.contains("..") || newName.contains("/") || newName.contains("\\")) {
                    sendJson("error", "Invalid filename"); return;
                }
                File dir = new File(NeoProxyServer.CURRENT_DIR_PATH, relPath);
                File src = new File(dir, oldName); File dst = new File(dir, newName);
                if (!src.exists()) { sendJson("error", "Source file not found"); return; }
                if (dst.exists()) { sendJson("error", "Destination already exists"); return; }
                Files.move(src.toPath(), dst.toPath());
                sendJson("action", "refresh_files");
            } catch (Exception e) { sendJson("error", "Rename failed: " + e.getMessage()); }
        }

        private void handleMoveFiles(String payload) {
            try {
                String[] parts = payload.split("\\|");
                if (parts.length < 2) return;
                String targetRelPath = parts[0];
                Debugger.debugOperation("Move Files to: " + targetRelPath);
                if (targetRelPath.contains("..")) throw new SecurityException("Invalid target path");
                File targetDir = new File(NeoProxyServer.CURRENT_DIR_PATH, targetRelPath);
                if (!targetDir.exists()) targetDir.mkdirs();
                int success = 0, fail = 0;
                for (int i = 1; i < parts.length; i++) {
                    String sourceRelPath = parts[i];
                    if (sourceRelPath.contains("..")) { fail++; continue; }
                    File sourceFile = new File(NeoProxyServer.CURRENT_DIR_PATH, sourceRelPath);
                    if (!sourceFile.exists()) { fail++; continue; }
                    File destFile = new File(targetDir, sourceFile.getName());
                    if (destFile.exists()) {
                        String name = destFile.getName(); int dot = name.lastIndexOf('.');
                        String newName = (dot > 0) ? name.substring(0, dot) + "_moved_" + System.currentTimeMillis() + name.substring(dot)
                                                   : name + "_moved_" + System.currentTimeMillis();
                        destFile = new File(targetDir, newName);
                    }
                    try { Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING); success++; }
                    catch (Exception e) { fail++; }
                }
                sendJson("action", "refresh_files");
                sendJson("toast", "Moved: " + success + ", Failed: " + fail);
            } catch (Exception e) { sendJson("error", "Move failed: " + e.getMessage()); }
        }

        private void handleListFiles(String relPath) {
            try {
                if (relPath.contains("..")) throw new SecurityException("Access Denied");
                File dir = new File(NeoProxyServer.CURRENT_DIR_PATH, relPath);
                if (!dir.exists()) { sendJson("error", "Directory not found"); return; }
                File[] files = dir.listFiles(); if (files == null) files = new File[0];
                StringBuilder sb = new StringBuilder("["); boolean needComma = false;
                if (!relPath.isEmpty()) { sb.append("{\"name\":\"..\",\"isDir\":true,\"size\":0,\"time\":0}"); needComma = true; }
                for (File f : files) {
                    if (needComma) sb.append(",");
                    sb.append(String.format("{\"name\":\"%s\",\"isDir\":%b,\"size\":%d,\"time\":%d}",
                            escapeJson(f.getName()), f.isDirectory(), f.length(), f.lastModified()));
                    needComma = true;
                }
                sb.append("]");
                sendJsonRaw("{\"type\":\"file_list\",\"path\":\"" + escapeJson(relPath) + "\",\"payload\":" + sb.toString() + "}");
            } catch (Exception e) { sendJson("error", e.getMessage()); }
        }

        private void handleReadFile(String relPath) {
            try {
                if (relPath.contains("..")) throw new SecurityException("Access Denied");
                File f = new File(NeoProxyServer.CURRENT_DIR_PATH, relPath);
                if (!f.exists() || f.isDirectory()) { sendJson("error", "Invalid file"); return; }
                if (f.length() > 1024 * 1024 || isBinaryFile(f)) {
                    sendJsonRaw("{\"type\":\"file_too_large\",\"path\":\"" + escapeJson(relPath) + "\"}"); return;
                }
                String content;
                try { content = Files.readString(f.toPath(), StandardCharsets.UTF_8); }
                catch (IOException e) {
                    sendJsonRaw("{\"type\":\"file_too_large\",\"path\":\"" + escapeJson(relPath) + "\"}"); return;
                }
                sendJsonRaw("{\"type\":\"file_content\",\"path\":\"" + escapeJson(relPath) + "\",\"payload\":\"" + escapeJson(content) + "\"}");
            } catch (Exception e) { sendJson("error", "Read failed: " + e.getMessage()); }
        }

        private void handleSaveFile(String relPath, String content) {
            try {
                Debugger.debugOperation("Save File: " + relPath);
                if (relPath.contains("..")) throw new SecurityException("Access Denied");
                File f = new File(NeoProxyServer.CURRENT_DIR_PATH, relPath);
                Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
                sendJson("toast", "File saved successfully.");
            } catch (Exception e) { sendJson("error", "Save failed: " + e.getMessage()); }
        }

        private void handleDeleteFile(String relPath) {
            try {
                Debugger.debugOperation("Delete File: " + relPath);
                if (relPath.contains("..")) throw new SecurityException("Access Denied");
                File f = new File(NeoProxyServer.CURRENT_DIR_PATH, relPath);
                if (deleteRecursively(f)) { sendJson("action", "refresh_files"); sendJson("toast", "Deleted: " + f.getName()); }
                else sendJson("error", "Delete failed");
            } catch (Exception e) { sendJson("error", e.getMessage()); }
        }

        private boolean deleteRecursively(File f) {
            if (f.isDirectory()) { File[] c = f.listFiles(); if (c != null) for (File child : c) deleteRecursively(child); }
            return f.delete();
        }

        private void handleCreateFile(String relPath, String name, boolean isDir) {
            try {
                Debugger.debugOperation("Create " + (isDir ? "Dir" : "File") + ": " + name + " in " + relPath);
                if (relPath.contains("..") || name.contains("..") || name.contains("/") || name.contains("\\"))
                    throw new SecurityException("Invalid name");
                File f = new File(new File(NeoProxyServer.CURRENT_DIR_PATH, relPath), name);
                if (f.exists()) { sendJson("error", "Exists already"); return; }
                if (isDir) f.mkdirs(); else f.createNewFile();
                sendJson("action", "refresh_files"); sendJson("toast", "Created: " + name);
            } catch (Exception e) { sendJson("error", e.getMessage()); }
        }

        private void handleGetDashboard() {
            int hostClientCount = NeoProxyServer.availableHostClient.size();
            int tcpClientCount = 0;
            double totalBalance = 0;
            for (HostClient hc : NeoProxyServer.availableHostClient) {
                tcpClientCount += hc.getActiveTcpSockets().size();
                if (hc.getKey() != null) totalBalance += hc.getKey().getBalance();
            }
            int udpClientCount = UDPTransformer.udpClientConnections.size();
            long now = System.currentTimeMillis();
            long currentTotalBytes = NeoProxyServer.TOTAL_BYTES_COUNTER.sum();
            long timeDiff = now - lastCalcTime;
            if (timeDiff >= 500) {
                currentSpeedBps = (double) (currentTotalBytes - lastTotalBytes) / (timeDiff / 1000.0);
                lastTotalBytes = currentTotalBytes;
                lastCalcTime = now;
            }
            String json = String.format(Locale.US,
                    "{\"hc\":%d, \"ec\":%d, \"uc\":%d, \"tb\":%.2f, \"gs\":%.2f, \"v\":\"%s\", \"sv\":\"%s\", \"p\":\"%s\"}",
                    hostClientCount, tcpClientCount, udpClientCount, totalBalance, currentSpeedBps,
                    NeoProxyServer.VERSION, NeoProxyServer.EXPECTED_CLIENT_VERSION,
                    NeoProxyServer.HOST_HOOK_PORT + " / " + NeoProxyServer.HOST_CONNECT_PORT);
            sendJson("dashboard_data", json);
        }

        private void handleRefreshLocation(String ip) {
            ServerLogger.infoWithSource("WebAdmin", "webAdmin.refreshingLoc", remoteIp);
            HostClient target = null;
            for (HostClient hc : NeoProxyServer.availableHostClient) {
                if (hc.getHostServerHook().getInetAddress().getHostAddress().equals(ip)) { target = hc; break; }
            }
            if (target != null) {
                IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.getLocationInfo(ip);
                target.setCachedLocation(info.location());
                target.setCachedISP(info.isp());
                sendJson("action", "refresh_clients");
            } else {
                String failMsg = ServerLogger.getMessage("webAdmin.clientOffline", ip);
                sendJson("log", formatLog(failMsg));
            }
        }

        private void handleRefreshBanLocation(String ip) {
            IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.getLocationInfo(ip);
            String payload = String.format("{\"ip\":\"%s\",\"loc\":\"%s\",\"isp\":\"%s\"}", ip, escapeJson(info.location()), escapeJson(info.isp()));
            sendJsonRaw(String.format("{\"type\": \"ban_loc_result\", \"payload\": %s}", payload));
        }

        public void sendJson(String type, String payload) {
            String json = String.format("{\"type\": \"%s\", \"payload\": \"%s\"}", type, escapeJson(payload));
            try { send(json); } catch (Exception ignored) {}
        }

        public void sendJsonRaw(String json) {
            try { send(json); } catch (Exception ignored) {}
        }

        public void onClose(WsCloseContext ctx) {
            Debugger.debugOperation("WebAdmin WebSocket Close: " + remoteIp + " Reason: " + ctx.reason());
            isConnected = false;
            GLOBAL_LOCK.lock();
            try {
                if (sessionType == 1 && myId.equals(activeTempSessionId)) {
                    activeTempSocket = null; activeTempSessionId = null;
                    ServerLogger.infoWithSource("WebAdmin", "webAdmin.session.disconnected");
                } else if (sessionType == 2 && myId.equals(activePermSessionId)) {
                    activePermSocket = null; activePermSessionId = null;
                    ServerLogger.infoWithSource("WebAdmin", "webAdmin.session.disconnected");
                }
            } finally { GLOBAL_LOCK.unlock(); }
        }

        public void onError(WsContext ctx) {
            if (NeoProxyServer.IS_DEBUG_MODE) ServerLogger.errorWithSource("WebAdmin", "webAdmin.conflict", "WebSocket error occurred");
        }

        public void send(String message) { wsContext.send(message); }
        public void close() { wsContext.closeSession(1000, "Closed"); }
        public boolean isOpen() { return wsContext.session.isOpen(); }
        public String getRemoteIp() { return remoteIp; }
        public long getLastActiveTime() { return lastActiveTime; }
    }
}