package neoproject.neoproxy.core.exceptions;

import static neoproject.neoproxy.NeoProxyServer.myConsole;

public class NoMoreNetworkFlowException extends Exception {
    private NoMoreNetworkFlowException(String msg) {
        super(msg);
    }

    public static void throwException(String subject, String accessCode) throws NoMoreNetworkFlowException {
        String str = "The access code " + accessCode + " network flow now is zero !";
        myConsole.log(subject, str);
        throw new NoMoreNetworkFlowException(str);
    }
}
