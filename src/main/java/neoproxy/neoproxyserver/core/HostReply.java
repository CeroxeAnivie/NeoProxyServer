package neoproxy.neoproxyserver.core;

import plethora.net.SecureSocket;

public record HostReply(long socketID, SecureSocket host) {
}
