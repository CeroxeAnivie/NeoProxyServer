package neoproxy.neoproxyserver.core.management.provider;

import neoproxy.neoproxyserver.core.exceptions.NoMorePortException;
import neoproxy.neoproxyserver.core.exceptions.OutDatedKeyException;
import neoproxy.neoproxyserver.core.exceptions.PortOccupiedException;
import neoproxy.neoproxyserver.core.exceptions.UnRecognizedKeyException;
import neoproxy.neoproxyserver.core.management.SequenceKey;

public interface KeyDataProvider {
    void init();

    /**
     * 获取 Key 信息
     * 修改：增加了 OutDatedKeyException, UnRecognizedKeyException 声明
     * 以便处理远程鉴权失败的情况
     */
    SequenceKey getKey(String name) throws PortOccupiedException, NoMorePortException, OutDatedKeyException, UnRecognizedKeyException;

    void consumeFlow(String name, double mib);

    void releaseKey(String name);

    void shutdown();

    boolean sendHeartbeat(Protocol.HeartbeatPayload payload);
}