package neoproject.neoproxy.core;

import plethora.utils.MyConsole;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static neoproject.neoproxy.NeoProxyServer.*;
import static neoproject.neoproxy.core.SequenceKey.saveToFile;


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
                myConsole.warn("Admin", "Usage: \nkey add <name> <balance> <expireTime> <port> <rate> \nkey del <name> \nkey list");
            } else if (params.getFirst().equals("add")) {//添加 key
                //name balance expireTime port rate
                checkAddParamsIsLegalAndSet(params);
            } else if (params.getFirst().equals("del")) {//删除 key
                checkDelParamsIsLegalAndSet(params);
            } else if (params.getFirst().equals("list")) {//列出所有 key 信息
                //Usage: key list | key list <name> | key list <name | balance | rate | expire-time>
                checkListParamsIsLegalAndSet(params);
            } else if (params.getFirst().equals("lp")) {//查找指定的 key 信息
                checkLookupParamsIsLegalAndSet(params);
            } else if (params.getFirst().equals("set")) {//设置已有的 key 数值
                checkSetParamsIsLegalAndSet(params);
            } else {
                myConsole.warn("Admin", "Usage: \nkey add <name> <balance> <expireTime> <port> \nkey set <name> <balance> <expireTime> <port> <rate> \nkey del <name> \nkey list \nkey lp <name>");
            }
        });
    }

    private static void checkSetParamsIsLegalAndSet(List<String> params) {
        if (params.size()!=6){
            myConsole.warn("Admin", "Usage: key set <name> <balance> <expireTime> <port> <rate>");
            return;
        }
        String expireTime=correctInputTime(params.get(3));
        if (expireTime!=null){
            if (!SequenceKey.isOutOfDate(expireTime)){
                boolean isFound=false;
                for (SequenceKey sequenceKey : sequenceKeyDatabase) {
                    if (sequenceKey.getName().equals(params.get(1))){
                        sequenceKey.balance=Double.parseDouble(params.get(2));
                        sequenceKey.expireTime=params.get(3);
                        sequenceKey.port=Integer.parseInt(params.get(4));
                        sequenceKey.rate=Double.parseDouble(params.get(5));
                        boolean isSuccess=saveToFile(sequenceKey);
                        if (!isSuccess){
                            myConsole.warn("Admin", "Fail to set the key.");
                        }
                        isFound=true;
                    }
                }
                if (isFound){
                    myConsole.log("Admin", "Operation complete !");
                }else {
                    myConsole.warn("Admin", "Could not find the key in database.");
                }
            }else {
                myConsole.warn("Admin", "The entered time cannot be later than the current time.");
            }
        }else{
            myConsole.warn("Admin", "Illegal time input.");
        }
    }

    private static void checkLookupParamsIsLegalAndSet(List<String> params) {
        if (params.size()!=2){
            myConsole.warn("Admin", "Usage: key lp <name>");
        }else{
            for (SequenceKey sequenceKey : sequenceKeyDatabase) {
                if (sequenceKey.getName().equals(params.get(1))){
                    myConsole.log("Admin", "\nname:" + sequenceKey.getName() + " balance:" + sequenceKey.getBalance() + " \nexpireTime:" + sequenceKey.getExpireTime() + " port:" + sequenceKey.getPort() + "\nrate=" + killDoubleEndZero(sequenceKey.getRate()) + "mbps " + +findKeyClientNum(sequenceKey) + " HostClient Active");
                }
            }
        }
    }

    private static void checkListParamsIsLegalAndSet(List<String> params) {
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
        }else if (params.get(1).equals("rate")) {
            String str = "";
            for (SequenceKey sequenceKey : sequenceKeyDatabase) {
                str = str.concat(sequenceKey.getName() + "(" + killDoubleEndZero(sequenceKey.getRate())+"mbps" + ")" + " ");
            }
            myConsole.log("Admin", str);
        }else if (params.get(1).equals("expire-time")) {
            String str = "\n";
            for (SequenceKey sequenceKey : sequenceKeyDatabase) {
                str = str.concat(sequenceKey.getName() + "(" + sequenceKey.getExpireTime() + ")" + "\n");
            }
            myConsole.log("Admin", str);
        }else{
            myConsole.warn("Admin", "Usage: key list | key list <name> | key list <name | balance | rate | expire-time>");
        }
    }

    private static void checkDelParamsIsLegalAndSet(List<String> params) {
        if (params.size() != 2) {
            myConsole.warn("Admin", "key del <name>");
        } else {
            if (SequenceKey.removeKey(params.get(1))) {
                myConsole.log("Admin", "Key " + params.get(1) + " now is deleted !");
            } else {
                myConsole.warn("Admin", "No such key.");
            }
        }
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

    private static String correctInputTime(String time) {
        // 定义一个正则表达式，用于匹配 "年/月/日-时:分" 的格式
        // 其中年必须是4位，月、日、时、分可以是1位或2位数字
        Pattern pattern = Pattern.compile("^(\\d{4})/(\\d{1,2})/(\\d{1,2})-(\\d{1,2}):(\\d{1,2})$");
        Matcher matcher = pattern.matcher(time);

        // 如果输入字符串不符合基本格式，直接返回 null
        if (!matcher.matches()) {
            return null;
        }

        try {
            // 从正则匹配中提取出各个时间部分
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            int hour = Integer.parseInt(matcher.group(4));
            int minute = Integer.parseInt(matcher.group(5));

            // 进行基本的数值范围校验，防止出现 2025/99/99-99:99 这样的情况
            if (month < 1 || month > 12 || day < 1 || day > 31 ||
                    hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }

            // 使用 String.format 方法，将所有部分格式化为固定宽度
            // %04d 代表4位数字，不足补0；%02d 代表2位数字，不足补0
            return String.format("%04d/%02d/%02d-%02d:%02d", year, month, day, hour, minute);
        } catch (NumberFormatException e) {
            // 理论上正则已保证是数字，此异常几乎不会发生，但为了健壮性保留
            return null;
        }
    }

    private static void checkAddParamsIsLegalAndSet(List<String> params){
        if (params.size()!=6){
            myConsole.warn("Admin", "Usage: key add <name> <balance> <expireTime> <port> <rate>");
            return;
        }
        String expireTime=correctInputTime(params.get(3));
        if (expireTime!=null){
            if (!SequenceKey.isOutOfDate(expireTime)){
                boolean isCreated = SequenceKey.createNewKey(params.get(1), Double.parseDouble(params.get(2)), expireTime , Integer.parseInt(params.get(4)), Double.parseDouble(params.get(5)));
                if (isCreated) {
                    myConsole.log("Admin", "Key " + params.get(1) + " now is created !");
                } else {
                    myConsole.error("Admin", "Fail to create key");
                    myConsole.warn("Admin", "Usage: key add <name> <balance> <expireTime> <port> \nexample: key add abcd 500 2030/12/01-13:05 8090");
                }
            }else {
                myConsole.warn("Admin", "The entered time cannot be later than the current time.");
            }
        }else{
            myConsole.warn("Admin", "Illegal time input.");
        }
    }
}
