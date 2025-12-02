package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

import static neoproxy.neoproxyserver.core.ServerLogger.alert;

public class UnSupportHostVersionException extends Exception {
    private UnSupportHostVersionException(String ip, String version) {
        if (alert) {
            ServerLogger.errorWithSource("VER-CHECKER", "exception.unSupportHostVersion.message", ip, version);
        }
    }

    public static void throwException(String ip, String version) throws UnSupportHostVersionException {
        throw new UnSupportHostVersionException(ip, version);
    }
}