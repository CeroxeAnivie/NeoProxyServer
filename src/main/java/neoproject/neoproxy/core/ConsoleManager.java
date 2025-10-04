package neoproject.neoproxy.core;

import plethora.utils.MyConsole;

import java.util.List;

import static neoproject.neoproxy.NeoProxyServer.*;


public class ConsoleManager {
    public static void init() {
        try {
            myConsole = new MyConsole("NeoProxyServer");
            initCommand();
            myConsole.start();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static void initCommand() {
        // 注册一个回显命令
        myConsole.registerCommand(null, "不存在的指令", (List<String> params) -> {
            myConsole.warn("Admin", "不存在的指令，输入 help 获取帮助。");
        });

        myConsole.registerCommand("key", "序列号管理", (List<String> params) -> {
            if (params.isEmpty()) {
                myConsole.warn("Admin", "Usage: \nkey add <name> <balance> <expireTime> <port> \nkey del <name> \nkey list");
            } else if (params.getFirst().equals("add")) {
                //name balance expireTime port
                boolean isCreated = SequenceKey.createNewKey(params.get(1), Double.parseDouble(params.get(2)), params.get(3), Integer.parseInt(params.get(4)), Integer.parseInt(params.get(5)));
                if (isCreated) {
                    myConsole.log("Admin", "Key " + params.get(1) + " now is created !");
                } else {
                    myConsole.error("Admin", "Fail to create key");
                    myConsole.warn("Admin", "Usage: key add <name> <balance> <expireTime> <port> \nexample: key add abcd 500 2030/12/01-13:05 8090");
                }
            } else if (params.getFirst().equals("del")) {
                if (params.size() != 2) {
                    myConsole.warn("Admin", "key del <name>");
                } else {
                    if (SequenceKey.removeKey(params.get(1))) {
                        myConsole.log("Admin", "Key " + params.get(1) + " now is deleted !");
                    } else {
                        myConsole.warn("Admin", "No such key.");
                    }
                }
            } else if (params.getFirst().equals("list")) {
                if (params.size() == 1) {
                    for (SequenceKey sequenceKey : sequenceKeyDatabase) {
                        myConsole.log("Admin", "\nname:" + sequenceKey.getName() + " balance:" + sequenceKey.getBalance() + " \nexpireTime:" + sequenceKey.getExpireTime() + " port:" + sequenceKey.getPort() + "\nrate=" + killDoubleEndZero(sequenceKey.getRate()) + "mbps " + +findKeyClientNum(sequenceKey) + " HostClient Active");
                    }
                } else if (params.get(1).equals("name")) {
                    String str = "";
                    for (SequenceKey sequenceKey : sequenceKeyDatabase) {
                        str = str.concat(sequenceKey.getName() + "(" + findKeyClientNum(sequenceKey) + ")" + " ");
                    }
                    myConsole.log("Admin", str);
                } else if (params.get(1).equals("balance")) {
                    String str = "\n";
                    for (SequenceKey sequenceKey : sequenceKeyDatabase) {
                        str = str.concat(sequenceKey.getName() + "(" + sequenceKey.getBalance() + ")" + "\n");
                    }
                    myConsole.log("Admin", str);
                }

            } else {
                myConsole.warn("Admin", "Usage: \nkey add <name> <balance> <expireTime> <port> \nkey del <name> \nkey list");
            }
        });
    }

    private static int findKeyClientNum(SequenceKey key) {//返回这个 Key 有多少个客户端正在使用
        String name = key.getName();
        int num = 0;
        for (HostClient hostClient : availableHostClient) {
            if (hostClient.getKey().getName().equals(name)) {
                num++;
            }
        }
        return num;
    }
    private static String killDoubleEndZero(double d){
        String str=String.valueOf(d);
        while (str.endsWith("0")){
            str=str.substring(0,str.length()-1);
            if (str.endsWith(".")){
                str=str.substring(0,str.length()-1);
                break;
            }
        }
        return str;
    }
}
