package neoproxy.neoproxyserver.core;

import neoproxy.neoproxyserver.core.exceptions.NoMoreNetworkFlowException;
import plethora.management.bufferedFile.SizeCalculator;
import plethora.net.SecureSocket;

import java.io.Closeable;
import java.io.IOException;
import java.net.*;

import static neoproxy.neoproxyserver.NeoProxyServer.debugOperation;

public final class InternetOperator {
    public static final String COMMAND_PREFIX = ":>";

    private InternetOperator() {
    }

    public static void sendStr(HostClient hostClient, String str) throws IOException {
        int length = hostClient.getHostServerHook().sendStr(str);

        if (hostClient.getKey() != null) {
            try {
                hostClient.getKey().mineMib("InternetOperator", SizeCalculator.byteToMib(length));
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

    public static void shutdownOutput(SecureSocket secureSocket) {
        try {
            secureSocket.shutdownOutput();
        } catch (Exception ignore) {
        }
    }

    public static void shutdownInput(SecureSocket secureSocket) {
        try {
            secureSocket.shutdownInput();
        } catch (Exception ignore) {
        }
    }

    public static void shutdownOutput(Socket socket) {
        try {
            socket.shutdownOutput();
        } catch (Exception ignore) {
        }
    }

    public static void shutdownInput(Socket socket) {
        try {
            socket.shutdownInput();
        } catch (Exception ignore) {
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

    public static String getInternetAddressAndPort(DatagramPacket packet) {
        return packet.getAddress().getHostAddress() + ":" + packet.getPort();
    }

    public static String getIP(SecureSocket socket) {
        return socket.getInetAddress().toString().replaceAll("/", "");
    }

    public static boolean isTCPAvailable(int port) {
        try {
            ServerSocket testSocket = new ServerSocket();
            testSocket.bind(new InetSocketAddress(port), 0);
            testSocket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isUDPAvailable(int port) {
        try {
            DatagramSocket datagramSocket = new DatagramSocket(port);
            datagramSocket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

}
