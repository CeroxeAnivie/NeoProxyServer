package neoproxy.neoproxyserver.core.management;

import fun.ceroxe.api.net.SecureServerSocket;
import fun.ceroxe.api.net.SecureSocket;
import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.Debugger;
import neoproxy.neoproxyserver.core.HostReply;
import neoproxy.neoproxyserver.core.ServerLogger;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static neoproxy.neoproxyserver.NeoProxyServer.isStopped;
import static neoproxy.neoproxyserver.core.Debugger.debugOperation;
import static neoproxy.neoproxyserver.core.InternetOperator.close;

public class TransferSocketAdapter implements Runnable {

    // 高并发 map：存放尚未被领取的 host replies（key = socketID）
    private static final ConcurrentHashMap<Long, HostReply> tcpHostReply = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, HostReply> udpHostReply = new ConcurrentHashMap<>();

    // 等待方映射：当调用 getHostReply 但还没到 host 时，会放一个 CompletableFuture 在这里
    private static final ConcurrentHashMap<Long, CompletableFuture<HostReply>> tcpWaiting = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, CompletableFuture<HostReply>> udpWaiting = new ConcurrentHashMap<>();

    // DelayQueue 管理过期项（单一清理线程消费）
    private static final DelayQueue<DelayedEntry> delayQueue = new DelayQueue<>();

