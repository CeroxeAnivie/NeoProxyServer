package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

import java.io.IOException;

/**
 * 非法网站访问异常 - 当客户端尝试访问被禁止的内容时抛出
 *
 * <p>此异常用于实现内容过滤和访问控制功能。当客户端尝试访问：</p>
 * <ul>
 *   <li>被列入黑名单的网站</li>
 *   <li>包含违规内容的页面</li>
 *   <li>被管理员禁止的特定URL</li>
 * </ul>
 *
 * <p>安全策略：</p>
 * <ul>
 *   <li>记录访问尝试用于审计</li>
 *   <li>可配置自动阻断客户端</li>
 *   <li>支持正则表达式匹配违规内容</li>
 * </ul>
 *
 * <p><strong>注意：</strong>此异常继承自 IOException，可在网络层统一处理。</p>
 *
 * @author Ceroxe
 * @version 6.1.0
 * @since 6.1.0
 * @see IOException
 */
public class IllegalWebSiteException extends IOException {

    /**
     * 私有构造器，强制使用静态工厂方法
     *
     * @param message 异常消息
     */
    private IllegalWebSiteException(String message) {
        super(message);
    }

    /**
     * 抛出非法网站访问异常
     *
     * <p>自动记录安全日志，来源标识为 "WEB-CHECKER"。</p>
     *
     * @param accessCode 违规访问的标识码（如URL或域名）
     * @throws IllegalWebSiteException 总是抛出此异常
     */
    public static void throwException(String accessCode) throws IllegalWebSiteException {
        ServerLogger.warnWithSource("WEB-CHECKER", "exception.illegalWebSite.message", accessCode);
        String message = ServerLogger.getMessage("exception.illegalWebSite.message", accessCode);
        throw new IllegalWebSiteException(message);
    }
}
