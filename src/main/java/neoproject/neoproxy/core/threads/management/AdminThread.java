package neoproject.neoproxy.core.threads.management;

public class AdminThread implements Runnable {
    public static int ADMIN_PORT = 945;
    public static String ADMIN_PASSWORD = "8888888";

    public static void startThread() {
        new Thread(new AdminThread()).start();
        //TODO Somethings
    }

    @Override
    public void run() {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    ServerSocket serverSocket = new ServerSocket(ADMIN_PORT);
//                    while (true) {
//                        Socket client = serverSocket.accept();
//                        new Thread(new Runnable() {
//                            @Override
//                            public void run() {
//                                try {
//                                    String password = receiveStr(client);
//                                    if (password.equals(PASSWORD)) {
//                                        sendStr(client, "Login successfully");
//                                        while (true) {
//                                            String command = receiveStr(client);
//                                            if (command.equals("reloadV")) {
//                                            }
//                                        }
//                                    } else {
//                                        sendStr(client, "Access denied , force exiting");
//                                        client.close();
//                                    }
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
//                            }
//                        }).start();
//                    }
//
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }).start();


    }
}
