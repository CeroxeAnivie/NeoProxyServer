package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.NeoProxyServer;

public class UnRecognizedKeyException extends Exception {
    private UnRecognizedKeyException(String msg) {
        super(msg);
    }

    public static void throwException(String keyCode) throws UnRecognizedKeyException {
        String str = "The access code " + keyCode + " could not find in DB , or it's disabled.";
        NeoProxyServer.sayInfo(str);
        throw new UnRecognizedKeyException(str);
    }
}
