package neoproxy.neoproxyserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.ceroxe.api.net.SecureSocket;
import neoproxy.neoproxyserver.core.constants.ServerConstants;
import neoproxy.neoproxyserver.core.threads.TCPTransformer;
import neoproxy.neoproxyserver.core.threads.UDPTransformer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NeoProxyServer 测试")
class NeoProxyServerTest {

    @Test
    @DisplayName("测试VERSION常量")
    void testVersionConstant() {
        assertNotNull(NeoProxyServer.VERSION);
    }

    @Test
    @DisplayName("测试EXPECTED_CLIENT_VERSION常量")
    void testExpectedClientVersionConstant() {
        assertNotNull(NeoProxyServer.EXPECTED_CLIENT_VERSION);
    }

    @Test
    @DisplayName("测试HOST_HOOK_PORT初始值")
    void testHostHookPortInitialValue() {
        assertEquals(44801, NeoProxyServer.HOST_HOOK_PORT);
    }

    @Test
    @DisplayName("测试HOST_CONNECT_PORT初始值")
    void testHostConnectPortInitialValue() {
        assertEquals(44802, NeoProxyServer.HOST_CONNECT_PORT);
    }

    @Test
    @DisplayName("测试LOCAL_DOMAIN_NAME初始值")
    void testLocalDomainNameInitialValue() {
        assertEquals("localhost", NeoProxyServer.LOCAL_DOMAIN_NAME);
    }

    @Test
    @DisplayName("测试IS_DEBUG_MODE初始值")
    void testIsDebugModeInitialValue() {
        assertFalse(NeoProxyServer.IS_DEBUG_MODE);
    }

    @Test
    @DisplayName("测试--low-ram参数会启用低内存运行策略")
    void testLowRamArgumentAppliesMemoryProfile() throws Exception {
        int originalTcpBuffer = TCPTransformer.BUFFER_LEN;
        int originalPacketLimit = SecureSocket.getMaxAllowedPacketSize();
        boolean originalLowRamMode = NeoProxyServer.LOW_RAM_MODE;

        Field udpQueueCapacity = UDPTransformer.class.getDeclaredField("sendQueueCapacity");
        udpQueueCapacity.setAccessible(true);
        int originalUdpQueueCapacity = udpQueueCapacity.getInt(null);

        Method checkArgs = NeoProxyServer.class.getDeclaredMethod("checkARGS", String[].class);
        Method applyProfile = NeoProxyServer.class.getDeclaredMethod("applyRuntimeMemoryProfile");
        checkArgs.setAccessible(true);
        applyProfile.setAccessible(true);

        try {
            TCPTransformer.BUFFER_LEN = ServerConstants.TCP_BUFFER_SIZE;
            NeoProxyServer.LOW_RAM_MODE = false;

            checkArgs.invoke(null, (Object) new String[]{"--low-ram"});
            applyProfile.invoke(null);

            assertTrue(NeoProxyServer.LOW_RAM_MODE);
            assertEquals(ServerConstants.LOW_RAM_TCP_BUFFER_SIZE, TCPTransformer.BUFFER_LEN);
            assertEquals(ServerConstants.LOW_RAM_SECURE_PACKET_SIZE, SecureSocket.getMaxAllowedPacketSize());
            assertEquals(ServerConstants.LOW_RAM_UDP_SEND_QUEUE_CAPACITY, udpQueueCapacity.getInt(null));
        } finally {
            TCPTransformer.BUFFER_LEN = originalTcpBuffer;
            SecureSocket.setMaxAllowedPacketSize(originalPacketLimit);
            UDPTransformer.setSendQueueCapacity(originalUdpQueueCapacity);
            NeoProxyServer.LOW_RAM_MODE = originalLowRamMode;
        }
    }

    @Test
    @DisplayName("测试isStopped初始值")
    void testIsStoppedInitialValue() {
        assertFalse(NeoProxyServer.isStopped);
    }

    @Test
    @DisplayName("测试availableHostClient初始值")
    void testAvailableHostClientInitialValue() {
        assertNotNull(NeoProxyServer.availableHostClient);
        assertTrue(NeoProxyServer.availableHostClient.isEmpty());
    }

    @Test
    @DisplayName("测试availableVersions初始值")
    void testAvailableVersionsInitialValue() {
        assertNotNull(NeoProxyServer.availableVersions);
    }

    @Test
    @DisplayName("测试ASCII_LOGO常量")
    void testAsciiLogoConstant() {
        assertNotNull(NeoProxyServer.ASCII_LOGO);
        assertTrue(NeoProxyServer.ASCII_LOGO.length() > 0);
    }

    @Test
    @DisplayName("测试CURRENT_DIR_PATH常量")
    void testCurrentDirPathConstant() {
        assertNotNull(NeoProxyServer.CURRENT_DIR_PATH);
    }

    @Test
    @DisplayName("测试TOTAL_BYTES_COUNTER常量")
    void testTotalBytesCounterConstant() {
        assertNotNull(NeoProxyServer.TOTAL_BYTES_COUNTER);
    }

    @Test
    @DisplayName("测试静态常量 CURRENT_DIR_PATH 反射")
    void testCurrentDirPathConstantReflection() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("CURRENT_DIR_PATH");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 availableHostClient 反射")
    void testAvailableHostClientConstantReflection() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("availableHostClient");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.CopyOnWriteArrayList.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 ASCII_LOGO 反射")
    void testAsciiLogoConstantReflection() throws Exception {
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
    @DisplayName("测试静态常量 TOTAL_BYTES_COUNTER 反射")
    void testTotalBytesCounterConstantReflection() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("TOTAL_BYTES_COUNTER");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.atomic.LongAdder.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 UDP_GLOBAL_LOCK 反射")
    void testUdpGlobalLockConstantReflection() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("UDP_GLOBAL_LOCK");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.locks.ReentrantLock.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 VERSION 反射")
    void testVersionVariableReflection() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("VERSION");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 EXPECTED_CLIENT_VERSION 反射")
    void testExpectedClientVersionVariableReflection() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("EXPECTED_CLIENT_VERSION");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 availableVersions 反射")
    void testAvailableVersionsVariableReflection() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("availableVersions");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.CopyOnWriteArrayList.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 HOST_HOOK_PORT 反射")
    void testHostHookPortVariableReflection() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("HOST_HOOK_PORT");
        field.setAccessible(true);
        assertEquals(int.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 HOST_CONNECT_PORT 反射")
    void testHostConnectPortVariableReflection() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("HOST_CONNECT_PORT");
        field.setAccessible(true);
        assertEquals(int.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 LOCAL_DOMAIN_NAME 反射")
    void testLocalDomainNameVariableReflection() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("LOCAL_DOMAIN_NAME");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 IS_DEBUG_MODE 反射")
    void testIsDebugModeVariableReflection() throws Exception {
        Field field = NeoProxyServer.class.getDeclaredField("IS_DEBUG_MODE");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 isStopped 反射")
    void testIsStoppedVariableReflection() throws Exception {
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
