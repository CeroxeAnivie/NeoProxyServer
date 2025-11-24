package neoproxy.neoproxyserver.core.webadmin;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import fi.iki.elonen.NanoWSD;
import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.management.IPGeolocationHelper;
import plethora.thread.ThreadManager;

import java.io.*;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static neoproxy.neoproxyserver.NeoProxyServer.debugOperation;

public class WebAdminServer extends NanoWSD {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> logHistory = Collections.synchronizedList(new LinkedList<>());
    private static final int MAX_HISTORY_SIZE = 1000;
    private static final Object LOCK = new Object();

    // 僵尸连接判定阈值 (毫秒)
    // 前端每 5000ms 发送一次 PING，如果 10000ms (两个周期) 没收到，说明连接已断开
    private static final long ZOMBIE_TIMEOUT_MS = 10000;
    private static final Map<String, Long> lastConflictWarning = new HashMap<>();
    private static volatile AdminWebSocket activeTempSocket = null;
    private static volatile String activeTempSessionId = null;
    private static volatile AdminWebSocket activePermSocket = null;
    private static volatile String activePermSessionId = null;

    static {
        // 屏蔽 NanoHTTPD 的 Socket closed / Broken pipe 报错
        try {
            Logger nanoLogger = Logger.getLogger("fi.iki.elonen.NanoHTTPD");
            nanoLogger.setFilter(new Filter() {
                @Override
                public boolean isLoggable(LogRecord record) {
                    Throwable t = record.getThrown();
                    if (t instanceof SocketException) return false;
                    String msg = record.getMessage();
                    return msg == null || (!msg.contains("Socket closed") && !msg.contains("Broken pipe"));
                }
            });
        } catch (Exception ignored) {
        }
    }

    public WebAdminServer(int port) {
        super(port);
        setAsyncRunner(new AsyncRunner() {
            @Override
            public void closeAll() {
            }

            @Override
            public void closed(ClientHandler clientHandler) {
            }

            @Override
            public void exec(ClientHandler code) {
                ThreadManager.runAsync(code);
            }
        });
    }

    public static void broadcastLog(String message) {
        if (message.contains("____") || message.contains("/  /")) {
            String safeLog = escapeJson(message);
            saveAndBroadcast(String.format("{\"type\": \"logo\", \"payload\": \"%s\"}", safeLog));
            return;
        }
        String finalMsg = message;
        if (!message.startsWith(">") && !message.startsWith("[")) finalMsg = formatLog(message);
        String safeLog = escapeJson(finalMsg);
        saveAndBroadcast(String.format("{\"type\": \"log\", \"payload\": \"%s\"}", safeLog));
    }

