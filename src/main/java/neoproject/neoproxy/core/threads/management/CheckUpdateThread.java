package neoproject.neoproxy.core.threads.management;

import neoproject.neoproxy.NeoProxyServer;
import neoproject.neoproxy.core.IPChecker;
import neoproject.neoproxy.core.InternetOperator;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class CheckUpdateThread implements Runnable {
    public static int UPDATE_PORT = 803;

    public static void startThread() {
        Thread t = new Thread(new CheckUpdateThread());
        t.start();
    }

    private static void enable(String dirPath) {
        new Thread(() -> {
            File destDir = new File(dirPath);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }

            try {
                File[] files = destDir.listFiles();
                File jarFile = null;
                File exeFile = null;
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(".exe")) {
                            exeFile = file;
                        } else {
                            jarFile = file;
                        }
                    }
                } else {
                    throw new NullPointerException();
                }

                ServerSocket serverSocket = new ServerSocket(UPDATE_PORT);
                while (true) {
                    Socket client = serverSocket.accept();
                    if (IPChecker.exec(client, IPChecker.CHECK_IS_BAN)) {
                        client.close();
                        continue;
                    }

                    BufferedOutputStream clientBufferedOutputStream = new BufferedOutputStream(client.getOutputStream());
                    BufferedInputStream clientBufferedInputStream = new BufferedInputStream(client.getInputStream());
                    BufferedInputStream fileBufferedInputStream;

                    int type = clientBufferedInputStream.read();//获取需要的类型，exe，还是jar
                    if (type == 0) {//exe
                        assert exeFile != null;
                        fileBufferedInputStream = new BufferedInputStream(new FileInputStream(exeFile));
                        NeoProxyServer.sayInfo("A host client from " + client.getInetAddress() + " try to download an EXE update.");
                    } else {
                        assert jarFile != null;
                        fileBufferedInputStream = new BufferedInputStream(new FileInputStream(jarFile));
                        NeoProxyServer.sayInfo("A host client from " + client.getInetAddress() + " try to download an JAR update.");
                    }

                    new Thread(() -> {
                        try {


                            byte[] data = new byte[500000];//0.5mb
                            int len;
                            while ((len = fileBufferedInputStream.read(data)) != -1) {
                                clientBufferedOutputStream.write(data, 0, len);
                                clientBufferedOutputStream.flush();
                            }
                            fileBufferedInputStream.close();//socket will close
                            clientBufferedOutputStream.close();
                            client.close();

                            if (type == 0) {
                                NeoProxyServer.sayInfo("A host client from " + client.getInetAddress() + " download an EXE update success !");
                            } else {
                                NeoProxyServer.sayInfo("A host client from " + client.getInetAddress() + " download an JAR update success !");
                            }
                        } catch (Exception e) {
                            NeoProxyServer.sayInfo("Fail to write data to " + InternetOperator.getInternetAddressAndPort(client));
                            IPChecker.exec(client, IPChecker.DO_BAN);
                            InternetOperator.close(client);
                        }
                    }).start();
                }
            } catch (IOException e) {
                NeoProxyServer.debugOperation(e);
                System.exit(-1);
            } catch (NullPointerException e) {
                NeoProxyServer.sayInfo("No update file in exe directory , close thread...");
            }
        }).start();
    }

    @Override
    public void run() {
        CheckUpdateThread.enable(System.getProperty("user.dir") + File.separator + "client");
    }
}
