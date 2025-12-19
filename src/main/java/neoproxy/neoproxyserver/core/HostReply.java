package neoproxy.neoproxyserver.core;

import fun.ceroxe.api.net.SecureSocket;

public record HostReply(long socketID, SecureSocket host) {
}
