package neoproject.neoproxy.core;

import java.io.*;
import java.net.Socket;

public class IPChecker {
    private static final File bannedIPList = new File(System.getProperty("user.dir") + File.separator + "banList.txt");
    public static boolean ENABLE_BAN = true;
    public static final int DO_BAN = 0;
    public static final int CHECK_IS_BAN = 1;

    static {

        try {
            if (!bannedIPList.exists()) {
                bannedIPList.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }


    }

    public static synchronized boolean exec(String ip, int execMode) {
        if (execMode == DO_BAN) {
            if (ENABLE_BAN) {
                try {
                    BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(bannedIPList, true));
                    bufferedWriter.write(ip);
                    bufferedWriter.newLine();
                    bufferedWriter.flush();
                    bufferedWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
            return false;
        } else if (execMode == CHECK_IS_BAN) {
            if (!ENABLE_BAN) {//enableBan==false
                return false;
            }
            try {
                if (!bannedIPList.exists()) {
                    return false;
                }
                BufferedReader bufferedReader = new BufferedReader(new FileReader(bannedIPList));
                String str;
                while ((str = bufferedReader.readLine()) != null) {
                    if (str.equals(ip)) {
                        bufferedReader.close();
                        return true;
                    }
                }
                bufferedReader.close();
                return false;
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
                return false;
            }
        }
        return false;
    }

    public static synchronized boolean exec(Socket socket, int execMode) {
        return exec(InternetOperator.getIP(socket), execMode);
    }
}
