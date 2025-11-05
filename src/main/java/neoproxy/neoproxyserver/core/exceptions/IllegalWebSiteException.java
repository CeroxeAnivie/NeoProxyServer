package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

/**
 * 当客户端尝试访问被禁止的网页内容时抛出此异常。
 */
public class IllegalWebSiteException extends Exception {
    private IllegalWebSiteException(String message) {
        super(message);
    }

    public static void throwException(String accessCode) throws IllegalWebSiteException {
        // 使用 warnWithSource 来记录警告，并指定来源为 "WEB-CHECKER"
        ServerLogger.warnWithSource("WEB-CHECKER", "exception.illegalWebSite.message", accessCode);
        String message = ServerLogger.getMessage("exception.illegalWebSite.message", accessCode);
        throw new IllegalWebSiteException(message);
    }
}