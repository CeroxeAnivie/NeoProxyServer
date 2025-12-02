package neoproxy.neoproxyserver.core.threads;

import java.util.concurrent.locks.ReentrantLock;

import static neoproxy.neoproxyserver.NeoProxyServer.debugOperation;

public class RateLimiter {
    // 使用 ReentrantLock 替代 synchronized，避免虚拟线程 Pinning 问题
    private final ReentrantLock lock = new ReentrantLock();

    private long startTime = System.nanoTime();
    private long totalBytes = 0;

    // 这里的变量在锁外读取，所以用 volatile 保证可见性，或者仅在锁内访问
    // 为了性能，读取 maxBytesPerSec 可以在锁内进行，或者复用 calculated value
    private double maxBytesPerSec;

    // 用于快速判断速率是否变更，避免每次都加锁
    private volatile double lastMbps = -1;

    public RateLimiter(double maxMbps) {
        // 初始化时直接设置，不走锁逻辑也可以，但为了统一调用 setMaxMbps
        setMaxMbps(maxMbps);
    }

    public void setMaxMbps(double maxMbps) {
        // 【优化】快速路径：如果速率没变，直接返回
        // 这一步非常重要，因为 Transformer 可能会在循环中频繁调用此方法
        // 使用 volatile 读取避免锁竞争
        if (Math.abs(this.lastMbps - maxMbps) < 0.00001) {
            return;
        }

        lock.lock();
        try {
            // 双重检查（虽然对于 setter 来说单线程调用居多，但为了严谨）
            if (Math.abs(this.lastMbps - maxMbps) < 0.00001) {
                return;
            }

            this.lastMbps = maxMbps;

            if (maxMbps <= 0) {
                this.maxBytesPerSec = Double.MAX_VALUE;
            } else {
                // 1 Mbps = 125,000 bytes/sec
                this.maxBytesPerSec = maxMbps * 125_000.0;
            }

            // 【核心修复】速率变更时，重置时间基准和累计流量
            // 这解决了从“高速”切换到“低速”时，因历史流量巨大导致的“死锁/长时间休眠”问题
            // 也解决了从“低速”切换到“高速”时的瞬间爆发问题
            this.startTime = System.nanoTime();
            this.totalBytes = 0;

        } finally {
            lock.unlock();
        }
    }

    /**
     * 记录传输字节并执行限速休眠
     */
    public void onBytesTransferred(int bytes) {
        // 如果没有限速，直接返回，不获取锁
        if (lastMbps <= 0) return;

        long sleepNanos = 0;

        lock.lock();
        try {
            // 再次检查，防止并发修改导致 maxBytesPerSec 变动
            if (maxBytesPerSec >= Double.MAX_VALUE) return;

            long currentNanos = System.nanoTime();
            long elapsedNanos = currentNanos - startTime;

            // 计算当前累计流量理论上应该花费的时间
            double expectedNanos = (totalBytes / maxBytesPerSec) * 1_000_000_000L;

            // 1. 检查是否发生长时间空闲（防止“攒流量”导致的瞬间爆发）
            // 如果当前时间比预期时间快了超过 1 秒（说明很久没传数据了，或者刚启动）
            // 重置基准，丢弃历史额度
            if (elapsedNanos > expectedNanos + 1_000_000_000L) {
                startTime = currentNanos;
                totalBytes = 0;
                elapsedNanos = 0;
                // 重置后重新计算预期时间 (0)
                expectedNanos = 0;
            }

            // 2. 累加流量
            totalBytes += bytes;

            // 3. 重新计算累加后的理论耗时
            expectedNanos = (totalBytes / maxBytesPerSec) * 1_000_000_000L;

            // 4. 如果实际流逝时间 < 理论时间，说明传太快了，需要睡一会
            if (elapsedNanos < expectedNanos) {
                sleepNanos = (long) (expectedNanos - elapsedNanos);
            }
        } finally {
            lock.unlock();
        }

        // 【重要】Sleep 必须在锁外面执行
        // 虚拟线程执行 Thread.sleep 会自动 Unmount，不会阻塞 OS 线程
        if (sleepNanos > 0) {
            try {
                long sleepMillis = sleepNanos / 1_000_000;
                int sleepNanosPart = (int) (sleepNanos % 1_000_000);
                Thread.sleep(sleepMillis, sleepNanosPart);
            } catch (InterruptedException e) {
                // 恢复中断状态
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                debugOperation(e);
            }
        }
    }
}