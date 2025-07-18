package neoproject.neoproxy;

import neoproject.neoproxy.core.*;
import neoproject.neoproxy.core.exceptions.*;
import neoproject.neoproxy.core.threads.Transformer;
import neoproject.neoproxy.core.threads.management.AdminThread;
import neoproject.neoproxy.core.threads.management.CheckUpdateThread;
import neoproject.neoproxy.core.threads.management.TransferSocketAdapter;
import plethora.management.bufferedFile.BufferedFile;
import plethora.print.log.LogType;
import plethora.print.log.Loggist;
import plethora.print.log.State;
import plethora.time.Time;
import plethora.utils.ArrayUtils;
import plethora.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

import static neoproject.neoproxy.core.threads.management.CheckUpdateThread.UPDATE_PORT;

public class NeoProxyServer {
    public static final String CURRENT_DIR_PATH = System.getProperty("user.dir");
    public static final File KEY_FILE_DIR = new File(CURRENT_DIR_PATH + File.separator + "keys");

    public static String EXPECTED_CLIENT_VERSION = "2.3-RELEASE";
    public static final CopyOnWriteArrayList<String> availableVersions = ArrayUtils.stringArrayToList(EXPECTED_CLIENT_VERSION.split("\\|"));

    public static int HOST_HOOK_PORT = 801;
    public static int HOST_CONNECT_PORT = 802;

    public static String LOCAL_DOMAIN_NAME = "127.0.0.1";
    public static CopyOnWriteArrayList<SequenceKey> sequenceKeyDatabase = new CopyOnWriteArrayList<>();
    public static ServerSocket hostServerTransferServerSocket = null;
    public static ServerSocket hostServerHookServerSocket = null;
    public static Loggist loggist;
    public static int START_PORT = 50000;
    public static int END_PORT = 65535;
    public static final CopyOnWriteArrayList<HostClient> availableHostClient = new CopyOnWriteArrayList<>();
    public static boolean IS_DEBUG_MODE = false;

    public static void initLoggist() {
        File logFile = new File(CURRENT_DIR_PATH + File.separator + "logs" + File.separator + Time.getCurrentTimeAsFileName(false) + ".log");
        Loggist l = new Loggist(logFile);
        l.openWriteChannel();
        NeoProxyServer.loggist = l;
    }

