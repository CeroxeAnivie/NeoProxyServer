package neoproxy.neoproxyserver.core.management.provider;

import fun.ceroxe.api.thread.ThreadManager;
import neoproxy.neoproxyserver.core.Debugger;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.management.SequenceKey;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static neoproxy.neoproxyserver.core.Debugger.debugOperation;

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
        Debugger.debugOperation("Initializing LocalKeyProvider...");
        SequenceKey.initKeyDatabase();
        scheduler.scheduleAtFixedRate(this::flushDirtyKeys, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        // Log: LocalKeyProvider initialized. Flush interval: {0}s
        ServerLogger.info("localProvider.init", FLUSH_INTERVAL_SECONDS);
        Debugger.debugOperation("LocalKeyProvider init complete. Flush interval: " + FLUSH_INTERVAL_SECONDS + "s");
    }

    @Override
    public SequenceKey getKey(String name) {
        // Debugger.debugOperation("Local getKey request: " + name);
        return SequenceKey.loadKeyFromDatabase(name, false);
    }

    @Override
    public void consumeFlow(String name, double mib) {
        if (mib > 0) {
            // Debugger.debugOperation("Marking key dirty: " + name);
            dirtyKeys.add(name);
        }
    }

    private void flushDirtyKeys() {
        if (dirtyKeys.isEmpty()) return;

        if (!isFlushing.compareAndSet(false, true)) {
            Debugger.debugOperation("Local flush skipped: Already flushing.");
            return;
        }

        Debugger.debugOperation("Starting local DB flush. Dirty keys: " + dirtyKeys.size());
        try {
            Iterator<String> it = dirtyKeys.iterator();
            while (it.hasNext()) {
                String name = it.next();
                try {
                    SequenceKey key = SequenceKey.getKeyFromDB(name);
                    if (key != null) {
                        SequenceKey.saveToDB(key);
                        // Debugger.debugOperation("Saved key to local DB: " + name);
                    }
                    it.remove();
                } catch (Exception e) {
                    // Log: Failed to flush key {0} to database: {1}
                    debugOperation(e);
                    ServerLogger.error("localProvider.flushError", e, name);
                }
            }
        } finally {
            isFlushing.set(false);
            Debugger.debugOperation("Local DB flush completed.");
        }
    }

    @Override
    public void releaseKey(String name) {
        Debugger.debugOperation("Releasing local key (marking dirty): " + name);
        dirtyKeys.add(name);
        ThreadManager.runAsync(this::flushDirtyKeys);
    }

    @Override
    public void shutdown() {
        Debugger.debugOperation("Shutting down LocalKeyProvider...");
        scheduler.shutdown();
        flushDirtyKeys();
        Debugger.debugOperation("LocalKeyProvider shutdown complete.");
    }

    @Override
    public boolean sendHeartbeat(Protocol.HeartbeatPayload payload) {
        // 本地模式无需远程心跳，直接返回 true 保持在线
        return true;
    }
}