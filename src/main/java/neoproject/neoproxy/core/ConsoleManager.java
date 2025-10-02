package neoproject.neoproxy.core;

import plethora.utils.MyConsole;

import java.util.List;

import static neoproject.neoproxy.NeoProxyServer.myConsole;
import static neoproject.neoproxy.NeoProxyServer.sequenceKeyDatabase;


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
        myConsole.registerCommand("echo", "回显输入的文本", (List<String> params) -> {
            if (params.isEmpty()) {
                myConsole.warn("Admin", "用法: echo <文本>");
            } else {
                myConsole.log("Admin", "回显: " + String.join(" ", params));
            }
        });

        // 注册一个模拟错误的命令
        myConsole.registerCommand("key", "序列号管理", (List<String> params) -> {
            if (params.getFirst().equals("add")) {
                //name rate expireTime port
                try {
                    SequenceKey key = SequenceKey.createNewKey(params.get(1), Double.parseDouble(params.get(2)), params.get(3), Integer.parseInt(params.get(4)));
                    sequenceKeyDatabase.add(key);
                    myConsole.log("Admin", "Key " + params.get(1) + " now is created !");
                } catch (Exception e) {
                    myConsole.error("Admin", "Fail to create key");
                    myConsole.error("Admin", e.getMessage(), e);
                    myConsole.warn("Admin", "Usage: key add <name> <rate> <expireTime> <port>");
                }
            }
        });
    }
}
