package neoproxy.neoproxyserver.core.management.provider;

import neoproxy.neoproxyserver.core.exceptions.PortOccupiedException;
import neoproxy.neoproxyserver.core.management.SequenceKey;

public interface KeyDataProvider {
    void init();

    SequenceKey getKey(String name) throws PortOccupiedException;

    void consumeFlow(String name, double mib);

    void releaseKey(String name);

    void shutdown();

    /**
     * 发送心跳包
     *
     * @param payload 心跳载荷
     * @return true=保持连接, false=立即断开(kill)
     */
    boolean sendHeartbeat(Protocol.HeartbeatPayload payload);
}