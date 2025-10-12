package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.NeoProxyServer;

public class AlreadyBlindPortException extends Exception {
    private AlreadyBlindPortException(int port) {
        super(String.valueOf(port));
    }

    private AlreadyBlindPortException(int startPort, int endPort) {
        super(startPort + "-" + endPort);
    }

    public static void throwException(int port) throws AlreadyBlindPortException {
        NeoProxyServer.sayInfo("The port " + port + " has already blind !");
        throw new AlreadyBlindPortException(port);
    }

    public static void throwException(int startPort, int endPort) throws AlreadyBlindPortException {
        NeoProxyServer.sayInfo("There are no available port between " + startPort + " and " + endPort + " !");
        throw new AlreadyBlindPortException(startPort, endPort);
    }
}
