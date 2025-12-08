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
        // 本地模式不需要显式释放 Session，因为没有中心控制
    }

    @Override
    public void consumeFlow(String name, double mib) {
        SequenceKey.updateBalanceInDB(name, mib);
    }

    @Override
    public void shutdown() {
    }
}