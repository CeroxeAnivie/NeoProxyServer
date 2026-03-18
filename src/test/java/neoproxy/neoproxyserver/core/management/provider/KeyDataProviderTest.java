package neoproxy.neoproxyserver.core.management.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KeyDataProvider 接口测试")
class KeyDataProviderTest {

    @Test
    @DisplayName("测试 KeyDataProvider 是接口")
    void testKeyDataProviderIsInterface() {
        assertTrue(KeyDataProvider.class.isInterface());
    }

    @Test
    @DisplayName("测试 init 方法存在")
    void testInitMethodExists() throws Exception {
        Method method = KeyDataProvider.class.getDeclaredMethod("init");
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isAbstract(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 getKey 方法存在")
    void testGetKeyMethodExists() throws Exception {
        Method method = KeyDataProvider.class.getDeclaredMethod("getKey", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isAbstract(method.getModifiers()));
        assertEquals(neoproxy.neoproxyserver.core.management.SequenceKey.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 getKey 方法抛出正确异常")
    void testGetKeyMethodThrowsCorrectExceptions() throws Exception {
        Method method = KeyDataProvider.class.getDeclaredMethod("getKey", String.class);
        Class<?>[] exceptions = method.getExceptionTypes();
        assertEquals(4, exceptions.length);
    }

    @Test
    @DisplayName("测试 consumeFlow 方法存在")
    void testConsumeFlowMethodExists() throws Exception {
        Method method = KeyDataProvider.class.getDeclaredMethod("consumeFlow", String.class, double.class);
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isAbstract(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 releaseKey 方法存在")
    void testReleaseKeyMethodExists() throws Exception {
        Method method = KeyDataProvider.class.getDeclaredMethod("releaseKey", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isAbstract(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 shutdown 方法存在")
    void testShutdownMethodExists() throws Exception {
        Method method = KeyDataProvider.class.getDeclaredMethod("shutdown");
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isAbstract(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 sendHeartbeat 方法存在")
    void testSendHeartbeatMethodExists() throws Exception {
        Method method = KeyDataProvider.class.getDeclaredMethod("sendHeartbeat", Protocol.HeartbeatPayload.class);
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isAbstract(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }
}
