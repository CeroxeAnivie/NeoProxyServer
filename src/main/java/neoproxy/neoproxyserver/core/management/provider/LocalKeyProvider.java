package neoproxy.neoproxyserver.core.management.provider;

import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.management.SequenceKey;
import plethora.thread.ThreadManager;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 工业级本地 Provider
 * 采用 "Dirty Flag" 机制，后台线程定期刷盘，解决高并发写入问题。
 */
public class LocalKeyProvider implements KeyDataProvider {

    private static final long FLUSH_INTERVAL_SECONDS = 5;
    private final Set<String> dirtyKeys = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Local-DB-Flush-Thread");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean isFlushing = new AtomicBoolean(false);

    @Override
    public void init() {
        SequenceKey.initKeyDatabase();
        scheduler.scheduleAtFixedRate(this::flushDirtyKeys, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        // Log: LocalKeyProvider initialized. Flush interval: {0}s
        ServerLogger.info("localProvider.init", FLUSH_INTERVAL_SECONDS);
    }

    @Override
    public SequenceKey getKey(String name) {
        return SequenceKey.loadKeyFromDatabase(name, false);
    }

    @Override
    public void consumeFlow(String name, double mib) {
        if (mib > 0) {
            dirtyKeys.add(name);
        }
    }

    private void flushDirtyKeys() {
        if (dirtyKeys.isEmpty() || !isFlushing.compareAndSet(false, true)) {
            return;
        }

        try {
            Iterator<String> it = dirtyKeys.iterator();
            while (it.hasNext()) {
                String name = it.next();
                try {
                    SequenceKey key = SequenceKey.getKeyFromDB(name);
                    if (key != null) {
                        SequenceKey.saveToDB(key);
                    }
                    it.remove();
                } catch (Exception e) {
                    // Log: Failed to flush key {0} to database: {1}
                    ServerLogger.error("localProvider.flushError", e, name);
                }
            }
        } finally {
            isFlushing.set(false);
        }
    }

    @Override
    public void releaseKey(String name) {
        dirtyKeys.add(name);
        ThreadManager.runAsync(this::flushDirtyKeys);
    }

    @Override
    public void shutdown() {
        scheduler.shutdown();
        flushDirtyKeys();
    }

    @Override
    public boolean sendHeartbeat(Protocol.HeartbeatPayload payload) {
        return true;
    }
}