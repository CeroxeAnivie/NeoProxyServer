package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.core.ServerLogger;

public class UnRecognizedKeyException extends Exception {
    public UnRecognizedKeyException(String message) {
        super(message);
        ServerLogger.error("UnRecognizedKeyException", "exception.unRecognizedKey.message", message);
    }

    public static void throwException(String key) throws UnRecognizedKeyException {
        String message = ServerLogger.getMessage("exception.unRecognizedKey.message", key);
        throw new UnRecognizedKeyException(message);
    }
}