package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.core.HostClient;
import neoproject.neoproxy.core.ServerLogger;

public class UnSupportHostVersionException extends Exception {
    public UnSupportHostVersionException(String message) {
        super(message);
        ServerLogger.error("UnSupportHostVersionException", "exception.unSupportHostVersion.message", message);
    }

    public static void throwException(String version, HostClient hostClient) throws UnSupportHostVersionException {
        String message = ServerLogger.getMessage("exception.unSupportHostVersion.message", version);
        throw new UnSupportHostVersionException(message);
    }
}