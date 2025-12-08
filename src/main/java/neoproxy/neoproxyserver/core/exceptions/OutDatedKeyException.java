package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.management.SequenceKey;

public class OutDatedKeyException extends Exception {
    private OutDatedKeyException(String message) {
        super(message);
        ServerLogger.error("exception.outDatedKey.message", message);
    }

    public static void throwException(SequenceKey key) throws OutDatedKeyException {
        String message = ServerLogger.getMessage("exception.outDatedKey.message", key.getName());
        throw new OutDatedKeyException(message);
    }
}