package neoproxy.neoproxyserver.core.threads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.DatagramPacket;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UDPTransformer 详细测试")
class UDPTransformerDetailTest {

    @Test
    @DisplayName("测试静态常量 udpClientConnections")
    void testUdpClientConnectionsConstant() throws Exception {
        Field field = UDPTransformer.class.getDeclaredField("udpClientConnections");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.CopyOnWriteArrayList.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 hostClient 类型")
    void testHostClientFieldType() throws Exception {
        Field field = UDPTransformer.class.getDeclaredField("hostClient");
        field.setAccessible(true);
        assertEquals(neoproxy.neoproxyserver.core.HostClient.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 hostReply 类型")
    void testHostReplyFieldType() throws Exception {
        Field field = UDPTransformer.class.getDeclaredField("hostReply");
        field.setAccessible(true);
        assertEquals(neoproxy.neoproxyserver.core.HostReply.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 sharedDatagramSocket 类型")
    void testSharedDatagramSocketFieldType() throws Exception {
        Field field = UDPTransformer.class.getDeclaredField("sharedDatagramSocket");
        field.setAccessible(true);
        assertEquals(java.net.DatagramSocket.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 clientIP 类型")
    void testClientIpFieldType() throws Exception {
        Field field = UDPTransformer.class.getDeclaredField("clientIP");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 clientOutPort 类型")
    void testClientOutPortFieldType() throws Exception {
        Field field = UDPTransformer.class.getDeclaredField("clientOutPort");
        field.setAccessible(true);
        assertEquals(int.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 sendQueue 类型")
    void testSendQueueFieldType() throws Exception {
        Field field = UDPTransformer.class.getDeclaredField("sendQueue");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.ArrayBlockingQueue.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 isRunning 类型")
    void testIsRunningFieldType() throws Exception {
        Field field = UDPTransformer.class.getDeclaredField("isRunning");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isVolatile(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态方法 serializeDatagramPacket 存在")
    void testSerializeDatagramPacketMethodExists() throws Exception {
        Method method = UDPTransformer.class.getDeclaredMethod("serializeDatagramPacket", DatagramPacket.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(byte[].class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 deserializeToDatagramPacket 存在")
    void testDeserializeToDatagramPacketMethodExists() throws Exception {
        Method method = UDPTransformer.class.getDeclaredMethod("deserializeToDatagramPacket", byte[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(DatagramPacket.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 tellRestBalance 存在")
    void testTellRestBalanceMethodExists() throws Exception {
        Method method = UDPTransformer.class.getDeclaredMethod("tellRestBalance", 
            neoproxy.neoproxyserver.core.HostClient.class, 
            double[].class, 
            int.class, 
            neoproxy.neoproxyserver.core.LanguageData.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 kickAllWithMsg 存在")
    void testKickAllWithMsgMethodExists() throws Exception {
        Method method = UDPTransformer.class.getDeclaredMethod("kickAllWithMsg", 
            neoproxy.neoproxyserver.core.HostClient.class, 
            fun.ceroxe.api.net.SecureSocket.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 getHostClient 存在")
    void testGetHostClientMethodExists() throws Exception {
        Method method = UDPTransformer.class.getDeclaredMethod("getHostClient");
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(neoproxy.neoproxyserver.core.HostClient.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 getClientOutPort 存在")
    void testGetClientOutPortMethodExists() throws Exception {
        Method method = UDPTransformer.class.getDeclaredMethod("getClientOutPort");
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(int.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 getClientIP 存在")
    void testGetClientIpMethodExists() throws Exception {
        Method method = UDPTransformer.class.getDeclaredMethod("getClientIP");
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 isRunning 存在")
    void testIsRunningMethodExists() throws Exception {
        Method method = UDPTransformer.class.getDeclaredMethod("isRunning");
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 addPacketToSend 存在")
    void testAddPacketToSendMethodExists() throws Exception {
        Method method = UDPTransformer.class.getDeclaredMethod("addPacketToSend", byte[].class);
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 outClientToHostClient 存在")
    void testOutClientToHostClientMethodExists() throws Exception {
        Method method = UDPTransformer.class.getDeclaredMethod("outClientToHostClient", double[].class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 hostClientToOutClient 存在")
    void testHostClientToOutClientMethodExists() throws Exception {
        Method method = UDPTransformer.class.getDeclaredMethod("hostClientToOutClient", double[].class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 stop 存在")
    void testStopMethodExists() throws Exception {
        Method method = UDPTransformer.class.getDeclaredMethod("stop");
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试方法 run 实现 Runnable 接口")
    void testRunImplementsRunnable() throws Exception {
        assertTrue(Runnable.class.isAssignableFrom(UDPTransformer.class));
    }

    @Test
    @DisplayName("测试 serializeDatagramPacket 方法 - 正常数据")
    void testSerializeDatagramPacketNormal() throws Exception {
        byte[] data = "Hello, World!".getBytes();
        InetAddress address = InetAddress.getByName("127.0.0.1");
        DatagramPacket packet = new DatagramPacket(data, data.length, address, 8080);
        
        byte[] serialized = UDPTransformer.serializeDatagramPacket(packet);
        
        assertNotNull(serialized);
        assertTrue(serialized.length > data.length);
    }

    @Test
    @DisplayName("测试 deserializeToDatagramPacket 方法 - null输入")
    void testDeserializeToDatagramPacketNull() throws Exception {
        DatagramPacket result = UDPTransformer.deserializeToDatagramPacket(null);
        assertNull(result);
    }

    @Test
    @DisplayName("测试 deserializeToDatagramPacket 方法 - 空数据")
    void testDeserializeToDatagramPacketEmpty() throws Exception {
        DatagramPacket result = UDPTransformer.deserializeToDatagramPacket(new byte[0]);
        assertNull(result);
    }

    @Test
    @DisplayName("测试 deserializeToDatagramPacket 方法 - 数据太短")
    void testDeserializeToDatagramPacketTooShort() throws Exception {
        DatagramPacket result = UDPTransformer.deserializeToDatagramPacket(new byte[10]);
        assertNull(result);
    }

    @Test
    @DisplayName("测试 deserializeToDatagramPacket 方法 - 无效魔数")
    void testDeserializeToDatagramPacketInvalidMagic() throws Exception {
        byte[] invalidData = new byte[20];
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(invalidData);
        buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
        buffer.putInt(0x12345678);
        
        DatagramPacket result = UDPTransformer.deserializeToDatagramPacket(invalidData);
        assertNull(result);
    }
}
