package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.core.ServerLogger;

public class NoMorePortException extends Exception {
    public NoMorePortException(String message) {
        super(message);
        ServerLogger.error("NoMorePortException", "exception.noMorePort.message", message);
    }

    public static void throwException() throws NoMorePortException {
        String message = ServerLogger.getMessage("exception.noMorePort.message");
        throw new NoMorePortException(message);
    }
}