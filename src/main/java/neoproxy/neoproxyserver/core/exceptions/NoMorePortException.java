package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

public class NoMorePortException extends Exception {

    // 构造器私有化
    private NoMorePortException(String message) {
        super(message);
    }

    // 场景 A: 动态端口耗尽 (无参)
    public static void throwException() throws NoMorePortException {
        String message = ServerLogger.getMessage("exception.noMorePort.message");
        ServerLogger.error("exception.noMorePort.message", message);
        throw new NoMorePortException(message);
    }

    // 场景 B: 特定端口被占用 (替代原本的 AlreadyBlindPortException)
    public static void throwException(int port) throws NoMorePortException {
        // 你可以在 messages.properties 里加一个 exception.portLocalOccupied = 本地端口 {0} 已被占用
        // 这里暂时用 warn 直接输出
        ServerLogger.warn("exception.alreadyBlindPort.message", port);
        throw new NoMorePortException("Local port " + port + " is already in use.");
    }

    // 场景 C: 端口范围被占用
    public static void throwException(int start, int end) throws NoMorePortException {
        ServerLogger.warn("exception.alreadyBlindPort.range.message", start, end);
        throw new NoMorePortException("Local port range " + start + "-" + end + " is exhausted.");
    }
}