    private static void broadcastJson(String json) {
        synchronized (LOCK) {
            if (activeTempSocket != null && activeTempSocket.isOpen()) {
                try {
                    activeTempSocket.send(json);
                } catch (IOException ignored) {
                }
            }
            if (activePermSocket != null && activePermSocket.isOpen()) {
                try {
                    activePermSocket.send(json);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void saveAndBroadcast(String json) {
        synchronized (logHistory) {
            if (logHistory.size() >= MAX_HISTORY_SIZE) logHistory.removeFirst();
            logHistory.add(json);
        }
        broadcastJson(json);
    }

    private static String formatLog(String msg) {
        return String.format("[%s] [%s]: %s", TIME_FORMATTER.format(LocalDateTime.now()), "WebAdmin", msg);
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\b", "\\b").replace("\f", "\\f").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    @Override
    public Response serve(IHTTPSession session) {
        String token = session.getParms().get("token");
        String remoteIp = session.getRemoteIpAddress();

        int tokenType = WebAdminManager.verifyTokenAndGetType(token);
        if (tokenType == 0) return serveErrorPage();

        String uri = session.getUri();
        Method method = session.getMethod();

        if (Method.GET.equals(method) && "/check_exists".equals(uri)) return handleCheckExists(session);
        if (Method.POST.equals(method) && "/upload".equals(uri)) return handleFileUpload(session);
        if (Method.GET.equals(method) && "/download".equals(uri)) return handleFileDownload(session);

        // 如果不是 WebSocket 请求，说明是页面加载/刷新
        // 此时检查是否存在冲突，如果存在冲突，则进一步检查是否为僵尸连接
        if (!isWebsocketRequested(session)) {
            // 如果检测到冲突（且不是僵尸），则拦截返回 403
            if (checkConflictWithZombieDetection(tokenType, remoteIp)) return serveErrorPage();
        }

        if (isWebsocketRequested(session)) return super.serve(session);

        String html = loadResourceString("templates/webadmin/index.html");
        if (html == null)
            return newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error: index.html missing");
        return newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_HTML, html);
    }

    private Response handleCheckExists(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        String relPath = params.get("path");
        String filename = params.get("filename");
        if (relPath == null) relPath = "";
        if (filename == null || filename.trim().isEmpty())
            return newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "No filename");
        if (relPath.contains("..") || filename.contains("..") || filename.contains("/") || filename.contains("\\"))
            return newFixedLengthResponse(Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Invalid Path");
        File target = new File(new File(NeoProxyServer.CURRENT_DIR_PATH, relPath), filename);
        return newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_PLAINTEXT, String.valueOf(target.exists()));
    }

    private Response handleFileUpload(IHTTPSession session) {
        File targetFile = null;
        boolean isUploadSuccessful = false;

        try {
            Map<String, String> params = session.getParms();
            String filenameEncoded = params.get("filename");
            String relPath = params.get("path");

            if (filenameEncoded == null)
                return newFixedLengthResponse(Status.BAD_REQUEST, "application/json", "{\"status\":\"error\",\"msg\":\"Missing filename\"}");
            if (relPath == null) relPath = "";
            if (relPath.contains("..") || filenameEncoded.contains("..") || filenameEncoded.contains("/") || filenameEncoded.contains("\\"))
                return newFixedLengthResponse(Status.FORBIDDEN, "application/json", "{\"status\":\"error\",\"msg\":\"Invalid Path\"}");

            String filename = new File(filenameEncoded).getName();
            if (filename.trim().isEmpty())
                return newFixedLengthResponse(Status.BAD_REQUEST, "application/json", "{\"status\":\"error\",\"msg\":\"Empty filename\"}");

            File targetDir = new File(NeoProxyServer.CURRENT_DIR_PATH, relPath);
            if (!targetDir.exists()) targetDir.mkdirs();

            targetFile = new File(targetDir, filename);

            long expectedSize = -1;
            if (session.getHeaders().containsKey("content-length")) {
                try {
                    expectedSize = Long.parseLong(session.getHeaders().get("content-length"));
                } catch (NumberFormatException ignored) {
                }
            }

            InputStream in = session.getInputStream();

            try (OutputStream out = new FileOutputStream(targetFile)) {
                if (expectedSize >= 0) {
                    byte[] buf = new byte[8192];
                    long remaining = expectedSize;
                    while (remaining > 0) {
                        int read = in.read(buf, 0, (int) Math.min(remaining, buf.length));
                        if (read == -1) {
                            throw new IOException("Premature End of Stream: Upload Cancelled");
                        }
                        out.write(buf, 0, read);
                        remaining -= read;
                    }
                } else {
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        out.write(buf, 0, read);
                    }
                }
                isUploadSuccessful = true;
            }

            broadcastJson("{\"type\":\"action\", \"payload\":\"refresh_files\"}");
            return newFixedLengthResponse(Status.OK, "application/json", "{\"status\":\"success\"}");

        } catch (Exception e) {
            if (!isUploadSuccessful && targetFile != null && targetFile.exists()) {
                try {
                    boolean deleted = targetFile.delete();
                    if (deleted) {
                        ServerLogger.infoWithSource("WebAdmin", "webAdmin.uploadCancelled", targetFile.getName());
                    } else {
                        targetFile.deleteOnExit();
                        ServerLogger.warnWithSource("WebAdmin", "Failed to delete zombie file immediately: " + targetFile.getName());
                    }
                } catch (Exception deleteEx) {
                }
            }

            if (!(e instanceof SocketException) && !(e instanceof IOException && e.getMessage().contains("Cancel"))) {
                if (NeoProxyServer.IS_DEBUG_MODE) e.printStackTrace();
                ServerLogger.errorWithSource("WebAdmin", "Upload Logic Error: " + e.getMessage());
            }
            return newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json", "{\"status\":\"error\",\"msg\":\"" + e.getMessage() + "\"}");
        }
    }

    private Response handleFileDownload(IHTTPSession session) {
        String relPath = session.getParms().get("file");
        if (relPath == null || relPath.contains(".."))
            return newFixedLengthResponse(Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Invalid File");
        File f = new File(NeoProxyServer.CURRENT_DIR_PATH, relPath);
        if (!f.exists()) return newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
        try {
            if (f.isDirectory()) {
                PipedInputStream in = new PipedInputStream();
                PipedOutputStream out = new PipedOutputStream(in);
                ThreadManager.runAsync(() -> {
                    try (ZipOutputStream zos = new ZipOutputStream(out)) {
                        zipFile(f, f.getName(), zos);
                    } catch (Exception e) {
                        ServerLogger.errorWithSource("WebAdmin", "Zip Error", e);
                    } finally {
                        try {
                            out.close();
                        } catch (IOException e) {
                        }
                    }
                });
                Response res = newFixedLengthResponse(Status.OK, "application/zip", in, -1);
                res.addHeader("Content-Disposition", "attachment; filename=\"" + f.getName() + ".zip\"");
                return res;
            } else {
                String mime = "application/octet-stream";
                String name = f.getName().toLowerCase();
                if (name.endsWith(".html")) mime = "text/html";
                else if (name.endsWith(".css")) mime = "text/css";
                else if (name.endsWith(".js")) mime = "text/javascript";
                else if (name.endsWith(".json")) mime = "application/json";
                else if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".cfg")) mime = "text/plain";
                else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) mime = "image/jpeg";
                else if (name.endsWith(".png")) mime = "image/png";
                else if (name.endsWith(".gif")) mime = "image/gif";
                else if (name.endsWith(".svg")) mime = "image/svg+xml";
                else if (name.endsWith(".webp")) mime = "image/webp";
                else if (name.endsWith(".ico")) mime = "image/x-icon";
                else if (name.endsWith(".bmp")) mime = "image/bmp";
                InputStream fis = new FileInputStream(f);
                Response res = newFixedLengthResponse(Status.OK, mime, fis, f.length());
                if (mime.equals("application/octet-stream"))
                    res.addHeader("Content-Disposition", "attachment; filename=\"" + f.getName() + "\"");
                else res.addHeader("Content-Disposition", "inline; filename=\"" + f.getName() + "\"");
                return res;
            }
        } catch (Exception e) {
            return newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error");
        }
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

    /**
     * 【修复版】检查冲突并检测僵尸连接
     * 优化：移除同步 Ping 操作，将 Socket 关闭操作移至异步线程，防止 TCP 超时阻塞主线程
     */
    private boolean checkConflictWithZombieDetection(int tokenType, String remoteIp) {
        synchronized (LOCK) {
            AdminWebSocket targetSocket = (tokenType == 1) ? activeTempSocket : activePermSocket;

            // 1. 如果没有连接，直接通过
            if (targetSocket == null) return false;

            // 2. 如果有连接，开始判断是否为僵尸
            long lastActive = targetSocket.getLastActiveTime();
            long now = System.currentTimeMillis();
            // 只要超时，直接判定为僵尸，不再尝试 Ping (因为 Ping 会导致几十秒的阻塞)
            boolean isZombie = (now - lastActive) > ZOMBIE_TIMEOUT_MS;

            if (isZombie) {
                ServerLogger.warnWithSource("WebAdmin", "webAdmin.clientOffline", targetSocket.getRemoteIp());

                // 【核心修改点】：将关闭操作移入异步线程
                // 为什么要这样做？因为向死掉的 TCP 连接发送 Close 帧会阻塞几十秒等待 ACK
                final AdminWebSocket socketToClose = targetSocket;
                ThreadManager.runAsync(() -> {
                    try {
                        // 强制断开，不等待
                        socketToClose.close(WebSocketFrame.CloseCode.GoingAway, "Zombie Timeout", false);
                    } catch (Exception ignored) {
                    }
                });

                // 立即清理引用，让当前新请求马上通过
                if (tokenType == 1) {
                    activeTempSocket = null;
                    activeTempSessionId = null;
                } else {
                    activePermSocket = null;
                    activePermSessionId = null;
                }
                // 返回 false，表示冲突已解除，允许访问
                return false;
            }

            // 3. 如果连接活着（在有效期内）
            // 无论 IP 是否相同，只要已存在活跃连接，就拒绝新连接（严格单例）
            if (!targetSocket.getRemoteIp().equals(remoteIp)) {
                logConflictWarning(remoteIp, "http");
            }
            return true; // 冲突存在，拦截
        }
    }

    private void logConflictWarning(String remoteIp, String source) {
        long now = System.currentTimeMillis();
        Long lastWarning = lastConflictWarning.get(remoteIp);
        if (lastWarning == null || (now - lastWarning) > 5000) {
            ServerLogger.warnWithSource("WebAdmin", "webAdmin.conflict", remoteIp);
            lastConflictWarning.put(remoteIp, now);
        }
    }

    private Response serveErrorPage() {
        String html = loadResourceString("templates/webadmin/403.html");
        if (html == null) html = loadResourceString("templates/webadmin/index.html");
        if (html == null) return newFixedLengthResponse(Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "403 Forbidden");
        Response response = newFixedLengthResponse(Status.FORBIDDEN, NanoHTTPD.MIME_HTML, html);
        response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        return response;
    }

    private String loadResourceString(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        return new AdminWebSocket(handshake);
    }

    public void closeTempConnections() {
        synchronized (LOCK) {
            if (activeTempSocket != null) {
                try {
                    activeTempSocket.close(WebSocketFrame.CloseCode.NormalClosure, "Token Reset", false);
                } catch (Exception ignored) {
                }
                activeTempSocket = null;
                activeTempSessionId = null;
            }
        }
    }

    private class AdminWebSocket extends WebSocket {
        private final String myId = UUID.randomUUID().toString();
        private final String remoteIp;
        private int sessionType = 0;
        // 记录最后一次收到消息（心跳）的时间
        private volatile long lastActiveTime = System.currentTimeMillis();

        public AdminWebSocket(IHTTPSession handshakeRequest) {
            super(handshakeRequest);
            this.remoteIp = handshakeRequest.getRemoteIpAddress();
        }

        public String getRemoteIp() {
            return remoteIp;
        }

        public long getLastActiveTime() {
            return lastActiveTime;
        }

        @Override
        protected void onOpen() {
            String token = this.getHandshakeRequest().getParms().get("token");
            this.sessionType = WebAdminManager.verifyTokenAndGetType(token);
            synchronized (LOCK) {
                // WebSocket 握手阶段的检查，同样应用异步清理优化
                if (sessionType == 1) {
                    if (activeTempSocket != null && activeTempSocket != this) {
                        // 再次检查僵尸（防止 HTTP 检查漏网）
                        if (System.currentTimeMillis() - activeTempSocket.getLastActiveTime() > ZOMBIE_TIMEOUT_MS) {
                            AdminWebSocket deadSocket = activeTempSocket;
                            // 异步清理
                            ThreadManager.runAsync(() -> {
                                try { deadSocket.close(WebSocketFrame.CloseCode.GoingAway, "Zombie", false); } catch (Exception ignored) {}
                            });
                            activeTempSocket = null; // 立即踢掉僵尸
                        } else {
                            if (!activeTempSocket.getRemoteIp().equals(this.remoteIp))
                                logConflictWarning(remoteIp, "ws");
                            closeSocket("Conflict");
                            return; // 严格拒绝
                        }
                    }
                    activeTempSocket = this;
                    activeTempSessionId = myId;
                } else if (sessionType == 2) {
                    if (activePermSocket != null && activePermSocket != this) {
                        // 再次检查僵尸
                        if (System.currentTimeMillis() - activePermSocket.getLastActiveTime() > ZOMBIE_TIMEOUT_MS) {
                            AdminWebSocket deadSocket = activePermSocket;
                            // 异步清理
                            ThreadManager.runAsync(() -> {
                                try { deadSocket.close(WebSocketFrame.CloseCode.GoingAway, "Zombie", false); } catch (Exception ignored) {}
                            });
                            activePermSocket = null; // 立即踢掉僵尸
                        } else {
                            if (!activePermSocket.getRemoteIp().equals(this.remoteIp))
                                logConflictWarning(remoteIp, "ws");
                            closeSocket("Conflict");
                            return; // 严格拒绝
                        }
                    }
                    activePermSocket = this;
                    activePermSessionId = myId;
                } else {
                    closeSocket("Auth Failed");
                    return;
                }
                lastConflictWarning.remove(remoteIp);
            }
            ServerLogger.infoWithSource("WebAdmin", "webAdmin.session.connected", remoteIp);
            sendJson("logo", NeoProxyServer.ASCII_LOGO);
            String msg = ServerLogger.getMessage("webAdmin.connected", remoteIp + (sessionType == 2 ? " (Perm)" : " (Temp)"));
            sendJson("log", formatLog(msg));
            synchronized (logHistory) {
                for (String json : logHistory) sendJsonRaw(json);
            }

            // 立即更新活跃时间
            lastActiveTime = System.currentTimeMillis();
        }

        private void closeSocket(String reason) {
            try {
                this.close(WebSocketFrame.CloseCode.PolicyViolation, reason, false);
            } catch (Exception ignored) {
            }
        }

        @Override
        protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
            synchronized (LOCK) {
                if (sessionType == 1 && myId.equals(activeTempSessionId)) {
                    activeTempSocket = null;
                    activeTempSessionId = null;
                    ServerLogger.infoWithSource("WebAdmin", "webAdmin.session.disconnected");
                } else if (sessionType == 2 && myId.equals(activePermSessionId)) {
                    activePermSocket = null;
                    activePermSessionId = null;
                    ServerLogger.infoWithSource("WebAdmin", "webAdmin.session.disconnected");
                }
            }
        }

        @Override
        protected void onPong(WebSocketFrame pong) {
            // 收到 Pong 更新活跃时间
            lastActiveTime = System.currentTimeMillis();
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            // 收到任何消息（包括 PING）都更新活跃时间
            lastActiveTime = System.currentTimeMillis();

            String text = message.getTextPayload().trim();
            if (text.equalsIgnoreCase("PING") || text.equalsIgnoreCase("P")) return;

            // 校验 Session ID 安全性
            synchronized (LOCK) {
                if (sessionType == 1 && !myId.equals(activeTempSessionId)) return;
                if (sessionType == 2 && !myId.equals(activePermSessionId)) return;
            }
            if (text.isEmpty()) return;

            ThreadManager.runAsync(() -> {
                try {
                    if (text.startsWith("#GET_PERFORMANCE")) {
                        sendJsonRaw("{\"type\":\"perf_sys\",\"payload\":" + SystemInfoHelper.getSystemSnapshotJson() + "}");
                        return;
                    }
                    if (text.startsWith("#GET_PORTS")) {
                        sendJsonRaw("{\"type\":\"perf_ports\",\"payload\":" + SystemInfoHelper.getPortUsageJson() + "}");
                        return;
                    }
                    if (text.startsWith("#GET_FILES:")) {
                        handleListFiles(text.substring(11));
                        return;
                    }
                    if (text.startsWith("#READ_FILE:")) {
                        handleReadFile(text.substring(11));
                        return;
                    }
                    if (text.startsWith("#SAVE_FILE:")) {
                        int split = text.indexOf('|', 11);
                        if (split > 0) handleSaveFile(text.substring(11, split), text.substring(split + 1));
                        return;
                    }
                    if (text.startsWith("#DELETE_FILE:")) {
                        handleDeleteFile(text.substring(13));
                        return;
                    }
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

                    if (text.startsWith("#MOVE_FILES:")) {
                        handleMoveFiles(text.substring(12));
                        return;
                    }

                    if (text.startsWith("#GET_DASHBOARD")) {
                        handleGetDashboard();
                        return;
                    }
                    if (text.startsWith("#REFRESH_LOC:")) {
                        handleRefreshLocation(text.substring(13));
                        return;
                    }
                    if (text.startsWith("#REFRESH_BAN_LOC:")) {
                        handleRefreshBanLocation(text.substring(17));
                        return;
                    }

                    String result = NeoProxyServer.myConsole.execute(text);
                    if (result != null && !result.isEmpty()) {
                        sendJson("cmd_result", result);
                    }
                } catch (Exception e) {
                    if (NeoProxyServer.IS_DEBUG_MODE) {
                        debugOperation(e);
                    }
                }
            });
        }

        private void handleMoveFiles(String payload) {
            try {
                String[] parts = payload.split("\\|");
                if (parts.length < 2) return;
                String targetRelPath = parts[0];
                if (targetRelPath.contains("..")) throw new SecurityException("Invalid target path");
                File targetDir = new File(NeoProxyServer.CURRENT_DIR_PATH, targetRelPath);
                if (!targetDir.exists()) targetDir.mkdirs();
                int success = 0;
                int fail = 0;
                for (int i = 1; i < parts.length; i++) {
                    String sourceRelPath = parts[i];
                    if (sourceRelPath.contains("..")) {
                        fail++;
                        continue;
                    }
                    File sourceFile = new File(NeoProxyServer.CURRENT_DIR_PATH, sourceRelPath);
                    if (!sourceFile.exists()) {
                        fail++;
                        continue;
                    }
                    File destFile = new File(targetDir, sourceFile.getName());
                    if (destFile.exists()) {
                        String name = destFile.getName();
                        int dot = name.lastIndexOf('.');
                        String newName;
                        if (dot > 0)
                            newName = name.substring(0, dot) + "_moved_" + System.currentTimeMillis() + name.substring(dot);
                        else newName = name + "_moved_" + System.currentTimeMillis();
                        destFile = new File(targetDir, newName);
                    }
                    try {
                        Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        success++;
                    } catch (Exception e) {
                        fail++;
                    }
                }
                sendJson("action", "refresh_files");
                sendJson("toast", "Moved: " + success + ", Failed: " + fail);
            } catch (Exception e) {
                sendJson("error", "Move failed: " + e.getMessage());
            }
        }

        private void handleListFiles(String relPath) {
            try {
                if (relPath.contains("..")) throw new SecurityException("Access Denied");
                File dir = new File(NeoProxyServer.CURRENT_DIR_PATH, relPath);
                if (!dir.exists()) {
                    sendJson("error", "Directory not found");
                    return;
                }
                File[] files = dir.listFiles();
                if (files == null) files = new File[0];
                StringBuilder sb = new StringBuilder("[");
                if (!relPath.isEmpty()) sb.append("{\"name\":\"..\",\"isDir\":true,\"size\":0},");
                for (int i = 0; i < files.length; i++) {
                    File f = files[i];
                    sb.append(String.format("{\"name\":\"%s\",\"isDir\":%b,\"size\":%d}", escapeJson(f.getName()), f.isDirectory(), f.length()));
                    if (i < files.length - 1) sb.append(",");
                }
                sb.append("]");
                sendJsonRaw("{\"type\":\"file_list\",\"path\":\"" + escapeJson(relPath) + "\",\"payload\":" + sb.toString() + "}");
            } catch (Exception e) {
                sendJson("error", e.getMessage());
            }
        }

        private void handleReadFile(String relPath) {
            try {
                if (relPath.contains("..")) throw new SecurityException("Access Denied");
                File f = new File(NeoProxyServer.CURRENT_DIR_PATH, relPath);
                if (!f.exists() || f.isDirectory()) {
                    sendJson("error", "Invalid file");
                    return;
                }
                if (f.length() > 512 * 1024) {
                    sendJsonRaw("{\"type\":\"file_too_large\",\"path\":\"" + escapeJson(relPath) + "\"}");
                    return;
                }
                String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                sendJsonRaw("{\"type\":\"file_content\",\"path\":\"" + escapeJson(relPath) + "\",\"payload\":\"" + escapeJson(content) + "\"}");
            } catch (Exception e) {
                sendJson("error", "Read failed: " + e.getMessage());
            }
        }

        private void handleSaveFile(String relPath, String content) {
            try {
                if (relPath.contains("..")) throw new SecurityException("Access Denied");
                File f = new File(NeoProxyServer.CURRENT_DIR_PATH, relPath);
                Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
                sendJson("toast", "File saved successfully.");
            } catch (Exception e) {
                sendJson("error", "Save failed: " + e.getMessage());
            }
        }

        private void handleDeleteFile(String relPath) {
            try {
                if (relPath.contains("..")) throw new SecurityException("Access Denied");
                File f = new File(NeoProxyServer.CURRENT_DIR_PATH, relPath);
                if (deleteRecursively(f)) {
                    sendJson("action", "refresh_files");
                    sendJson("toast", "Deleted: " + f.getName());
                } else {
                    sendJson("error", "Delete failed");
                }
            } catch (Exception e) {
                sendJson("error", e.getMessage());
            }
        }

        private boolean deleteRecursively(File f) {
            if (f.isDirectory()) {
                File[] c = f.listFiles();
                if (c != null) for (File child : c) deleteRecursively(child);
            }
            return f.delete();
        }

        private void handleCreateFile(String relPath, String name, boolean isDir) {
            try {
                if (relPath.contains("..") || name.contains("..") || name.contains("/") || name.contains("\\"))
                    throw new SecurityException("Invalid name");
                File f = new File(new File(NeoProxyServer.CURRENT_DIR_PATH, relPath), name);
                if (f.exists()) {
                    sendJson("error", "Exists already");
                    return;
                }
                if (isDir) f.mkdirs();
                else f.createNewFile();
                sendJson("action", "refresh_files");
                sendJson("toast", "Created: " + name);
            } catch (Exception e) {
                sendJson("error", e.getMessage());
            }
        }

        private void handleGetDashboard() {
            int hostClientCount = NeoProxyServer.availableHostClient.size();
            int extClientCount = 0;
            double totalBalance = 0;
            for (HostClient hc : NeoProxyServer.availableHostClient) {
                extClientCount += hc.getActiveTcpSockets().size();
                if (hc.getKey() != null) totalBalance += hc.getKey().getBalance();
            }
            String json = String.format(Locale.US, "{\"hc\":%d, \"ec\":%d, \"tb\":%.2f, \"v\":\"%s\", \"sv\":\"%s\", \"p\":\"%s\"}", hostClientCount, extClientCount, totalBalance, NeoProxyServer.VERSION, NeoProxyServer.EXPECTED_CLIENT_VERSION, NeoProxyServer.HOST_HOOK_PORT + " / " + NeoProxyServer.HOST_CONNECT_PORT);
            sendJson("dashboard_data", json);
        }

        private void handleRefreshLocation(String ip) {
            ServerLogger.infoWithSource("WebAdmin", "webAdmin.refreshingLoc", remoteIp);
            HostClient target = null;
            for (HostClient hc : NeoProxyServer.availableHostClient) {
                if (hc.getHostServerHook().getInetAddress().getHostAddress().equals(ip)) {
                    target = hc;
                    break;
                }
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
            try {
                send(json);
            } catch (IOException ignored) {
            }
        }

        public void sendJsonRaw(String json) {
            try {
                send(json);
            } catch (IOException ignored) {
            }
        }

        @Override
        protected void onException(IOException exception) {
            if (NeoProxyServer.IS_DEBUG_MODE) ServerLogger.errorWithSource("WebAdmin", "webAdmin.conflict", exception);
        }
    }
}