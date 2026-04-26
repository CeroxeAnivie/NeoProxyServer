package neoproxy.neoproxyserver.core.threads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UDPTransformer 序列化测试")
class UDPTransformerTest {

    @Test
    @DisplayName("测试serializeDatagramPacket - IPv4地址")
    void testSerializeDatagramPacket_IPv4() throws Exception {
        byte[] data = "Hello World".getBytes();
        InetAddress address = InetAddress.getByName("192.168.1.1");
        int port = 8080;
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);

        byte[] serialized = UDPTransformer.serializeDatagramPacket(packet);

        assertNotNull(serialized);
        assertTrue(serialized.length > 0);
    }

    @Test
    @DisplayName("测试serializeDatagramPacket - IPv6地址")
    void testSerializeDatagramPacket_IPv6() throws Exception {
        byte[] data = "Test Data".getBytes();
        InetAddress address = InetAddress.getByName("::1");
        int port = 9090;
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);

        byte[] serialized = UDPTransformer.serializeDatagramPacket(packet);

        assertNotNull(serialized);
        assertTrue(serialized.length > 0);
    }

    @Test
    @DisplayName("测试serializeDatagramPacket - 空数据")
    void testSerializeDatagramPacket_EmptyData() throws Exception {
        byte[] data = new byte[0];
        InetAddress address = InetAddress.getByName("127.0.0.1");
        int port = 12345;
        DatagramPacket packet = new DatagramPacket(data, 0, address, port);

        byte[] serialized = UDPTransformer.serializeDatagramPacket(packet);

        assertNotNull(serialized);
    }

    @Test
    @DisplayName("测试deserializeToDatagramPacket - IPv4往返")
    void testDeserializeToDatagramPacket_IPv4RoundTrip() throws Exception {
        byte[] originalData = "Test Message 123".getBytes();
        InetAddress originalAddress = InetAddress.getByName("10.0.0.1");
        int originalPort = 5000;
        DatagramPacket originalPacket = new DatagramPacket(originalData, originalData.length, originalAddress, originalPort);

        byte[] serialized = UDPTransformer.serializeDatagramPacket(originalPacket);
        DatagramPacket deserialized = UDPTransformer.deserializeToDatagramPacket(serialized);

        assertNotNull(deserialized);
        assertArrayEquals(originalData, deserialized.getData());
        assertEquals(originalAddress, deserialized.getAddress());
        assertEquals(originalPort, deserialized.getPort());
    }

    @Test
    @DisplayName("测试deserializeToDatagramPacket - null输入")
    void testDeserializeToDatagramPacket_NullInput() {
        assertNull(UDPTransformer.deserializeToDatagramPacket(null));
    }

    @Test
    @DisplayName("测试deserializeToDatagramPacket - 过短数据")
    void testDeserializeToDatagramPacket_TooShort() {
        byte[] shortData = new byte[10];

        assertNull(UDPTransformer.deserializeToDatagramPacket(shortData));
    }

    @Test
    @DisplayName("测试deserializeToDatagramPacket - 无效魔数")
    void testDeserializeToDatagramPacket_InvalidMagic() {
        byte[] invalidData = new byte[20];
        invalidData[0] = 0x00;
        invalidData[1] = 0x00;
        invalidData[2] = 0x00;
        invalidData[3] = 0x00;

        assertNull(UDPTransformer.deserializeToDatagramPacket(invalidData));
    }

    @Test
    @DisplayName("测试deserializeToDatagramPacket - 有效魔数")
    void testDeserializeToDatagramPacket_ValidMagic() throws Exception {
        byte[] data = "Test".getBytes();
        InetAddress address = InetAddress.getByName("192.168.1.100");
        DatagramPacket packet = new DatagramPacket(data, data.length, address, 8080);

        byte[] serialized = UDPTransformer.serializeDatagramPacket(packet);
        DatagramPacket result = UDPTransformer.deserializeToDatagramPacket(serialized);

        assertNotNull(result);
        assertEquals(0xDEADBEEF, java.nio.ByteBuffer.wrap(serialized).getInt());
    }

    @Test
    @DisplayName("测试serializeDatagramPacket - 带偏移的数据")
    void testSerializeDatagramPacket_WithOffset() throws Exception {
        byte[] buffer = new byte[100];
        byte[] data = "Offset Test".getBytes();
        System.arraycopy(data, 0, buffer, 10, data.length);
        InetAddress address = InetAddress.getByName("172.16.0.1");
        int port = 3000;
        DatagramPacket packet = new DatagramPacket(buffer, 10, data.length, address, port);

        byte[] serialized = UDPTransformer.serializeDatagramPacket(packet);

        assertNotNull(serialized);
        DatagramPacket deserialized = UDPTransformer.deserializeToDatagramPacket(serialized);
        assertNotNull(deserialized);
        assertArrayEquals(data, deserialized.getData());
    }

    @Test
    @DisplayName("测试序列化大数据包")
    void testSerializeLargePacket() throws Exception {
        byte[] largeData = new byte[65000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }
        InetAddress address = InetAddress.getByName("8.8.8.8");
        int port = 53;
        DatagramPacket packet = new DatagramPacket(largeData, largeData.length, address, port);

        byte[] serialized = UDPTransformer.serializeDatagramPacket(packet);
        DatagramPacket deserialized = UDPTransformer.deserializeToDatagramPacket(serialized);

        assertNotNull(deserialized);
        assertEquals(largeData.length, deserialized.getLength());
    }
}
