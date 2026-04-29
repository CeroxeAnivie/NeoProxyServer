package neoproxy.neoproxyserver.core;

import top.ceroxe.api.net.SecureSocket;

public record HostReply(long socketID, SecureSocket host) {
}
