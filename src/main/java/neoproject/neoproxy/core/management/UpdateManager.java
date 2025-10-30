package neoproject.neoproxy.core.management;

import neoproject.neoproxy.core.HostClient;
import neoproject.neoproxy.core.ServerLogger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static neoproject.neoproxy.NeoProxyServer.CURRENT_DIR_PATH;
import static neoproject.neoproxy.NeoProxyServer.debugOperation;
import static neoproject.neoproxy.core.ServerLogger.alert;

public class UpdateManager {
    public static final String CLIENT_DIR = CURRENT_DIR_PATH + File.separator + "clients";
    public static final File CLIENT_DIR_FOLDER = new File(CLIENT_DIR);
    private static final int CHUNK_SIZE = 8192; // 8KB块大小
    private static boolean isInit = false;

    public static void init() {
        if (!CLIENT_DIR_FOLDER.exists()) {
            boolean b = CLIENT_DIR_FOLDER.mkdirs();
            if (!b) {
                ServerLogger.error("updateManager.failToCreateUpdateDir");
                return;
            }
            if (!CLIENT_DIR_FOLDER.canRead()) {
                ServerLogger.error("updateManager.updateDirUnreadable");
            } else {
                isInit = true;
            }
        } else {
            isInit = true;
        }
    }

    public static void handle(HostClient hostClient) {
        if (!isInit || !CLIENT_DIR_FOLDER.exists()) {
            tellFalseAndClose(hostClient);
            return;
        }

        File[] files = CLIENT_DIR_FOLDER.listFiles();
        if (files == null) {
            tellFalseAndClose(hostClient);
            if (alert) {
                ServerLogger.errorWithSource("Update-Manager", "updateManager.clientsDirEmpty");
            }
            return;
        }

        File sevenZFile = null;
        for (File file : files) {
            if (file.getName().endsWith(".7z")) {
                sevenZFile = file;
                break;
            }
        }

        try {
            String str = hostClient.getHostServerHook().receiveStr(); // 获取客户端是要exe还是jar
            if (str.equals("exe")) {
                if (sevenZFile == null) {
                    tellFalseAndClose(hostClient);
                    if (alert) {
                        ServerLogger.errorWithSource("Update-Manager", "updateManager.exeNotFound");
                    }
                } else {
                    if (alert) {
                        ServerLogger.infoWithSource("Update-Manager", "updateManager.clientDownloadingExe");
                    }
                    tellTrueAndWriteAndClose(hostClient, sevenZFile);
                    if (alert) {
                        ServerLogger.infoWithSource("Update-Manager", "updateManager.exeDownloadSuccess", hostClient.getAddressAndPort());
                    }
                }
            } else {
                // 对于jar请求，保持原有逻辑
                File jarFile = null;
                for (File file : files) {
                    if (file.getName().endsWith(".jar")) {
                        jarFile = file;
                        break;
                    }
                }

                if (jarFile == null) {
                    tellFalseAndClose(hostClient);
                    if (alert) {
                        ServerLogger.errorWithSource("Update-Manager", "updateManager.jarNotFound");
                    }
                } else {
                    if (alert) {
                        ServerLogger.infoWithSource("Update-Manager", "updateManager.clientDownloadingJar");
                    }
                    tellTrueAndWriteAndClose(hostClient, jarFile);
                    if (alert) {
                        ServerLogger.infoWithSource("Update-Manager", "updateManager.jarDownloadSuccess", hostClient.getAddressAndPort());
                    }
                }
            }
        } catch (Exception e) {
            debugOperation(e);
            if (alert) {
                ServerLogger.errorWithSource("Update-Manager", "updateManager.downloadFailed");
            }
        }
    }

    private static void tellFalseAndClose(HostClient hostClient) {
        try {
            hostClient.getHostServerHook().receiveStr(); // ignore
            hostClient.getHostServerHook().sendStr("false"); // 告诉客户端无法下载
        } catch (IOException ignored) {
        }
        hostClient.close();
    }

    private static void tellTrueAndWriteAndClose(HostClient hostClient, File file) {
        try {
            hostClient.getHostServerHook().sendStr("true"); // 告诉客户端可以下载

            // 发送文件大小（使用字符串形式）
            long fileSize = file.length();
            hostClient.getHostServerHook().sendStr(String.valueOf(fileSize));

            // 分块发送文件内容
            try (BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;

                while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                    // 只发送实际读取的字节数
                    byte[] chunkToSend = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunkToSend, 0, bytesRead);
                    hostClient.getHostServerHook().sendByte(chunkToSend);
                }
            }
        } catch (IOException e) {
            debugOperation(e);
            ServerLogger.error("updateManager.clientDownloadFailed");
        }
        hostClient.close();
    }
}