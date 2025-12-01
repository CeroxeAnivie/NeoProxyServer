package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.NeoProxyServer;
import neoproxy.neoproxyserver.core.HostReply;
import neoproxy.neoproxyserver.core.ServerLogger;
import plethora.net.SecureServerSocket;
import plethora.net.SecureSocket;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static neoproxy.neoproxyserver.NeoProxyServer.debugOperation;
import static neoproxy.neoproxyserver.NeoProxyServer.isStopped;
import static neoproxy.neoproxyserver.core.InternetOperator.close;

/**
 * 极限并发版本：
 * - 使用 ConcurrentHashMap 存储未领取的 HostReply（O(1)）
 * - 使用单一 DelayQueue 统一超时清理（无 per-entry scheduled task）
 * - 使用 waitingMap(CompletableFuture) 将等待方与到达者即时配对（避免轮询）
 * - 低线程、低锁，适合上千万并发场景（受限于机器资源）
 * <p>
 * 语义与原始类等价：getHostReply 仍在超时后抛 SocketTimeoutException；
 * 超时时会关闭对应 HostReply 的 socket。
 */
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

    // small pool to handle post-accept handshake (reading flags, inserting into maps)
    // 使用可伸缩的线程池避免每次 accept 都 new Thread
    private static final ExecutorService acceptHandlerPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "TransferSocketAdapter-AcceptHandler");
        t.setDaemon(true);
        return t;
    });

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

    /**
     * 主清理循环：从 DelayQueue 中取到期的 entry，尝试从对应 map 中移除并关闭 socket。
     * 如果对应 entry 已经被领取（map 中已经没有或被替换），则跳过。
     * <p>
     * 该线程为守护线程，持续运行。
     */
    private static void cleanerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                DelayedEntry entry = delayQueue.take(); // 阻塞直到有到期项

                ConcurrentHashMap<Long, HostReply> map =
                        entry.isTcp ? tcpHostReply : udpHostReply;

                // 若还能移除，说明没人领取 -> 超时真实发生，需要关闭 socket
                HostReply removed = map.remove(entry.socketID);
                if (removed != null) {
                    try {
                        close(removed.host());
                    } catch (Exception ex) {
                        debugOperation(ex);
                    }
                }

                // 若存在等待方（理论上不会有，因为等待方 get 有超时），对方可能已经超时或仍在等，
                // 不对 waiting map 做额外动作；waiting 的 CompletableFuture 会在超时后被移除/取消。
                // 这样可以避免清理线程与等待线程之间复杂的竞争。

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                debugOperation(e);
            }
        }
    }

    /**
     * 高性能阻塞取 HostReply：
     * - 先尝试直接从 map 中移除（O(1)）
     * - 若不存在，则把自己注册为等待者（CompletableFuture），在 accept 端到来时会直接 complete()
     * - 最后用 CompletableFuture#get(timeout) 等待（避免 busy-wait）
     * <p>
     * 保持与原来相同的超时语义：超时抛 SocketTimeoutException
     */
    public static HostReply getHostReply(long socketID, int CONN_TYPE) throws SocketTimeoutException {
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
            return existing;
        }

        // 2) 若没有，则注册等待者
        CompletableFuture<HostReply> cf = new CompletableFuture<>();
        CompletableFuture<HostReply> prev = waiting.putIfAbsent(socketID, cf);
        if (prev != null) {
            // 有其他线程也在等，使用已有的 future（避免覆盖）
            cf = prev;
        }

        // 再次尝试抢占（race condition 处理）
        existing = map.remove(socketID);
        if (existing != null) {
            // 抢到后要移除我们放的 waiting future（如果是我们放的）
            waiting.remove(socketID, cf);
            // 完成可能存在的 future（如果其他线程已经在等待），但既然我们已经获得 HostReply，直接返回
            return existing;
        }

        // 3) 等待完成或超时
        try {
            // 成功被 accept 侧 complete，返回
            return cf.get(SO_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            // 超时：清理 waiting 映射并抛出 SocketTimeoutException
            waiting.remove(socketID, cf);
            throw new SocketTimeoutException();
        } catch (InterruptedException ie) {
            waiting.remove(socketID, cf);
            Thread.currentThread().interrupt();
            throw new SocketTimeoutException();
        } catch (ExecutionException ee) {
            waiting.remove(socketID, cf);
            // 如果 accept 侧发生异常并 completeExceptionally，会进入这里。把异常记录后作为超时/失败处理。
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

            // delegate accept-side handshake to pool (避免每次 new Thread)
            final SecureSocket accepted = host;
            acceptHandlerPool.submit(() -> {
                try {
                    String[] flags = accepted.receiveStr().split(";");
                    String connectionType = flags[0];
                    long socketID = Long.parseLong(flags[1]);

                    HostReply newReply = new HostReply(socketID, accepted);

                    boolean isTcp = "TCP".equals(connectionType);
                    boolean isUdp = "UDP".equals(connectionType);

                    if (!isTcp && !isUdp) {
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
                        // 有等待方，直接完成 future（唤醒等待线程）
                        waitCf.complete(newReply);
                        // 不放入 map，也不加入 delayQueue，因为已经被领取
                        return;
                    }

                    // 无等待方：把 hostReply 放入 map，并放入 DelayQueue 管理超时
                    map.put(socketID, newReply);

                    // 不把 DelayedEntry 保存在 map 中以避免复杂同步 / remove 开销
                    DelayedEntry entry = new DelayedEntry(socketID, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SO_TIMEOUT), isTcp);
                    // 将 HostReply 的引用放入 entry，清理线程在到期时会尝试移除 map 中对应的 HostReply 并关闭 socket
                    entry.attachHostReply(newReply);
                    delayQueue.offer(entry);

                } catch (Exception e) {
                    debugOperation(e);
                    close(accepted);
                }
            });
        }

        // 退出循环后，优雅关停线程池（任选）
        acceptHandlerPool.shutdown();
        cleanerExecutor.shutdown();
    }

    // DelayedEntry: DelayQueue 中的条目
    private static class DelayedEntry implements Delayed {
        final long socketID;
        final long triggerTimeNanos;
        final boolean isTcp;
        // 附带 hostReply 引用以便清理线程在取到期项时能访问
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

    // 保持原始的 CONN_TYPE 常量类定义（兼容外部）
    public static class CONN_TYPE {
        public static final int TCP = 0;
        public static final int UDP = 1;
    }
}
