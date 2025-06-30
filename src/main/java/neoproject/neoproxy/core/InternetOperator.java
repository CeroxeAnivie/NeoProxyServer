package neoproject.neoproxy.core;

import neoproject.neoproxy.core.exceptions.NoMoreNetworkFlowException;
import plethora.management.bufferedFile.SizeCalculator;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class InternetOperator {
    public static final String COMMAND_PREFIX = ":>";

    private InternetOperator() {
    }

    public static void sendStr(HostClient hostClient, String str) throws IOException {
        ObjectOutputStream objectOutputStream = hostClient.getWriter();
        byte[] data = hostClient.getAESUtil().encrypt(str.getBytes(StandardCharsets.UTF_8));
        objectOutputStream.writeObject(data);
        objectOutputStream.flush();

        if (hostClient.getVault() != null) {
            try {
                hostClient.getVault().mineMib(SizeCalculator.byteToMib(data.length));
            } catch (NoMoreNetworkFlowException e) {
                hostClient.close();
            }
        }
    }

    public static String receiveStr(HostClient hostClient) throws IOException {
        try {
            ObjectInputStream objectInputStream = hostClient.getReader();
            return new String(hostClient.getAESUtil().decrypt((byte[]) objectInputStream.readObject()), StandardCharsets.UTF_8);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
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
        return socket.getInetAddress().toString().replaceAll("/", "") + ":" + socket.getPort();
    }

    public static String getIP(Socket socket) {
        return socket.getInetAddress().toString().replaceAll("/", "");
    }

}
