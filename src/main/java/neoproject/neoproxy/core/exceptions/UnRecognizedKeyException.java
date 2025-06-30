package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.NeoProxyServer;

public class UnRecognizedKeyException extends Exception {
    private UnRecognizedKeyException(String msg) {
        super(msg);
    }

    public static void throwException(String vaultCode) throws UnRecognizedKeyException {
        String str = "The access code " + vaultCode + " could not find in vault dir.";
        NeoProxyServer.sayInfo(str);
        throw new UnRecognizedKeyException(str);
    }
}
