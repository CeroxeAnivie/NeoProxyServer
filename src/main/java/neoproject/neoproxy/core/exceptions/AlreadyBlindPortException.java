package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.core.ServerLogger;

public class AlreadyBlindPortException extends Exception {
    private AlreadyBlindPortException(int port) {
        super(String.valueOf(port));
    }

    private AlreadyBlindPortException(int startPort, int endPort) {
        super(startPort + "-" + endPort);
    }

    public static void throwException(int port) throws AlreadyBlindPortException {
        ServerLogger.warn("exception.alreadyBlindPort.message", port);
        throw new AlreadyBlindPortException(port);
    }

    public static void throwException(int startPort, int endPort) throws AlreadyBlindPortException {
        ServerLogger.warn("exception.alreadyBlindPort.range.message", startPort, endPort);
        throw new AlreadyBlindPortException(startPort, endPort);
    }
}