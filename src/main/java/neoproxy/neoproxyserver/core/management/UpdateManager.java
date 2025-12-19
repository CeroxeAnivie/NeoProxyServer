package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.core.Debugger;
import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.ServerLogger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static neoproxy.neoproxyserver.NeoProxyServer.CURRENT_DIR_PATH;
import static neoproxy.neoproxyserver.core.Debugger.debugOperation;
import static neoproxy.neoproxyserver.core.ServerLogger.alert;

public class UpdateManager {
    public static final String CLIENT_DIR = CURRENT_DIR_PATH + File.separator + "clients";
    public static final File CLIENT_DIR_FOLDER = new File(CLIENT_DIR);
    private static final int CHUNK_SIZE = 8192; // 8KB块大小
    private static boolean isInit = false;

    public static void init() {
        Debugger.debugOperation("UpdateManager initializing at: " + CLIENT_DIR);
        if (!CLIENT_DIR_FOLDER.exists()) {
            boolean b = CLIENT_DIR_FOLDER.mkdirs();
            if (!b) {
                ServerLogger.error("updateManager.failToCreateUpdateDir");
                Debugger.debugOperation("Failed to create update directory.");
                return;
            }
            if (!CLIENT_DIR_FOLDER.canRead()) {
                ServerLogger.error("updateManager.updateDirUnreadable");
                Debugger.debugOperation("Update directory is unreadable.");
            } else {
                isInit = true;
                Debugger.debugOperation("Update directory initialized.");
            }
        } else {
            isInit = true;
            Debugger.debugOperation("Update directory exists.");
        }
    }

    public static void handle(HostClient hostClient) {
        Debugger.debugOperation("Handling update request for client: " + hostClient.getIP());
        boolean isWantedToUpdate;
        try {
            String updateFlag = hostClient.getHostServerHook().receiveStr(1000);
            isWantedToUpdate = Boolean.parseBoolean(updateFlag);
            Debugger.debugOperation("Client update requested: " + isWantedToUpdate);
        } catch (IOException e) {
            debugOperation(e);
            hostClient.close();
            return;
        }

        if (!isWantedToUpdate) {
            Debugger.debugOperation("Client does not want update. Exiting update handler.");
            return;
        }

        if (!isInit || !CLIENT_DIR_FOLDER.exists()) {
            Debugger.debugOperation("Update environment not initialized. Denying update.");
            tellFalseAndClose(hostClient);
            return;
        }

        File[] files = CLIENT_DIR_FOLDER.listFiles();
        if (files == null) {
            Debugger.debugOperation("Update folder is empty. Denying update.");
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
            String str = hostClient.getHostServerHook().receiveStr(); // 获取客户端是要 7z 还是jar
            Debugger.debugOperation("Client requested file format: " + str);

            if (str.equals("7z")) {
                if (sevenZFile == null) {
                    Debugger.debugOperation("7z file requested but not found.");
                    tellFalseAndClose(hostClient);
                    if (alert) {
                        ServerLogger.errorWithSource("Update-Manager", "updateManager.exeNotFound");
                    }
                } else {
                    if (alert) {
                        ServerLogger.infoWithSource("Update-Manager", "updateManager.clientDownloadingExe");
                    }
                    Debugger.debugOperation("Starting 7z download for client.");
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
                    Debugger.debugOperation("Jar file requested but not found.");
                    tellFalseAndClose(hostClient);
                    if (alert) {
                        ServerLogger.errorWithSource("Update-Manager", "updateManager.jarNotFound");
                    }
                } else {
                    if (alert) {
                        ServerLogger.infoWithSource("Update-Manager", "updateManager.clientDownloadingJar");
                    }
                    Debugger.debugOperation("Starting Jar download for client.");
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
            hostClient.getHostServerHook().sendStr("false"); // 告诉客户端无法下载
            Debugger.debugOperation("Sent 'false' to client.");
        } catch (IOException ignored) {
        }
        hostClient.close();
    }

    private static void tellTrueAndWriteAndClose(HostClient hostClient, File file) {
        Debugger.debugOperation("Transferring file: " + file.getAbsolutePath());
        try {
            hostClient.getHostServerHook().sendStr("true"); // 告诉客户端可以下载

            // 发送文件大小（使用字符串形式）
            long fileSize = file.length();
            hostClient.getHostServerHook().sendStr(String.valueOf(fileSize));
            Debugger.debugOperation("File size: " + fileSize);

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
            Debugger.debugOperation("File transfer completed.");
        } catch (IOException e) {
            debugOperation(e);
            ServerLogger.error("updateManager.clientDownloadFailed");
        }
        hostClient.close();
    }
}