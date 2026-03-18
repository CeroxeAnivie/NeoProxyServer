package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

import static neoproxy.neoproxyserver.core.ServerLogger.alert;

/**
 * 客户端版本不支持异常 - 当客户端版本与服务器不兼容时抛出
 *
 * <p>此异常表示连接的客户端使用了服务器不支持的版本，可能原因：</p>
 * <ul>
 *   <li>客户端版本过旧，需要升级</li>
 *   <li>客户端版本过新，服务器尚未支持</li>
 *   <li>协议版本不匹配</li>
 * </ul>
 *
 * <p>版本管理策略：</p>
 * <ul>
 *   <li>服务器维护支持的版本列表</li>
 *   <li>客户端连接时进行版本协商</li>
 *   <li>不兼容版本被拒绝连接</li>
 * </ul>
 *
 * @author Ceroxe
 * @version 6.1.0
 * @since 6.1.0
 * @see Exception
 */
public class UnSupportHostVersionException extends Exception {

    /**
     * 私有构造器
     *
     * <p>当 alert 启用时记录版本不匹配信息。</p>
     *
     * @param ip      客户端IP地址
     * @param version 客户端版本号
     */
    private UnSupportHostVersionException(String ip, String version) {
        if (alert) {
            ServerLogger.errorWithSource("VER-CHECKER", "exception.unSupportHostVersion.message", ip, version);
        }
    }

    /**
     * 抛出版本不支持异常
     *
     * @param ip      客户端IP地址
     * @param version 客户端版本号
     * @throws UnSupportHostVersionException 总是抛出此异常
     */
    public static void throwException(String ip, String version) throws UnSupportHostVersionException {
        throw new UnSupportHostVersionException(ip, version);
    }
}
