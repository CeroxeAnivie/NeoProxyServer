package neoproject.neoproxy.core;

import plethora.print.log.LogType;
import plethora.print.log.State;

import java.net.Socket;

import static neoproject.neoproxy.NeoProxyServer.sayInfo;

public class InfoBox {
    public static void sayHostClientDiscInfo(HostClient hostClient, String subject) {
        sayInfo(LogType.INFO, subject, "Detected hostClient on " + hostClient.getAddressAndPort() + " has been disconnected !");
    }

    public static void sayClientConnectBuildUpInfo(HostClient hostClient, Socket client) {
        sayInfo("Connection: " + InternetOperator.getInternetAddressAndPort(client) + " -> " + hostClient.getAddressAndPort() + " build up !");
    }

    public static void sayClientConnectBuildUpInfo(HostClient hostClient, String addressAndPort) {
        sayInfo("Connection: " + addressAndPort + " -> " + hostClient.getAddressAndPort() + " build up !");
    }

    public static void sayClientConnectDestroyInfo(HostClient hostClient, Socket client) {
        sayInfo("Connection: " + InternetOperator.getInternetAddressAndPort(client) + " -> " + hostClient.getAddressAndPort() + " destroyed !");
    }

    public static void sayClientConnectDestroyInfo(HostClient hostClient, String addressAndPort) {
        sayInfo("Connection: " + addressAndPort + " -> " + hostClient.getAddressAndPort() + " destroyed !");
    }

    public static void sayClientSuccConnecToChaSerButHostClientTimeOut(HostClient hostClient) {
        sayInfo("A client successfully connect to the channel server but host client from " + hostClient.getAddressAndPort() + " time out.");
    }
}
