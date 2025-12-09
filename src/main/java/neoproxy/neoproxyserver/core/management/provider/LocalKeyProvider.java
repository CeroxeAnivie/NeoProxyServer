package neoproxy.neoproxyserver.core.management.provider;

import neoproxy.neoproxyserver.core.management.SequenceKey;

public class LocalKeyProvider implements KeyDataProvider {

    @Override
    public void init() {
        SequenceKey.initKeyDatabase();
    }

    @Override
    public SequenceKey getKey(String name) {
        return SequenceKey.loadKeyFromDatabase(name, false);
    }

    @Override
    public void releaseKey(String name) {
        // Local mode implies manual management, no release needed usually
    }

    @Override
    public void consumeFlow(String name, double mib) {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public boolean sendHeartbeat(Protocol.HeartbeatPayload payload) {
        // 本地模式始终允许连接
        return true;
    }
}