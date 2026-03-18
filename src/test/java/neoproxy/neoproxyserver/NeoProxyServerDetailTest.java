package neoproxy.neoproxyserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NeoProxyServer 详细测试")
class NeoProxyServerDetailTest {

    @Test
    @DisplayName("测试静态常量 CURRENT_DIR_PATH")
    void testCurrentDirPathConstant() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("CURRENT_DIR_PATH");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 availableHostClient")
    void testAvailableHostClientConstant() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("availableHostClient");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.CopyOnWriteArrayList.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 ASCII_LOGO")
    void testAsciiLogoConstant() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("ASCII_LOGO");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertNotNull(value);
        assertTrue(value.contains("_____"));
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 TOTAL_BYTES_COUNTER")
    void testTotalBytesCounterConstant() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("TOTAL_BYTES_COUNTER");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.atomic.LongAdder.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 UDP_GLOBAL_LOCK")
    void testUdpGlobalLockConstant() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("UDP_GLOBAL_LOCK");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.locks.ReentrantLock.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 VERSION")
    void testVersionVariable() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("VERSION");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 EXPECTED_CLIENT_VERSION")
    void testExpectedClientVersionVariable() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("EXPECTED_CLIENT_VERSION");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 availableVersions")
    void testAvailableVersionsVariable() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("availableVersions");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.CopyOnWriteArrayList.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 HOST_HOOK_PORT")
    void testHostHookPortVariable() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("HOST_HOOK_PORT");
        field.setAccessible(true);
        assertEquals(int.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 HOST_CONNECT_PORT")
    void testHostConnectPortVariable() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("HOST_CONNECT_PORT");
        field.setAccessible(true);
        assertEquals(int.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 LOCAL_DOMAIN_NAME")
    void testLocalDomainNameVariable() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("LOCAL_DOMAIN_NAME");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 IS_DEBUG_MODE")
    void testIsDebugModeVariable() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("IS_DEBUG_MODE");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 isStopped")
    void testIsStoppedVariable() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("isStopped");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试私有方法 toCopyOnWriteArrayListWithLoop 存在")
    void testToCopyOnWriteArrayListWithLoopMethodExists() throws Exception {
        Method method = NeoProxyServer.class.getDeclaredMethod("toCopyOnWriteArrayListWithLoop", String[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(java.util.concurrent.CopyOnWriteArrayList.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 getFromAppProperties 存在")
    void testGetFromAppPropertiesMethodExists() throws Exception {
        Method method = NeoProxyServer.class.getDeclaredMethod("getFromAppProperties", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 initStructure 存在")
    void testInitStructureMethodExists() throws Exception {
        Method method = NeoProxyServer.class.getDeclaredMethod("initStructure");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 main 存在")
    void testMainMethodExists() throws Exception {
        Method method = NeoProxyServer.class.getDeclaredMethod("main", String[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 shutdown 存在")
    void testShutdownMethodExists() throws Exception {
        Method method = NeoProxyServer.class.getDeclaredMethod("shutdown");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 checkARGS 存在")
    void testCheckArgsMethodExists() throws Exception {
        Method method = NeoProxyServer.class.getDeclaredMethod("checkARGS", String[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 printLogo 存在")
    void testPrintLogoMethodExists() throws Exception {
        Method method = NeoProxyServer.class.getDeclaredMethod("printLogo");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleNewHostClient 存在")
    void testHandleNewHostClientMethodExists() throws Exception {
        Method method = NeoProxyServer.class.getDeclaredMethod("handleNewHostClient", neoproxy.neoproxyserver.core.HostClient.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }
}
