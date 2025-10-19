package neoproject.neoproxy.core;

import java.net.Socket;

import static neoproject.neoproxy.NeoProxyServer.sayInfo;

public class InfoBox {
    public static boolean alert = true;

    public static void sayHostClientDiscInfo(HostClient hostClient, String subject) {
        sayInfo(subject, "Detected hostClient on " + hostClient.getAddressAndPort() + " has been disconnected !");
    }

    public static void sayClientConnectBuildUpInfo(HostClient hostClient, Socket client) {
        if (alert) {
            sayInfo("Connection: " + InternetOperator.getInternetAddressAndPort(client) + " -> " + hostClient.getAddressAndPort() + " build up !");
        }
    }

    public static void sayClientConnectDestroyInfo(HostClient hostClient, Socket client) {
        if (alert) {
            sayInfo("Connection: " + InternetOperator.getInternetAddressAndPort(client) + " -> " + hostClient.getAddressAndPort() + " destroyed !");
        }
    }

    public static void sayClientSuccConnectToChaSerButHostClientTimeOut(HostClient hostClient) {
        if (alert) {
            sayInfo("A client successfully connect to the channel server but host client from " + hostClient.getAddressAndPort() + " time out.");
        }
    }

    public static void sayKillingClientSideConnection(Socket client) {
        if (alert) {
            sayInfo("Killing client's side connection: " + InternetOperator.getInternetAddressAndPort(client));
        }
    }

    public static void sayHostClientTryToConnect(String address, int port) {
        if (alert) {
            sayInfo("HostClient from " + address + ":" + port + " try to connect!");
        }
    }

    public static void sayAHostClientTryToConnectButFail() {
        if (alert) {
            sayInfo("A host client try to connect but fail.");
        }
    }

    public static void sayBanConnectInfo(String clientAddress) {
        if (alert) {
            sayInfo("Connection from the banned IP: "+clientAddress+" has been rejected");
        }
    }
}
