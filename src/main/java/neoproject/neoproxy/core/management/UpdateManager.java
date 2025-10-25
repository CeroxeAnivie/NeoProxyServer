package neoproject.neoproxy.core.management;

import neoproject.neoproxy.core.HostClient;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static neoproject.neoproxy.NeoProxyServer.*;
import static neoproject.neoproxy.core.InfoBox.alert;

public class UpdateManager {
    public static final String CLIENT_DIR = CURRENT_DIR_PATH + File.separator + "clients";
    public static final File CLIENT_DIR_FOLDER = new File(CLIENT_DIR);
    private static boolean isInit = false;

    public static void init() {
        if (!CLIENT_DIR_FOLDER.exists()) {
            boolean b = CLIENT_DIR_FOLDER.mkdirs();
            if (!b) {
                myConsole.warn("Update-Manager", "Fail to create the update dir , this feature will be disabled.");
                return;
            }
            if (!CLIENT_DIR_FOLDER.canRead()) {
                myConsole.warn("Update-Manager", "The update dir is unreadable ! This feature will be disabled.");
            } else {
                isInit = true;
            }
        } else {
            isInit = true;
        }
    }

    public static void handle(HostClient hostClient) {
        if (!isInit || !CLIENT_DIR_FOLDER.exists()) {//无法初始化
            tellFalseAndClose(hostClient);
            return;
        }

        File[] files = CLIENT_DIR_FOLDER.listFiles();
        if (files == null) {
            tellFalseAndClose(hostClient);
            if (alert){
                myConsole.warn("Update-Manager", "A client try to download an update but clients dir is empty !");
            }
            return;
        }

        File exeFile = null;
        for (File file : files) {
            if (file.getName().endsWith(".exe")) {
                exeFile = file;
                break;
            }
        }
        File jarFile = null;
        for (File file : files) {
            if (file.getName().endsWith(".jar")) {
                jarFile = file;
                break;
            }
        }


        try {
            String str = hostClient.getHostServerHook().receiveStr();//获取客户端是要exe还是jar
            if (str.equals("exe")) {
                if (exeFile == null) {
                    tellFalseAndClose(hostClient);
                    if (alert){
                        myConsole.warn("Update-Manager", "A client try to download an EXE not found in dir.");
                    }
                } else {
                    if (alert){
                        myConsole.log("Update-Manager", "A client try to download an EXE update !");
                    }
                    tellTrueAndWriteAndClose(hostClient, exeFile);
                    if (alert){
                        myConsole.log("Update-Manager", "A client try to download an EXE update !");
                    }
                }
            } else {
                if (jarFile == null) {
                    tellFalseAndClose(hostClient);
                    if (alert){
                        myConsole.warn("Update-Manager", "A client try to download an JAR not found in dir.");
                    }
                } else {
                    if (alert){
                        myConsole.log("Update-Manager", "A client try to download an JAR update !");
                    }
                    tellTrueAndWriteAndClose(hostClient, jarFile);
                    if (alert){
                        myConsole.log("Update-Manager", "A client from " + hostClient.getAddressAndPort() + " downloaded an JAR update success !");
                    }
                }
            }
        } catch (Exception e) {
            debugOperation(e);
            if (alert){
                myConsole.warn("Update-Manager", "A client try to download an update but failed.");
            }
        }
    }

    private static void tellFalseAndClose(HostClient hostClient) {
        try {
            hostClient.getHostServerHook().receiveStr();//ignore
            hostClient.getHostServerHook().sendStr("false");//告诉客户端无法下载
        } catch (IOException ignored) {
        }
        hostClient.close();
    }

    private static void tellTrueAndWriteAndClose(HostClient hostClient, File file) {
        try {
            hostClient.getHostServerHook().sendStr("true");//告诉客户端可以下载
            BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
            hostClient.getHostServerHook().sendByte(bufferedInputStream.readAllBytes());
        } catch (IOException e) {
            debugOperation(e);
            myConsole.warn("Update-Manager", "A client try to download an update but failed.");
        }
        hostClient.close();
    }
}