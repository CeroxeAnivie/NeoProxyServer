package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

import static neoproxy.neoproxyserver.core.ServerLogger.alert;

/**
 * 端口配额超限异常 - 当密钥请求的端口数超过配额时抛出
 *
 * <p>此异常与 {@link NoMorePortException} 的区别：</p>
 * <ul>
 *   <li>PortOccupiedException：密钥的端口配额不足（配置问题）</li>
 *   <li>NoMorePortException：系统端口资源不足（资源问题）</li>
 * </ul>
 *
 * <p>触发场景：</p>
 * <ul>
 *   <li>密钥配置了最大2个端口，但请求绑定3个</li>
 *   <li>管理员减少了密钥的端口配额</li>
 *   <li>客户端尝试动态扩展超出配额限制</li>
 * </ul>
 *
 * <p>检查责任：NKM (NeoProxy Key Manager)</p>
 *
 * @author Ceroxe
 * @version 6.1.0
 * @since 6.1.0
 * @see Exception
 * @see NoMorePortException
 */
public class PortOccupiedException extends Exception {

    /**
     * 构造器
     *
     * <p>当 alert 启用时记录详细错误信息。</p>
     *
     * @param key 导致异常的密钥标识
     */
    public PortOccupiedException(String key) {
        super(ServerLogger.getMessage("exception.portOccupied.message"));
        if (alert) {
            ServerLogger.errorWithSource("NKM->" + key, "exception.portOccupied.message");
        }
    }

    /**
     * 抛出端口配额超限异常
     *
     * @param key 导致异常的密钥标识
     * @throws PortOccupiedException 总是抛出此异常
     */
    public static void throwException(String key) throws PortOccupiedException {
        throw new PortOccupiedException(key);
    }
}
