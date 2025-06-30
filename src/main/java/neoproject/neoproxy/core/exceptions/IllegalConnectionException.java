package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.NeoProxyServer;
import neoproject.neoproxy.core.IPChecker;
import neoproject.neoproxy.core.InternetOperator;

import java.net.Socket;

public class IllegalConnectionException extends Exception {
    private IllegalConnectionException(String msg) {
        super(msg);
    }

    public static void throwException(Socket socket) throws IllegalConnectionException {
        if (IPChecker.ENABLE_BAN) {
            NeoProxyServer.sayInfo("The " + InternetOperator.getIP(socket) + " attempted an illegal connection,BAN IT!!!");
            IPChecker.exec(socket, IPChecker.DO_BAN);
        } else {
            NeoProxyServer.sayInfo("The " + InternetOperator.getIP(socket) + " attempted an illegal connection,close it...");
        }
    }
}
