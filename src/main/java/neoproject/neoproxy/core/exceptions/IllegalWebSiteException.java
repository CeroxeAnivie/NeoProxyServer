package neoproject.neoproxy.core.exceptions;

import static neoproject.neoproxy.NeoProxyServer.myConsole;
import static neoproject.neoproxy.core.InfoBox.alert;

/**
 * 当客户端尝试访问被禁止的网页内容时抛出此异常。
 */
public class IllegalWebSiteException extends Exception {
    private IllegalWebSiteException(String message) {
        super(message);
    }
    public static void throwException(String accessCode) throws IllegalWebSiteException {
        String str = "Access code: " + accessCode + " trying to transfer a web HTML , block it !";
        if (alert){
            myConsole.warn("WEB-CHECKER", str);
        }
        throw new IllegalWebSiteException(str);
    }
}