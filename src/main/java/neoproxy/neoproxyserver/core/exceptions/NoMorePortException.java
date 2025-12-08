package neoproxy.neoproxyserver.core.exceptions;

import neoproxy.neoproxyserver.core.ServerLogger;

public class NoMorePortException extends Exception {
    private NoMorePortException(String message) {
        super(message);
        ServerLogger.error("exception.noMorePort.message", message);
    }

    public static void throwException() throws NoMorePortException {
        String message = ServerLogger.getMessage("exception.noMorePort.message");
        throw new NoMorePortException(message);
    }
}