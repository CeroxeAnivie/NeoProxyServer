package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

/**
 * 端口耗尽异常 - 当无法分配可用端口时抛出
 *
 * <p>此异常表示服务器无法为客户端分配请求的端口，可能原因包括：</p>
 * <ul>
 *   <li>动态端口池已耗尽（所有可用端口都被占用）</li>
 *   <li>请求的特定端口已被其他服务占用</li>
 *   <li>请求的端口范围已满</li>
 * </ul>
 *
 * <p>端口分配策略：</p>
 * <ul>
 *   <li>动态端口：从配置的范围内自动分配</li>
 *   <li>静态端口：客户端指定特定端口</li>
 *   <li>端口范围：为密钥分配一段连续端口</li>
 * </ul>
 *
 * @author Ceroxe
 * @version 6.1.0
 * @since 6.1.0
 * @see Exception
 */
public class NoMorePortException extends Exception {

    /**
     * 私有构造器，强制使用静态工厂方法
     *
     * @param message 异常消息
     */
    private NoMorePortException(String message) {
        super(message);
    }

    /**
     * 抛出动态端口耗尽异常
     *
     * <p>当动态端口池中没有可用端口时调用。</p>
     *
     * @throws NoMorePortException 总是抛出此异常
     */
    public static void throwException() throws NoMorePortException {
        String message = ServerLogger.getMessage("exception.noMorePort.message");
        ServerLogger.error("exception.noMorePort.message", message);
        throw new NoMorePortException(message);
    }

    /**
     * 抛出特定端口被占用异常
     *
     * <p>当客户端请求的特定端口已被占用时调用。</p>
     *
     * @param port 被占用的端口号
     * @throws NoMorePortException 总是抛出此异常
     */
    public static void throwException(int port) throws NoMorePortException {
        ServerLogger.warn("exception.alreadyBlindPort.message", port);
        throw new NoMorePortException("Local port " + port + " is already in use.");
    }

    /**
     * 抛出端口范围耗尽异常
     *
     * <p>当请求的端口范围内没有可用端口时调用。</p>
     *
     * @param start 范围起始端口
     * @param end   范围结束端口
     * @throws NoMorePortException 总是抛出此异常
     */
    public static void throwException(int start, int end) throws NoMorePortException {
        ServerLogger.warn("exception.alreadyBlindPort.range.message", start, end);
        throw new NoMorePortException("Local port range " + start + "-" + end + " is exhausted.");
    }
}
