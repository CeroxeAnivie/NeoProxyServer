package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.core.ServerLogger;

public class UnSupportHostVersionException extends Exception {
    public UnSupportHostVersionException(String ip, String version) {
        ServerLogger.errorWithSource("VER-CHECKER", "exception.unSupportHostVersion.message", ip, version);
    }

    public static void throwException(String ip, String version) throws UnSupportHostVersionException {
        throw new UnSupportHostVersionException(ip, version);
    }
}