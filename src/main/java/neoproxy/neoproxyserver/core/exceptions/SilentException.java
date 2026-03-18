package neoproxy.neoproxyserver.core.exceptions;

/**
 * 静默异常 - 用于控制流而非错误处理的特殊异常
 *
 * <p>此异常设计用于在特定场景下中断程序流程，但不需要记录错误日志或显示错误信息。
 * 与普通异常不同，静默异常：</p>
 * <ul>
 *   <li>不携带堆栈跟踪信息（性能优化）</li>
 *   <li>不触发错误日志记录</li>
 *   <li>仅用于内部控制流跳转</li>
 * </ul>
 *
 * <p>典型使用场景：</p>
 * <ul>
 *   <li>连接池资源耗尽时的快速失败</li>
 *   <li>循环中断的替代方案</li>
 *   <li>需要立即终止但非错误的操作</li>
 * </ul>
 *
 * <p><strong>注意：</strong>此异常不应在正常的业务逻辑错误处理中使用，
 * 仅用于特定的性能敏感场景。</p>
 *
 * @author Ceroxe
 * @version 6.1.0
 * @since 6.1.0
 * @see Exception
 */
public class SilentException extends Exception {

    /**
     * 私有构造器，强制使用静态工厂方法
     *
     * <p>不填充堆栈跟踪以提高性能</p>
     */
    private SilentException() {
        super();
    }

    /**
     * 抛出静默异常
     *
     * <p>静态工厂方法，用于统一抛出静默异常。</p>
     *
     * @throws SilentException 总是抛出此异常
     */
    public static void throwException() throws SilentException {
        throw new SilentException();
    }
}
