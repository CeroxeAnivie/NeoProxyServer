package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.NeoProxyServer;
import neoproject.neoproxy.core.HostClient;

public class UnSupportHostVersionException extends Exception {
    private UnSupportHostVersionException(String msg) {
        super(msg);
    }

    public static void throwException(String version, HostClient hostClient) throws UnSupportHostVersionException {
        String str = "The host client from " + hostClient.getAddressAndPort() + " try to use an outdated version " + version + " to connect , refuse it...";
        NeoProxyServer.sayInfo(str);
        throw new UnSupportHostVersionException(str);
    }

}
