package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.core.ServerLogger;
import neoproject.neoproxy.core.management.SequenceKey;

public class OutDatedKeyException extends Exception {
    public OutDatedKeyException(String message) {
        super(message);
        ServerLogger.error("OutDatedKeyException", "exception.outDatedKey.message", message);
    }

    public static void throwException(SequenceKey key) throws OutDatedKeyException {
        String message = ServerLogger.getMessage("exception.outDatedKey.message", key.getName());
        throw new OutDatedKeyException(message);
    }
}