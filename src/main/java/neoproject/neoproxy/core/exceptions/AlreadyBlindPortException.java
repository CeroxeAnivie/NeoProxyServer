package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.NeoProxyServer;

public class AlreadyBlindPortException extends Exception {
    private AlreadyBlindPortException(int port) {
        super(String.valueOf(port));
    }

    public static void throwException(int port) throws AlreadyBlindPortException {
        NeoProxyServer.sayInfo("The port " + port + " has already blind !");
        throw new AlreadyBlindPortException(port);
    }
}
