package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

public class PortOccupiedException extends Exception {

    // 私有构造器，强制使用静态方法抛出
    public PortOccupiedException(String key) {
        super(ServerLogger.getMessage("exception.portOccupied.message"));
        ServerLogger.errorWithSource("NKM->" + key, "exception.portOccupied.message");
    }

    // 静态抛出方法
    //这个是请求的端口数量超过了在序列号中定义的端口数量，就会抛出，无论是任何节点，由 NKM 负责检查
    public static void throwException(String key) throws PortOccupiedException {
        throw new PortOccupiedException(key);
    }
}