    public static void initStructure() {

        initLoggist();//初始化日志系统
        initVaultDatabase();

        ConfigOperator.readAndSetValue();

        try {
            hostServerHookServerSocket = new ServerSocket(HOST_HOOK_PORT);
            TransferSocketAdapter.startThread();
        } catch (IOException e) {
            debugOperation(e);
            sayInfo(LogType.ERROR, "Main", "Can not blind the port , it's Occupied ?");
            System.exit(-1);
        }

        if (!KEY_FILE_DIR.exists()) {
            KEY_FILE_DIR.mkdirs();

        }

        BufferedFile logDir = new BufferedFile(CURRENT_DIR_PATH + File.separator + "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        AdminThread.startThread();//Not completed yet!
        CheckUpdateThread.startThread();

    }

    public static void initVaultDatabase() {
        if (KEY_FILE_DIR.exists()) {
            File[] c = KEY_FILE_DIR.listFiles();
            if (c == null) {
                sayInfo(LogType.WARNING, "Main", "No key at all...");
            }
            if (c != null) {
                for (File file : c) {
                    sequenceKeyDatabase.add(new SequenceKey(file));
                }
            }
//            vaultDatabase.add(new Vault(new File("C:\\Users\\Administrator\\Desktop\\Test\\AtomServerX\\vault\\Sample1")));

        } else {
            sayInfo(LogType.ERROR, "Main", "No key at all...");
            KEY_FILE_DIR.mkdirs();
            System.exit(-1);
        }
    }

    private static void checkARGS(String[] args) {
        for (String arg : args) {
            switch (arg) {
                case "--no-color" -> loggist.disableColor();
                case "--debug" -> IS_DEBUG_MODE = true;
            }
        }
    }

    public static void main(String[] args) {

        NeoProxyServer.initStructure();
        NeoProxyServer.checkARGS(args);

        sayInfo("-----------------------------------------------------");
        sayInfo("""
                
                   _____                                    \s
                  / ____|                                   \s
                 | |        ___   _ __    ___   __  __   ___\s
                 | |       / _ \\ | '__|  / _ \\  \\ \\/ /  / _ \\
                 | |____  |  __/ | |    | (_) |  >  <  |  __/
                  \\_____|  \\___| |_|     \\___/  /_/\\_\\  \\___|
                                                            \s
                                                             \
                """);


        sayInfo("Current log file : " + loggist.getLogFile().getAbsolutePath());
        sayInfo("LOCAL_DOMAIN_NAME: " + LOCAL_DOMAIN_NAME);
        sayInfo("Listen HOST_CONNECT_PORT on " + HOST_CONNECT_PORT);
        sayInfo("Listen HOST_HOOK_PORT on " + HOST_HOOK_PORT);
        sayInfo("Listen UPDATE_PORT on " + UPDATE_PORT);
        sayInfo("Support client versions: " + EXPECTED_CLIENT_VERSION);


        while (true) {
            try {
                HostClient hostClient = listenAndConfigureHostClient();//监听端口accept socket，并且检查是不是ban的ip，如果不是，则返回初始化的HostClient对象

                new Thread(() -> {//这个子线程是监听 host client 连接的服务
                    try {
                        //检查host client合法性,并且打印相关信息到控制台和告诉host client相关要素
                        NeoProxyServer.checkHostClientLegitimacyAndTellInfo(hostClient);

                        //开始服务
                        NeoProxyServer.handleTransformerServiceWithNewThread(hostClient);

                    } catch (UnSupportHostVersionException | IndexOutOfBoundsException | IOException |
                             NoMorePortException |
                             AlreadyBlindPortException | UnRecognizedKeyException e) {
                        // exception class will auto say OTHER info ! Just do things.
//                        e.printStackTrace();
                        InfoBox.sayHostClientDiscInfo(hostClient, "Main");
                        hostClient.close();
                    } catch (SlientException ignore) {
                    }
                }).start();
            } catch (IOException e) {
                sayInfo(LogType.INFO, "Main", "A host client try to connect but fail .");
            } catch (SlientException ignored) {
            }
        }

    }

    public static HostClient listenAndConfigureHostClient() throws SlientException, IOException {
        //listen for host client,and check is ban and available
        Socket hostServerHook = hostServerHookServerSocket.accept();
        if (IPChecker.exec(hostServerHook, IPChecker.CHECK_IS_BAN)) {
            InternetOperator.close(hostServerHook);
            SlientException.throwException();//skip while
        }
        sayInfo("HostClient on " + hostServerHook.getInetAddress() + ":" + hostServerHook.getPort() + " try to connect !");
        HostClient hostClient = null;
        try {
            hostClient = new HostClient(hostServerHook);
        } catch (IllegalConnectionException e) {
            SlientException.throwException();//skip while
        }
        return hostClient;
    }

    public static void handleTransformerServiceWithNewThread(HostClient hostClient) {
        new Thread(() -> {//这个子线程是对于连接成功的 host client 后续的服务
            while (true) {

                Socket client;

                //等待外网client连接
                try {
                    if (hostClient.getClientServerSocket().isClosed()) {
                        break;//msg is print to the console at CheckAliveThread ! then it die
                    } else {
                        client = hostClient.getClientServerSocket().accept();//这里会发生阻塞等待
                    }
                } catch (IOException e) {
                    debugOperation(e);
                    continue;
                }


                //外部client连接以后，提醒 host client 有客户端连接了。
                try {//send ":>sendSocket;{clientAddr}" str to tell host client to connect
                    //example ":>sendSocket;cha.ceron.fun:50001"
                    InternetOperator.sendCommand(hostClient, "sendSocket" + ";" + InternetOperator.getInternetAddressAndPort(client));
                } catch (Exception e) {
                    InfoBox.sayHostClientDiscInfo(hostClient, "Main");
                    hostClient.close();
                    InternetOperator.close(client);
                    break;//break inner because host client is destroyed.
                }

                //获取host client发来的AES传输通道socket
                HostSign hostSign;
                try {
                    hostSign = TransferSocketAdapter.getThisHostClientHostSign(hostClient.getOutPort());
                } catch (IOException e) {//if host client timeout
                    InfoBox.sayClientSuccConnecToChaSerButHostClientTimeOut(hostClient);
                    sayInfo("Killing client's side connection: " + InternetOperator.getInternetAddressAndPort(client));
                    InternetOperator.close(client);
                    continue;
                }

                //立即服务
                Transformer.startThread(hostClient, hostSign, client);

                InfoBox.sayClientConnectBuildUpInfo(hostClient, client);//say connection build up info
            }
        }).start();
    }

    public static void sayInfo(String str) {
        loggist.say(new State(LogType.INFO, "Main", str));
    }

    public static void sayInfo(LogType type, String subject, String str) {
        loggist.say(new State(type, subject, str));
    }

    private static int getCurrentAvailableOutPort() {
        for (int i = START_PORT; i <= END_PORT; i++) {
            try {
                ServerSocket serverSocket = new ServerSocket(i);
                serverSocket.close();
                return i;
            } catch (Exception ignore) {
            }
        }
        return -1;
    }

    private static void checkHostClientLegitimacyAndTellInfo(HostClient hostClient) throws IOException, NoMorePortException, SlientException, UnRecognizedKeyException, AlreadyBlindPortException, UnSupportHostVersionException {
        //get and check host client property
        Object[] obj = NeoProxyServer.checkHostClientVersionAndKeyAndLang(hostClient);
        sayInfo("HostClient on " + InternetOperator.getInternetAddressAndPort(hostClient.getHostServerHook()) + " register successfully!");
        hostClient.enableCheckAliveThread();
        availableHostClient.add(hostClient);

        //generate them into pieces for use
        hostClient.setVault((SequenceKey) obj[0]);
        hostClient.setLangData((LanguageData) obj[1]);

        //arrange port if no specific , or give the chosen one
        int port;
        if (hostClient.getVault().getPort() != -1) {
            port = hostClient.getVault().getPort();
        } else {
            port = NeoProxyServer.getCurrentAvailableOutPort();
            if (port == -1) {
                NoMorePortException.throwException();
            }
        }
        hostClient.setOutPort(port);

        hostClient.setClientServerSocket(new ServerSocket(port));

        InternetOperator.sendCommand(hostClient, String.valueOf(port));//tell the host client remote out port
        //tell the message to the host client
        InternetOperator.sendStr(hostClient, hostClient.getLangData().THIS_ACCESS_CODE_HAVE + hostClient.getVault().getRate() + hostClient.getLangData().MB_OF_FLOW_LEFT);
        InternetOperator.sendStr(hostClient, hostClient.getLangData().EXPIRE_AT + hostClient.getVault().getExpireTime());

        InternetOperator.sendStr(hostClient, hostClient.getLangData().USE_THE_ADDRESS + LOCAL_DOMAIN_NAME + ":" + port + hostClient.getLangData().TO_START_UP_CONNECTION);//send remote connect address
        //print the property into the console
        sayInfo("Assigned connection address: " + LOCAL_DOMAIN_NAME + ":" + port);
    }

    private static Object[] checkHostClientVersionAndKeyAndLang(HostClient hostClient) throws IOException, UnSupportHostVersionException, UnRecognizedKeyException, AlreadyBlindPortException, IndexOutOfBoundsException, SlientException {
        if (hostClient.getReader() == null) {
            SlientException.throwException();
        }
        String hostClientInfo = InternetOperator.receiveStr(hostClient);//host client property in one line

        if (hostClientInfo == null) {
            UnSupportHostVersionException.throwException("_NULL_", hostClient);
        }

        assert hostClientInfo != null;
        String[] info = hostClientInfo.split(";");//make them into pieces for use

        if (info.length != 3) {//只允许 3 的检查数组长度
            UnSupportHostVersionException.throwException("_NULL_", hostClient);
        }

        // zh;version;key
        LanguageData languageData;
        if (info[0].equals("zh")) {
            languageData = LanguageData.getChineseLanguage();
        } else {
            languageData = new LanguageData();
        }

        boolean isAvailVersion = availableVersions.contains(info[1]);
        if (!isAvailVersion) {
            InternetOperator.sendStr(hostClient, languageData.UNSUPPORTED_VERSION_MSG + EXPECTED_CLIENT_VERSION);
            hostClient.close();
            UnSupportHostVersionException.throwException(info[1], hostClient);
        }

        SequenceKey currentSequenceKey = NeoProxyServer.getKeyOnDatabase(info[2]);
        if (currentSequenceKey == null) {//在数据库里找不到序列号,踢掉
            InternetOperator.sendStr(hostClient, languageData.ACCESS_DENIED_FORCE_EXITING);
            hostClient.close();
            UnRecognizedKeyException.throwException(info[2]);
        }

        assert currentSequenceKey != null;//找到了
        if (currentSequenceKey.getPort() != -1) {//check port on key file!
            try {

                ServerSocket serverSocket = new ServerSocket(currentSequenceKey.getPort());//check is not occupied
                serverSocket.close();

            } catch (IOException e) {// if is blind
                InternetOperator.sendStr(hostClient, languageData.THE_PORT_HAS_ALREADY_BLIND);
                AlreadyBlindPortException.throwException(currentSequenceKey.getPort());
            }
        }

        //if nothing is bad,complete checking
        InternetOperator.sendStr(hostClient, languageData.CONNECTION_BUILD_UP_SUCCESSFULLY);
        return new Object[]{currentSequenceKey, languageData};
    }

    public static SequenceKey getKeyOnDatabase(String key) {
        for (SequenceKey sequenceKey : sequenceKeyDatabase) {
            if (key.equals(sequenceKey.getName())) {// vault file does not have suffix
                return sequenceKey;
            }
        }
        return null;
    }

    public static void debugOperation(Exception e) {
        if (IS_DEBUG_MODE) {
            String exceptionMsg = StringUtils.getExceptionMsg(e);
            System.out.println(exceptionMsg);
            loggist.write(exceptionMsg, true);
        }
    }

}
