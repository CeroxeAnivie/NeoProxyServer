package neoproject.neoproxy.core;

import java.io.Serializable;

public class LanguageData implements Serializable {
    public String THE_PORT_HAS_ALREADY_BLIND = "This port is already occupied.";
    public String IF_YOU_SEE_EULA = "If you use this software, you understand and agree with eula .";
    public String VERSION = "Version : ";
    public String PLEASE_ENTER_ACCESS_CODE = "Please enter the access code:";
    public String CONNECT_TO = "Connect to ";
    public String OMITTED = " ...";
    public String A_CONNECTION = "A connection to ";
    public String BUILD_UP = " build up";
    public String ENTER_PORT_MSG = "Enter the port for which you want to penetrate the intranet:";
    public String USE_THE_ADDRESS = "Use the address: ";
    public String TO_START_UP_CONNECTION = " to start up connections.";
    public String CONNECTION_BUILD_UP_SUCCESSFULLY = "Connection build up successfully";
    public String FAIL_TO_BUILD_A_CHANNEL_FROM = "Fail to build a channel from ";
    public String DESTROY = " destroyed";
    public String FAIL_TO_CONNECT_LOCALHOST = "Fail to connect to 127.0.0.1:";
    public String THE_VAULT = "The vault ";
    public String ARE_OUT_OF_DATE = " are out of date.";
    public String THIS_ACCESS_CODE_HAVE = "This access code have ";
    public String MB_IF_FLOW_LEFT = " MB of network flow left";
    public String YOU_HAVE_NO_NETWORK_FLOW_LEFT = "You have no network flow left ! Force exiting...";
    public String ACCESS_DENIED_FORCE_EXITING = "Access denied , force exiting...";
    public String EXPIRE_AT = "This serial number will expire at ";
    public String DETECTED_VERSION = "Detected host client's version ";
    public String UNSUPPORTED_VERSION_MSG = "Unsupported version ! It should be :";
    private String currentLanguage = "en";

    public static LanguageData getChineseLanguage() {

        LanguageData languageData = new LanguageData();
        languageData.currentLanguage = "zh";

        languageData.IF_YOU_SEE_EULA = "如果你已经开始使用的本软件，说明你已经知晓并同意了本软件的eula协议";
        languageData.VERSION = "版本 ： ";
        languageData.PLEASE_ENTER_ACCESS_CODE = "请输入序列号：";
        languageData.CONNECT_TO = "连接 ";
        languageData.OMITTED = " ...";
        languageData.A_CONNECTION = "一个 ";
        languageData.BUILD_UP = " 的通道建立";
        languageData.ENTER_PORT_MSG = "请输入你想进行内网穿透的内网端口：";
        languageData.USE_THE_ADDRESS = "使用链接地址： ";
        languageData.TO_START_UP_CONNECTION = " 来从公网连接。";
        languageData.CONNECTION_BUILD_UP_SUCCESSFULLY = "服务器连接成功";
        languageData.FAIL_TO_BUILD_A_CHANNEL_FROM = "连接以下地址失败：";
        languageData.DESTROY = " 的通道关闭";
        languageData.FAIL_TO_CONNECT_LOCALHOST = "连接以下地址失败：127.0.0.1:";

        languageData.THE_VAULT = "这个序列号 ";
        languageData.ARE_OUT_OF_DATE = " 已经过期了。";
        languageData.THIS_ACCESS_CODE_HAVE = "这个序列号有 ";
        languageData.MB_IF_FLOW_LEFT = " MB 流量可以消耗。";
        languageData.YOU_HAVE_NO_NETWORK_FLOW_LEFT = "你已经没有流量了，强制退出。。。";
        languageData.ACCESS_DENIED_FORCE_EXITING = "序列号错误，强制退出。。。";
        languageData.EXPIRE_AT = "有效期至：";
        languageData.DETECTED_VERSION = "检测到客户端版本：";
        languageData.UNSUPPORTED_VERSION_MSG = "不受支持的版本，应该为:";
        languageData.THE_PORT_HAS_ALREADY_BLIND = "这个端口已经被占用了。";

        return languageData;
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }


}
