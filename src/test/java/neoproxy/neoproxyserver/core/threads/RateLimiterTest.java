package neoproxy.neoproxyserver.core.threads;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RateLimiter 测试")
class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(10.0);
    }

    @Test
    @DisplayName("测试构造器 - 初始化速率限制")
    void testConstructor() {
        RateLimiter limiter = new RateLimiter(100.0);

        assertEquals(100.0, limiter.getCurrentRateMbps());
    }

    @Test
    @DisplayName("测试构造器 - 零速率")
    void testConstructor_ZeroRate() {
        RateLimiter limiter = new RateLimiter(0);

        assertEquals(0, limiter.getCurrentRateMbps());
    }

    @Test
    @DisplayName("测试构造器 - 负速率")
    void testConstructor_NegativeRate() {
        RateLimiter limiter = new RateLimiter(-10.0);

        assertEquals(-10.0, limiter.getCurrentRateMbps());
    }

    @Test
    @DisplayName("测试setMaxMbps - 设置新速率")
    void testSetMaxMbps() {
        rateLimiter.setMaxMbps(50.0);

        assertEquals(50.0, rateLimiter.getCurrentRateMbps());
    }

    @Test
    @DisplayName("测试setMaxMbps - 设置相同速率不触发重置")
    void testSetMaxMbps_SameRate() {
        double initialRate = rateLimiter.getCurrentRateMbps();

        rateLimiter.setMaxMbps(initialRate);

        assertEquals(initialRate, rateLimiter.getCurrentRateMbps());
    }

    @Test
    @DisplayName("测试setMaxMbps - 设置零速率表示不限速")
    void testSetMaxMbps_ZeroRate() {
        rateLimiter.setMaxMbps(0);

        assertEquals(0, rateLimiter.getCurrentRateMbps());
    }

    @Test
    @DisplayName("测试reset方法")
    void testReset() {
        rateLimiter.onBytesTransferred(1000);

        assertDoesNotThrow(() -> rateLimiter.reset());
    }

    @Test
    @DisplayName("测试onBytesTransferred - 零速率不限速")
    void testOnBytesTransferred_ZeroRate() {
        RateLimiter limiter = new RateLimiter(0);

        assertDoesNotThrow(() -> limiter.onBytesTransferred(1000000));
    }

    @Test
    @DisplayName("测试onBytesTransferred - 正常传输")
    void testOnBytesTransferred_NormalTransfer() {
        assertDoesNotThrow(() -> rateLimiter.onBytesTransferred(1000));
    }

    @Test
    @DisplayName("测试onBytesTransferred - 大量数据传输")
    void testOnBytesTransferred_LargeData() {
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 100; i++) {
                rateLimiter.onBytesTransferred(1000);
            }
        });
    }

    @Test
    @DisplayName("测试getCurrentRateMbps")
    void testGetCurrentRateMbps() {
        assertEquals(10.0, rateLimiter.getCurrentRateMbps());

        rateLimiter.setMaxMbps(20.0);
        assertEquals(20.0, rateLimiter.getCurrentRateMbps());
    }

    @Test
    @DisplayName("测试并发设置速率")
    void testConcurrentSetMaxMbps() throws InterruptedException {
        Thread[] threads = new Thread[10];

        for (int i = 0; i < threads.length; i++) {
            final double rate = (i + 1) * 10.0;
            threads[i] = new Thread(() -> rateLimiter.setMaxMbps(rate));
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(rateLimiter.getCurrentRateMbps() > 0);
    }

    @Test
    @DisplayName("测试负速率传输")
    void testOnBytesTransferred_NegativeRate() {
        RateLimiter limiter = new RateLimiter(-5.0);

        assertDoesNotThrow(() -> limiter.onBytesTransferred(1000));
    }

    @RepeatedTest(5)
    @DisplayName("测试多次重置和传输")
    void testMultipleResetAndTransfer() {
        rateLimiter.onBytesTransferred(500);
        rateLimiter.reset();
        rateLimiter.onBytesTransferred(500);
        rateLimiter.reset();

        assertDoesNotThrow(() -> rateLimiter.onBytesTransferred(1000));
    }
}
