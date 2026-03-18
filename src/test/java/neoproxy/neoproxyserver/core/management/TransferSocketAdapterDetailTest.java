package neoproxy.neoproxyserver.core.management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TransferSocketAdapter 详细测试")
class TransferSocketAdapterDetailTest {

    @Test
    @DisplayName("测试静态变量 SO_TIMEOUT")
    void testSoTimeoutVariable() throws Exception {
        Field field = TransferSocketAdapter.class.getDeclaredField("SO_TIMEOUT");
        field.setAccessible(true);
        assertEquals(int.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 tcpHostReply")
    void testTcpHostReplyVariable() throws Exception {
        Field field = TransferSocketAdapter.class.getDeclaredField("tcpHostReply");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.ConcurrentHashMap.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 udpHostReply")
    void testUdpHostReplyVariable() throws Exception {
        Field field = TransferSocketAdapter.class.getDeclaredField("udpHostReply");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.ConcurrentHashMap.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 tcpWaiting")
    void testTcpWaitingVariable() throws Exception {
        Field field = TransferSocketAdapter.class.getDeclaredField("tcpWaiting");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.ConcurrentHashMap.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 udpWaiting")
    void testUdpWaitingVariable() throws Exception {
        Field field = TransferSocketAdapter.class.getDeclaredField("udpWaiting");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.ConcurrentHashMap.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 delayQueue")
    void testDelayQueueVariable() throws Exception {
        Field field = TransferSocketAdapter.class.getDeclaredField("delayQueue");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.DelayQueue.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 cleanerExecutor")
    void testCleanerExecutorVariable() throws Exception {
        Field field = TransferSocketAdapter.class.getDeclaredField("cleanerExecutor");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.ScheduledExecutorService.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 acceptHandlerPool")
    void testAcceptHandlerPoolVariable() throws Exception {
        Field field = TransferSocketAdapter.class.getDeclaredField("acceptHandlerPool");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.ExecutorService.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 cleanerStarted")
    void testCleanerStartedVariable() throws Exception {
        Field field = TransferSocketAdapter.class.getDeclaredField("cleanerStarted");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.atomic.AtomicBoolean.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试公共方法 startThread 存在")
    void testStartThreadMethodExists() throws Exception {
        Method method = TransferSocketAdapter.class.getDeclaredMethod("startThread");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 getHostReply 存在")
    void testGetHostReplyMethodExists() throws Exception {
        Method method = TransferSocketAdapter.class.getDeclaredMethod("getHostReply", long.class, int.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(neoproxy.neoproxyserver.core.HostReply.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 cleanerLoop 存在")
    void testCleanerLoopMethodExists() throws Exception {
        Method method = TransferSocketAdapter.class.getDeclaredMethod("cleanerLoop");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试实现 Runnable 接口")
    void testImplementsRunnable() {
        assertTrue(Runnable.class.isAssignableFrom(TransferSocketAdapter.class));
    }

    @Test
    @DisplayName("测试 CONN_TYPE 类存在")
    void testConnTypeClassExists() throws Exception {
        Class<?> connTypeClass = Class.forName("neoproxy.neoproxyserver.core.management.TransferSocketAdapter$CONN_TYPE");
        assertNotNull(connTypeClass);
        assertTrue(Modifier.isStatic(connTypeClass.getModifiers()));
        assertTrue(Modifier.isPublic(connTypeClass.getModifiers()));
    }

    @Test
    @DisplayName("测试 CONN_TYPE.TCP 常量")
    void testConnTypeTcpConstant() throws Exception {
        Class<?> connTypeClass = Class.forName("neoproxy.neoproxyserver.core.management.TransferSocketAdapter$CONN_TYPE");
        Field field = connTypeClass.getDeclaredField("TCP");
        field.setAccessible(true);
        int value = (int) field.get(null);
        assertEquals(0, value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试 CONN_TYPE.UDP 常量")
    void testConnTypeUdpConstant() throws Exception {
        Class<?> connTypeClass = Class.forName("neoproxy.neoproxyserver.core.management.TransferSocketAdapter$CONN_TYPE");
        Field field = connTypeClass.getDeclaredField("UDP");
        field.setAccessible(true);
        int value = (int) field.get(null);
        assertEquals(1, value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }
}
