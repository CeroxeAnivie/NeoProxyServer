package neoproject.neoproxy.core.threads;

import static neoproject.neoproxy.NeoProxyServer.debugOperation;

public class RateLimiter {
    private long totalBytes = 0;
    private final long startTime = System.nanoTime();
    private final double maxBytesPerSec;

    protected RateLimiter(double maxMbps) {
        // 👇 新增：速率限制（单位：Mbps），设为 0 表示不限速
        if (maxMbps <= 0) {
            // <= 0 表示不限速
            this.maxBytesPerSec = Double.MAX_VALUE;
        } else {
            // 1 Mbps = 1,000,000 bits per second = 125,000 bytes per second
            this.maxBytesPerSec = maxMbps * 125_000.0;// 内部转换：Mbps → bytes per second
        }
    }

    protected void onBytesTransferred(int bytes) {
        if (maxBytesPerSec >= Double.MAX_VALUE) return; // 不限速

        totalBytes += bytes;
        long elapsedNanos = System.nanoTime() - startTime;
        double expectedNanos = (totalBytes / maxBytesPerSec) * 1_000_000_000L;

        if (elapsedNanos < expectedNanos) {
            long sleepNanos = (long) (expectedNanos - elapsedNanos);
            if (sleepNanos > 0) {
                // 纳秒级 sleep（实际精度有限，但足够）
                try {
                    Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                } catch (Exception e) {
                    debugOperation(e);
                }
            }
        }
    }
}