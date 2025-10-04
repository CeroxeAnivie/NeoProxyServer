package neoproject.neoproxy.core;

import neoproject.neoproxy.core.exceptions.NoMoreNetworkFlowException;
import plethora.management.bufferedFile.SizeCalculator;
import plethora.net.SecureSocket;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;

import static neoproject.neoproxy.NeoProxyServer.debugOperation;

public final class InternetOperator {
    public static final String COMMAND_PREFIX = ":>";

    private InternetOperator() {
    }

    public static void sendStr(HostClient hostClient, String str) throws IOException {
        int length = hostClient.getHostServerHook().sendStr(str);

        if (hostClient.getKey() != null) {
            try {
                hostClient.getKey().mineMib(SizeCalculator.byteToMib(length));
            } catch (NoMoreNetworkFlowException e) {
                hostClient.close();
            }
        }
    }

    public static String receiveStr(HostClient hostClient) {
        try {
            return hostClient.getHostServerHook().receiveStr();
        } catch (Exception e) {
            debugOperation(e);
            return null;
        }
    }

    public static void close(Closeable... as) {

        for (Closeable a : as) {
            try {
                a.close();
            } catch (Exception ignore) {
            }
        }

    }

    public static void sendCommand(HostClient hostClient, String command) {
        try {
            sendStr(hostClient, COMMAND_PREFIX + command);
        } catch (Exception ignored) {
        }
    }

    public static String getInternetAddressAndPort(Socket socket) {
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    public static String getInternetAddressAndPort(SecureSocket socket) {
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    public static String getIP(Socket socket) {
        return socket.getInetAddress().toString().replaceAll("/", "");
    }

}
