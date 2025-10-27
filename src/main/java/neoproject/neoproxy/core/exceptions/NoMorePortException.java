package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.NeoProxyServer;

public class NoMorePortException extends Exception {
    private NoMorePortException(String msg) {
        super(msg);
    }

    public static void throwException() throws NoMorePortException {
        String str = "There are no more dynamic ports available";
        NeoProxyServer.sayInfo(str);
        throw new NoMorePortException(str);
    }
}
