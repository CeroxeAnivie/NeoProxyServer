package neoproxy.neoproxyserver.core.management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MinecraftTrafficInspector 测试")
class MinecraftTrafficInspectorTest {

    @Test
    @DisplayName("识别合法 Minecraft 握手并完整保留前缀")
    void testReadHandshakePrefixAcceptsValidHandshake() throws Exception {
        byte[] handshake = createHandshake("localhost", 25565, 2);

        byte[] result = MinecraftTrafficInspector.readHandshakePrefix(new ByteArrayInputStream(handshake));

        assertArrayEquals(handshake, result);
    }

    @Test
    @DisplayName("拒绝非 Minecraft TCP 数据")
    void testReadHandshakePrefixRejectsPlainTcpData() throws Exception {
        byte[] data = "GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

        byte[] result = MinecraftTrafficInspector.readHandshakePrefix(new ByteArrayInputStream(data));

        assertNull(result);
    }

    @Test
    @DisplayName("拒绝被截断的 Minecraft 握手")
    void testReadHandshakePrefixRejectsTruncatedHandshake() throws Exception {
        byte[] handshake = createHandshake("localhost", 25565, 2);
        byte[] truncated = new byte[handshake.length - 1];
        System.arraycopy(handshake, 0, truncated, 0, truncated.length);

        byte[] result = MinecraftTrafficInspector.readHandshakePrefix(new ByteArrayInputStream(truncated));

        assertNull(result);
    }

    private static byte[] createHandshake(String host, int port, int nextState) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 0);
        writeVarInt(payload, 765);
        byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
        writeVarInt(payload, hostBytes.length);
        payload.write(hostBytes);
        payload.write((port >>> 8) & 0xFF);
        payload.write(port & 0xFF);
        writeVarInt(payload, nextState);

        byte[] payloadBytes = payload.toByteArray();
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        writeVarInt(packet, payloadBytes.length);
        packet.write(payloadBytes);
        return packet.toByteArray();
    }

    private static void writeVarInt(ByteArrayOutputStream outputStream, int value) {
        int remaining = value;
        do {
            int current = remaining & 0x7F;
            remaining >>>= 7;
            if (remaining != 0) {
                current |= 0x80;
            }
            outputStream.write(current);
        } while (remaining != 0);
    }
}
