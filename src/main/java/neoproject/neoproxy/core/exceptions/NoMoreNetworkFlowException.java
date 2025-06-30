package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.NeoProxyServer;

public class NoMoreNetworkFlowException extends Exception {
    private NoMoreNetworkFlowException(String msg) {
        super(msg);
    }

    public static void throwException(String accessCode) throws NoMoreNetworkFlowException {
        String str = "The access code " + accessCode + " network flow now is zero !";
        NeoProxyServer.sayInfo(str);
        throw new NoMoreNetworkFlowException(str);
    }
}
