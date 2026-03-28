package neoproxy.neoproxyserver.core;

import fun.ceroxe.api.net.SecureSocket;
import neoproxy.neoproxyserver.core.management.SequenceKey;
import neoproxy.neoproxyserver.core.threads.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HostClient 测试")
class HostClientTest {

    @Test
    @DisplayName("测试静态常量 EXPECTED_HEARTBEAT")
    void testExpectedHeartbeatConstant() throws Exception {
        Field field = HostClient.class.getDeclaredField("EXPECTED_HEARTBEAT");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("PING", value);
    }

    @Test
    @DisplayName("测试静态常量 SAVE_DELAY")
    void testSaveDelayConstant() throws Exception {
        Field field = HostClient.class.getDeclaredField("SAVE_DELAY");
        field.setAccessible(true);
        int value = (int) field.get(null);
        assertEquals(3000, value);
        assertTrue(HostClient.SAVE_DELAY > 0);
    }

    @Test
    @DisplayName("测试静态常量 DETECTION_DELAY")
    void testDetectionDelayConstant() throws Exception {
        Field field = HostClient.class.getDeclaredField("DETECTION_DELAY");
        field.setAccessible(true);
        int value = (int) field.get(null);
        assertEquals(1000, value);
        assertTrue(HostClient.DETECTION_DELAY > 0);
    }

    @Test
    @DisplayName("测试静态常量 AES_KEY_SIZE")
    void testAesKeySizeConstant() throws Exception {
        Field field = HostClient.class.getDeclaredField("AES_KEY_SIZE");
        field.setAccessible(true);
        int value = (int) field.get(null);
        assertEquals(128, value);
        assertTrue(HostClient.AES_KEY_SIZE > 0);
    }

    @Test
    @DisplayName("测试静态常量 HEARTBEAT_TIMEOUT")
    void testHeartbeatTimeoutConstant() throws Exception {
        Field field = HostClient.class.getDeclaredField("HEARTBEAT_TIMEOUT");
        field.setAccessible(true);
        int value = (int) field.get(null);
        assertEquals(30000, value);
        assertTrue(HostClient.HEARTBEAT_TIMEOUT > 0);
    }

    @Test
    @DisplayName("测试字段 activeTcpSockets 类型")
    void testActiveTcpSocketsFieldType() throws Exception {
        Field field = HostClient.class.getDeclaredField("activeTcpSockets");
        field.setAccessible(true);
        assertTrue(Set.class.isAssignableFrom(field.getType()));
    }

    @Test
    @DisplayName("测试字段 globalRateLimiter 类型")
    void testGlobalRateLimiterFieldType() throws Exception {
        Field field = HostClient.class.getDeclaredField("globalRateLimiter");
        field.setAccessible(true);
        assertEquals(RateLimiter.class, field.getType());
    }

    @Test
    @DisplayName("测试字段 isClosed 类型")
    void testIsClosedFieldType() throws Exception {
        Field field = HostClient.class.getDeclaredField("isClosed");
        field.setAccessible(true);
        assertEquals(AtomicBoolean.class, field.getType());
    }

    @Test
    @DisplayName("测试字段 isStopped 类型")
    void testIsStoppedFieldType() throws Exception {
        Field field = HostClient.class.getDeclaredField("isStopped");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
    }

    @Test
    @DisplayName("测试字段 sequenceKey 类型")
    void testSequenceKeyFieldType() throws Exception {
        Field field = HostClient.class.getDeclaredField("sequenceKey");
        field.setAccessible(true);
        assertEquals(SequenceKey.class, field.getType());
    }

    @Test
    @DisplayName("测试字段 clientServerSocket 类型")
    void testClientServerSocketFieldType() throws Exception {
        Field field = HostClient.class.getDeclaredField("clientServerSocket");
        field.setAccessible(true);
        assertEquals(ServerSocket.class, field.getType());
    }

    @Test
    @DisplayName("测试字段 outPort 类型")
    void testOutPortFieldType() throws Exception {
        Field field = HostClient.class.getDeclaredField("outPort");
        field.setAccessible(true);
        assertEquals(int.class, field.getType());
    }

    @Test
    @DisplayName("测试字段 isTCPEnabled 类型")
    void testIsTcpEnabledFieldType() throws Exception {
        Field field = HostClient.class.getDeclaredField("isTCPEnabled");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
    }

    @Test
    @DisplayName("测试字段 isUDPEnabled 类型")
    void testIsUdpEnabledFieldType() throws Exception {
        Field field = HostClient.class.getDeclaredField("isUDPEnabled");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
    }

