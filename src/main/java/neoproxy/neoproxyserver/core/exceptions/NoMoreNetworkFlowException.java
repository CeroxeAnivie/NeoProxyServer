package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

/**
 * 流量耗尽异常 - 当访问密钥的流量配额用尽时抛出
 *
 * <p>此异常表示客户端使用的访问密钥已达到其流量限制，无法继续传输数据。
 * 异常抛出时会自动记录错误日志，并包含本地化的错误消息。</p>
 *
 * <p>触发条件：</p>
 * <ul>
 *   <li>密钥的总流量配额已耗尽</li>
 *   <li>密钥的每日/每月流量限制已达到</li>
 *   <li>密钥的流量值设置无效（负数或非数字）</li>
 * </ul>
 *
 * <p>处理建议：</p>
 * <ul>
 *   <li>提示用户升级套餐或购买更多流量</li>
 *   <li>引导用户联系管理员重置流量</li>
 *   <li>记录流量使用模式用于分析</li>
 * </ul>
 *
 * @author Ceroxe
 * @version 6.1.0
 * @since 6.1.0
 * @see RuntimeException
 * @see SequenceKey
 */
public class NoMoreNetworkFlowException extends RuntimeException {

    /**
     * 私有构造器，强制使用静态工厂方法
     *
     * @param message 异常消息
     */
    private NoMoreNetworkFlowException(String message) {
        super(message);
    }

    /**
     * 抛出异常并记录通用日志消息
     *
     * <p>使用默认消息键记录流量耗尽错误。</p>
     *
     * @throws NoMoreNetworkFlowException 总是抛出此异常
     */
    public static void throwException() throws NoMoreNetworkFlowException {
        String message = ServerLogger.getMessage("exception.noMoreNetworkFlow.message");
        ServerLogger.error("exception.noMoreNetworkFlow.message", message);
        throw new NoMoreNetworkFlowException(message);
    }

    /**
     * 抛出异常并记录带来源的日志消息
     *
     * <p>允许指定日志来源和自定义消息键，支持消息参数化。</p>
     *
     * @param source     日志来源标识（如 "SK-Manager"）
     * @param messageKey 消息资源键
     * @param args       消息参数
     * @throws NoMoreNetworkFlowException 总是抛出此异常
     */
    public static void throwException(String source, String messageKey, Object... args) throws NoMoreNetworkFlowException {
        ServerLogger.errorWithSource(source, messageKey, args);
        String formattedMessage = ServerLogger.getMessage(messageKey, args);
        throw new NoMoreNetworkFlowException(formattedMessage);
    }
}
