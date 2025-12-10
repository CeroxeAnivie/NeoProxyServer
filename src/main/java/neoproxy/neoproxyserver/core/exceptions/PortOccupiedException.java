package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

public class PortOccupiedException extends Exception {

    // 私有构造器，强制使用静态方法抛出
    public PortOccupiedException(String key) {
        super(ServerLogger.getMessage("exception.portOccupied.message"));
        // 记录错误日志，使用特定的 key
        ServerLogger.errorWithSource("NKM->" + key, "exception.portOccupied.message");
    }

    // 静态抛出方法
    public static void throwException(String key) throws PortOccupiedException {
        throw new PortOccupiedException(key);
    }
}