package neoproject.neoproxy.core;

import plethora.net.SecureSocket;

public record HostReply(int port, SecureSocket host) {
}
