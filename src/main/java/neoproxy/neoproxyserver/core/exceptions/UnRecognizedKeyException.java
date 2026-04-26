package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

import static neoproxy.neoproxyserver.core.ServerLogger.alert;

/**
 * 密钥未识别异常 - 当客户端提供的访问密钥无效时抛出
 *
 * <p>此异常表示客户端尝试使用一个不存在或已被删除的访问密钥连接服务器。
 * 这通常意味着：</p>
 * <ul>
 *   <li>用户输入了错误的密钥</li>
 *   <li>密钥已被管理员删除</li>
 *   <li>密钥数据库损坏或同步问题</li>
 * </ul>
 *
 * <p>安全考虑：</p>
 * <ul>
 *   <li>此类错误可能暗示暴力破解尝试</li>
 *   <li>建议记录客户端IP用于安全分析</li>
 *   <li>可考虑实施速率限制防止暴力破解</li>
 * </ul>
 *
 * @author Ceroxe
 * @version 6.1.0
 * @see Exception
 * @see SequenceKey
 * @since 6.1.0
 */
public class UnRecognizedKeyException extends Exception {

    /**
     * 私有构造器，强制使用静态工厂方法
     *
     * <p>构造函数内不记录日志，由静态方法统一管理。</p>
     *
     * @param message 异常消息
     */
    private UnRecognizedKeyException(String message) {
        super(message);
    }

    /**
     * 抛出异常并记录安全日志
     *
     * <p>当 alert 启用时，会记录详细的错误来源信息。</p>
     *
     * @param key 导致异常的密钥值
     * @throws UnRecognizedKeyException 总是抛出此异常
     */
    public static void throwException(String key) throws UnRecognizedKeyException {
        if (alert) {
            ServerLogger.errorWithSource("NeoProxyServer", "exception.unRecognizedKey.message", key);
        }
        String message = ServerLogger.getMessage("exception.unRecognizedKey.message", key);
        throw new UnRecognizedKeyException(message);
    }
}