    @Test
    @DisplayName("测试字段 hostServerHook 类型")
    void testHostServerHookFieldType() throws Exception {
        Field field = HostClient.class.getDeclaredField("hostServerHook");
        field.setAccessible(true);
        assertEquals(SecureSocket.class, field.getType());
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 lastValidHeartbeatTime 类型")
    void testLastValidHeartbeatTimeFieldType() throws Exception {
        Field field = HostClient.class.getDeclaredField("lastValidHeartbeatTime");
        field.setAccessible(true);
        assertEquals(long.class, field.getType());
    }

    @Test
    @DisplayName("测试HostClient实现Closeable接口")
    void testImplementsCloseable() {
        assertTrue(Closeable.class.isAssignableFrom(HostClient.class));
    }

    @Test
    @DisplayName("测试方法 getHostServerHook 返回类型")
    void testGetHostServerHookReturnType() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("getHostServerHook");
        assertEquals(SecureSocket.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 getKey 返回类型")
    void testGetKeyReturnType() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("getKey");
        assertEquals(SequenceKey.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 setKey 参数类型")
    void testSetKeyParameterType() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("setKey", SequenceKey.class);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 getGlobalRateLimiter 返回类型")
    void testGetGlobalRateLimiterReturnType() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("getGlobalRateLimiter");
        assertEquals(RateLimiter.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 isStopped 返回类型")
    void testIsStoppedReturnType() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("isStopped");
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 getActiveTcpSockets 返回类型")
    void testGetActiveTcpSocketsReturnType() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("getActiveTcpSockets");
        assertTrue(Collection.class.isAssignableFrom(method.getReturnType()));
    }

    @Test
    @DisplayName("测试方法 registerTcpSocket 参数类型")
    void testRegisterTcpSocketParameterType() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("registerTcpSocket", Socket.class);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 unregisterTcpSocket 参数类型")
    void testUnregisterTcpSocketParameterType() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("unregisterTcpSocket", Socket.class);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 getExternalConnectionsStr 返回类型")
    void testGetExternalConnectionsStrReturnType() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("getExternalConnectionsStr");
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 refreshHeartbeat 返回类型")
    void testRefreshHeartbeatReturnType() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("refreshHeartbeat");
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 applyDynamicUpdates 返回类型")
    void testApplyDynamicUpdatesReturnType() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("applyDynamicUpdates");
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 enableCheckAliveThread 存在")
    void testEnableCheckAliveThreadExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("enableCheckAliveThread");
        assertNotNull(method);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 startRemoteHeartbeat 存在")
    void testStartRemoteHeartbeatExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("startRemoteHeartbeat");
        assertNotNull(method);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 waitForTcpEnabled 存在")
    void testWaitForTcpEnabledExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("waitForTcpEnabled", HostClient.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 waitForUDPEnabled 存在")
    void testWaitForUDPEnabledExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("waitForUDPEnabled", HostClient.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleHostClientCommand 存在")
    void testHandleHostClientCommandExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("handleHostClientCommand", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 cleanActiveTcpSockets 存在")
    void testCleanActiveTcpSocketsExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("cleanActiveTcpSockets");
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有静态方法 enableAutoSaveThread 存在")
    void testEnableAutoSaveThreadExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("enableAutoSaveThread", HostClient.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有静态方法 enableKeyDetectionTread 存在")
    void testEnableKeyDetectionTreadExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("enableKeyDetectionTread", HostClient.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 formatAsTableRow 存在")
    void testFormatAsTableRowExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("formatAsTableRow", int.class, boolean.class, java.util.List.class);
        assertNotNull(method);
        assertEquals(String[].class, method.getReturnType());
    }

    @Test
    @DisplayName("测试类是 final 的")
    void testClassIsFinal() {
        assertTrue(Modifier.isFinal(HostClient.class.getModifiers()));
    }

    @Test
    @DisplayName("测试 getClientServerSocket 方法存在")
    void testGetClientServerSocketExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("getClientServerSocket");
        assertNotNull(method);
        assertEquals(ServerSocket.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 setClientServerSocket 方法存在")
    void testSetClientServerSocketExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("setClientServerSocket", ServerSocket.class);
        assertNotNull(method);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 getIP 方法存在")
    void testGetIpExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("getIP");
        assertNotNull(method);
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 getOutPort 方法存在")
    void testGetOutPortExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("getOutPort");
        assertNotNull(method);
        assertEquals(int.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 setOutPort 方法存在")
    void testSetOutPortExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("setOutPort", int.class);
        assertNotNull(method);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 getCachedLocation 方法存在")
    void testGetCachedLocationExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("getCachedLocation");
        assertNotNull(method);
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 setCachedLocation 方法存在")
    void testSetCachedLocationExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("setCachedLocation", String.class);
        assertNotNull(method);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 getCachedISP 方法存在")
    void testGetCachedIspExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("getCachedISP");
        assertNotNull(method);
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 setCachedISP 方法存在")
    void testSetCachedIspExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("setCachedISP", String.class);
        assertNotNull(method);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 isTCPEnabled 方法存在")
    void testIsTcpEnabledExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("isTCPEnabled");
        assertNotNull(method);
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 setTCPEnabled 方法存在")
    void testSetTcpEnabledExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("setTCPEnabled", boolean.class);
        assertNotNull(method);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 isUDPEnabled 方法存在")
    void testIsUdpEnabledExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("isUDPEnabled");
        assertNotNull(method);
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 setUDPEnabled 方法存在")
    void testSetUdpEnabledExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("setUDPEnabled", boolean.class);
        assertNotNull(method);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 getLangData 方法存在")
    void testGetLangDataExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("getLangData");
        assertNotNull(method);
        assertEquals(LanguageData.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 getClientDatagramSocket 方法存在")
    void testGetClientDatagramSocketExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("getClientDatagramSocket");
        assertNotNull(method);
        assertEquals(java.net.DatagramSocket.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 setClientDatagramSocket 方法存在")
    void testSetClientDatagramSocketExists() throws Exception {
        Method method = HostClient.class.getDeclaredMethod("setClientDatagramSocket", java.net.DatagramSocket.class);
        assertNotNull(method);
        assertEquals(void.class, method.getReturnType());
    }
}