    // 清理线程池（单线程）+ 接受处理线程池（短任务、IO 之后即返回）
    private static final ScheduledExecutorService cleanerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "TransferSocketAdapter-Cleaner");
        t.setDaemon(true);
        return t;
    });

    private static final ExecutorService acceptHandlerPool = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("TransferSocketAdapter-Handler-", 0).factory()
    );

    private static final AtomicBoolean cleanerStarted = new AtomicBoolean(false);

    public static int SO_TIMEOUT = 5000; // 毫秒

    public static void startThread() {
        // 启动 accept 主线程
        new Thread(new TransferSocketAdapter(), "TransferSocketAdapter-Acceptor").start();
        // 启动清理线程（保证只启动一次）
        if (cleanerStarted.compareAndSet(false, true)) {
            cleanerExecutor.execute(TransferSocketAdapter::cleanerLoop);
        }
    }

    private static void cleanerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                DelayedEntry entry = delayQueue.take(); // 阻塞直到有到期项

                ConcurrentHashMap<Long, HostReply> map =
                        entry.isTcp ? tcpHostReply : udpHostReply;

                // 若还能移除，说明没人领取 -> 超时真实发生，需要关闭 socket
                HostReply removed = map.remove(entry.socketID);
                if (removed != null) {
                    Debugger.debugOperation("TransferSocket Cleaner: Cleaning up stale socket ID: " + entry.socketID);
                    try {
                        close(removed.host());
                    } catch (Exception ex) {
                        debugOperation(ex);
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                debugOperation(e);
            }
        }
    }

    public static HostReply getHostReply(long socketID, int CONN_TYPE) throws SocketTimeoutException {
        // Debugger.debugOperation("Waiting for HostReply ID: " + socketID + " Type: " + CONN_TYPE);

        final ConcurrentHashMap<Long, HostReply> map =
                (CONN_TYPE == TransferSocketAdapter.CONN_TYPE.TCP) ? tcpHostReply :
                        (CONN_TYPE == TransferSocketAdapter.CONN_TYPE.UDP) ? udpHostReply : null;

        final ConcurrentHashMap<Long, CompletableFuture<HostReply>> waiting =
                (CONN_TYPE == TransferSocketAdapter.CONN_TYPE.TCP) ? tcpWaiting :
                        (CONN_TYPE == TransferSocketAdapter.CONN_TYPE.UDP) ? udpWaiting : null;

        if (map == null || waiting == null) return null;

        // 1) 先尝试直接领取（常走路径）
        HostReply existing = map.remove(socketID);
        if (existing != null) {
            Debugger.debugOperation("HostReply acquired immediately: " + socketID);
            return existing;
        }

        // 2) 若没有，则注册等待者
        CompletableFuture<HostReply> cf = new CompletableFuture<>();
        CompletableFuture<HostReply> prev = waiting.putIfAbsent(socketID, cf);
        if (prev != null) {
            cf = prev;
        }

        // 再次尝试抢占（race condition 处理）
        existing = map.remove(socketID);
        if (existing != null) {
            waiting.remove(socketID, cf);
            Debugger.debugOperation("HostReply acquired (race win): " + socketID);
            return existing;
        }

        // 3) 等待完成或超时
        try {
            // 成功被 accept 侧 complete，返回
            return cf.get(SO_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            waiting.remove(socketID, cf);
            Debugger.debugOperation("Timeout waiting for HostReply: " + socketID);
            throw new SocketTimeoutException();
        } catch (InterruptedException ie) {
            waiting.remove(socketID, cf);
            Thread.currentThread().interrupt();
            throw new SocketTimeoutException();
        } catch (ExecutionException ee) {
            waiting.remove(socketID, cf);
            debugOperation(ee);
            throw new SocketTimeoutException();
        }
    }

    @Override
    public void run() {
        try {
            NeoProxyServer.hostServerTransferServerSocket = new SecureServerSocket(NeoProxyServer.HOST_CONNECT_PORT);
        } catch (IOException e) {
            debugOperation(e);
            ServerLogger.errorWithSource("TransferSocketAdapter", "transferSocketAdapter.bindPortFailed");
            System.exit(-1);
        }

        while (!isStopped) {
            SecureSocket host;
            try {
                host = NeoProxyServer.hostServerTransferServerSocket.accept();
            } catch (IOException e) {
                if (isStopped) break;
                debugOperation(e);
                continue;
            }

            final SecureSocket accepted = host;
            acceptHandlerPool.submit(() -> {
                try {
                    String[] flags = accepted.receiveStr(SO_TIMEOUT).split(";");
                    Debugger.debugOperation("TransferSocket received flags: " + String.join(";", flags));

                    accepted.setSoTimeout(0);//保险起见再调回去
                    String connectionType = flags[0];
                    long socketID = Long.parseLong(flags[1]);

                    HostReply newReply = new HostReply(socketID, accepted);

                    boolean isTcp = "TCP".equals(connectionType);
                    boolean isUdp = "UDP".equals(connectionType);

                    if (!isTcp && !isUdp) {
                        Debugger.debugOperation("Invalid TransferSocket connection type: " + connectionType);
                        close(accepted);
                        return;
                    }

                    ConcurrentHashMap<Long, CompletableFuture<HostReply>> waiting =
                            isTcp ? tcpWaiting : udpWaiting;
                    ConcurrentHashMap<Long, HostReply> map =
                            isTcp ? tcpHostReply : udpHostReply;

                    // 优先尝试唤醒等待方（避免不必要的放入 map）
                    CompletableFuture<HostReply> waitCf = waiting.remove(socketID);
                    if (waitCf != null) {
                        Debugger.debugOperation("TransferSocket matched waiting thread: " + socketID);
                        waitCf.complete(newReply);
                        return;
                    }

                    // 无等待方：把 hostReply 放入 map，并放入 DelayQueue 管理超时
                    Debugger.debugOperation("TransferSocket queued (no waiter): " + socketID);
                    map.put(socketID, newReply);

                    DelayedEntry entry = new DelayedEntry(socketID, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SO_TIMEOUT), isTcp);
                    entry.attachHostReply(newReply);
                    delayQueue.offer(entry);

                } catch (Exception e) {
                    debugOperation(e);
                    close(accepted);
                }
            });
        }

        acceptHandlerPool.shutdown();
        cleanerExecutor.shutdown();
    }

    private static class DelayedEntry implements Delayed {
        final long socketID;
        final long triggerTimeNanos;
        final boolean isTcp;
        private volatile HostReply hostReply;

        DelayedEntry(long socketID, long triggerTimeNanos, boolean isTcp) {
            this.socketID = socketID;
            this.triggerTimeNanos = triggerTimeNanos;
            this.isTcp = isTcp;
        }

        void attachHostReply(HostReply hr) {
            this.hostReply = hr;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = triggerTimeNanos - System.nanoTime();
            return unit.convert(diff, TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            if (o == this) return 0;
            if (o instanceof DelayedEntry other) {
                return Long.compare(this.triggerTimeNanos, other.triggerTimeNanos);
            }
            long d = this.getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS);
            return (d == 0) ? 0 : ((d < 0) ? -1 : 1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DelayedEntry that)) return false;
            return socketID == that.socketID && isTcp == that.isTcp;
        }

        @Override
        public int hashCode() {
            return Objects.hash(socketID, isTcp);
        }
    }

    public static class CONN_TYPE {
        public static final int TCP = 0;
        public static final int UDP = 1;
    }
}