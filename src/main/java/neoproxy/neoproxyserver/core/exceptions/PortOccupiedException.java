package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

public class PortOccupiedException extends Exception {

    // 私有构造器，强制使用静态方法抛出
    private PortOccupiedException(String message) {
        super(message);
        // 记录错误日志，使用特定的 key
        ServerLogger.error("PortOccupiedException", "exception.portOccupied.message", message);
    }

    // 静态抛出方法
    public static void throwException(String detailInfo) throws PortOccupiedException {
        // 获取基础错误信息 (需要在 messages.properties 中定义，或者直接使用 fallback)
        String baseMsg = ServerLogger.getMessage("exception.portOccupied.message");
        if (baseMsg.startsWith("!!!")) {
            baseMsg = "Remote port occupied or session limit reached.";
        }

        String fullMessage = baseMsg + " [" + detailInfo + "]";
        throw new PortOccupiedException(fullMessage);
    }
}