package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.core.ServerLogger;

public class NoMoreNetworkFlowException extends Exception {
    public NoMoreNetworkFlowException(String message) {
        super(message);
    }

    /**
     * 抛出异常并记录一个通用的、无参数的日志消息。
     */
    public static void throwException() throws NoMoreNetworkFlowException {
        String message = ServerLogger.getMessage("exception.noMoreNetworkFlow.message");
        ServerLogger.error("NoMoreNetworkFlowException", message);
        throw new NoMoreNetworkFlowException(message);
    }

    /**
     * 抛出异常并记录一个带有来源和消息键的日志。
     * 此方法将使用消息键从资源文件中获取本地化消息。
     *
     * @param source     日志来源（例如 "SK-Manager"）
     * @param messageKey 消息键（例如 "exception.invalidMibValue"）
     * @param args       用于填充消息模板的参数
     */
    public static void throwException(String source, String messageKey, Object... args) throws NoMoreNetworkFlowException {
        // 使用 errorWithSource 和消息键来获取并记录本地化消息
        ServerLogger.errorWithSource(source, messageKey, args);
        String formattedMessage = ServerLogger.getMessage(messageKey, args);
        throw new NoMoreNetworkFlowException(formattedMessage);
    }
}