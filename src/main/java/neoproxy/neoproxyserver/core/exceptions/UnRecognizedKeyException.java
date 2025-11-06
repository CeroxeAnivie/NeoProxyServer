package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

public class UnRecognizedKeyException extends Exception {
    /**
     * 构造函数，仅用于创建异常对象，不记录日志。
     *
     * @param message 异常消息
     */
    private UnRecognizedKeyException(String message) {
        super(message);
        // 注意：构造函数内不记录日志，由静态方法 throwException 负责
    }

    /**
     * 抛出异常并记录日志。
     *
     * @param key 导致异常的密钥
     * @throws UnRecognizedKeyException 总是抛出此异常
     */
    public static void throwException(String key) throws UnRecognizedKeyException {
        // 使用 errorWithSource 来明确指定来源和消息键，避免重载歧义
        ServerLogger.errorWithSource("NeoProxyServer", "exception.unRecognizedKey.message", key);
        String message = ServerLogger.getMessage("exception.unRecognizedKey.message", key);
        throw new UnRecognizedKeyException(message);
    }
}