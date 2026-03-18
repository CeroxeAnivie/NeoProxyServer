package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.management.SequenceKey;

/**
 * 密钥过期异常 - 当访问密钥超过有效期时抛出
 *
 * <p>此异常表示客户端使用的访问密钥已过期，无法继续使用。
 * 密钥过期是正常业务流程，通常发生在：</p>
 * <ul>
 *   <li>订阅套餐到期</li>
 *   <li>临时密钥超过使用期限</li>
 *   <li>管理员手动设置过期时间</li>
 * </ul>
 *
 * <p>与 {@link UnRecognizedKeyException} 的区别：</p>
 * <ul>
 *   <li>密钥存在但已过期 vs 密钥不存在</li>
 *   <li>用户需要续费 vs 需要重新输入密钥</li>
 * </ul>
 *
 * @author Ceroxe
 * @version 6.1.0
 * @since 6.1.0
 * @see Exception
 * @see SequenceKey#isOutOfDate()
 */
public class OutDatedKeyException extends Exception {

    /**
     * 私有构造器，强制使用静态工厂方法
     *
     * @param message 异常消息
     */
    private OutDatedKeyException(String message) {
        super(message);
    }

    /**
     * 抛出密钥过期异常
     *
     * <p>自动从资源文件加载本地化错误消息。</p>
     *
     * @param keyName 过期的密钥名称
     * @throws OutDatedKeyException 总是抛出此异常
     */
    public static void throwException(String keyName) throws OutDatedKeyException {
        String message = ServerLogger.getMessage("exception.outDatedKey.message", keyName);
        throw new OutDatedKeyException(message);
    }
}
