package neoproxy.neoproxyserver.core.threads;

import static neoproxy.neoproxyserver.NeoProxyServer.debugOperation;

public class RateLimiter {
    // 去掉 final，允许在长时间空闲后重置时间基准
    private long startTime = System.nanoTime();
    private long totalBytes = 0;
    private volatile double maxBytesPerSec;

    public RateLimiter(double maxMbps) {
        setMaxMbps(maxMbps);
    }

    public void setMaxMbps(double maxMbps) {
        if (maxMbps <= 0) {
            this.maxBytesPerSec = Double.MAX_VALUE;
        } else {
            // 1 Mbps = 125,000 bytes/sec
            this.maxBytesPerSec = maxMbps * 125_000.0;
        }
    }

    /**
     * 记录传输字节并执行限速休眠
     * 线程安全：支持多线程并发调用（TCP+UDP）
     */
    public void onBytesTransferred(int bytes) {
        if (maxBytesPerSec >= Double.MAX_VALUE) return;

        long sleepNanos = 0;

        synchronized (this) {
            // 1. 检查是否发生长时间空闲（防止“攒流量”导致的瞬间爆发）
            long currentNanos = System.nanoTime();
            long elapsedNanos = currentNanos - startTime;
            double expectedNanos = (totalBytes / maxBytesPerSec) * 1_000_000_000L;

            // 如果当前时间比预期时间快了超过 1 秒（说明很久没传数据了）
            // 重置基准，避免允许瞬间传输大量积攒的额度
            if (elapsedNanos > expectedNanos + 1_000_000_000L) {
                startTime = currentNanos;
                totalBytes = 0;
                elapsedNanos = 0;
            }

            // 2. 累加流量
            totalBytes += bytes;

            // 3. 计算由于流量增加，理论上需要花费的时间
            expectedNanos = (totalBytes / maxBytesPerSec) * 1_000_000_000L;

            // 4. 如果实际流逝时间 < 理论时间，说明太快了，需要睡一会
            if (elapsedNanos < expectedNanos) {
                sleepNanos = (long) (expectedNanos - elapsedNanos);
            }
        }

        // 【重要】Sleep 必须在锁外面执行，否则会阻塞所有共享此限速器的连接
        if (sleepNanos > 0) {
            try {
                long sleepMillis = sleepNanos / 1_000_000;
                int sleepNanosPart = (int) (sleepNanos % 1_000_000);
                Thread.sleep(sleepMillis, sleepNanosPart);
            } catch (Exception e) {
                debugOperation(e);
            }
        }
    }
}