package neoproxy.neoproxyserver.core.exceptions;

/**
 * 阻塞消息异常 - 用于向客户端发送阻塞性错误消息
 *
 * <p>与普通异常不同，此异常设计用于：</p>
 * <ul>
 *   <li>向客户端显示关键错误信息</li>
 *   <li>阻断当前操作流程</li>
 *   <li>携带自定义消息内容</li>
 * </ul>
 *
 * <p>使用场景：</p>
 * <ul>
 *   <li>需要向用户显示特定错误提示</li>
 *   <li>业务流程需要强制中断并通知用户</li>
 *   <li>错误消息需要国际化支持</li>
 * </ul>
 *
 * <p><strong>注意：</strong>此异常的消息可通过 {@link #getCustomMessage()} 获取，
 * 用于向客户端展示。</p>
 *
 * @author Ceroxe
 * @version 6.1.0
 * @since 6.1.0
 * @see Exception
 */
public class BlockingMessageException extends Exception {

    /** 自定义消息内容 */
    private final String customMessage;

    /**
     * 构造器
     *
     * @param customMessage 自定义错误消息
     */
    public BlockingMessageException(String customMessage) {
        super(customMessage);
        this.customMessage = customMessage;
    }

    /**
     * 获取自定义消息
     *
     * @return 自定义错误消息
     */
    public String getCustomMessage() {
        return customMessage;
    }
}
