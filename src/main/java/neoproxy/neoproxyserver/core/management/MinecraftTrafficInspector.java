package neoproxy.neoproxyserver.core.management;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 对 Minecraft Java Edition 初始握手包做结构化识别。
 *
 * <p>MC-only 模式需要在创建远端转发 socket 之前拒绝非 MC 流量，因此这里仅解析第一帧
 * Handshake，不做任何业务层兼容猜测。只要长度、VarInt、字段边界或 next state 异常，就返回 null，
 * 调用方即可直接关闭外部 TCP 连接。</p>
 */
public final class MinecraftTrafficInspector {
    private static final int HANDSHAKE_MAX_BYTES = 2048;
    private static final int VARINT_MAX_BYTES = 5;
    private static final int HANDSHAKE_PACKET_ID = 0;
    private static final int STATUS_NEXT_STATE = 1;
    private static final int LOGIN_NEXT_STATE = 2;
    private static final byte[] EMPTY_BYTES = new byte[0];

    private MinecraftTrafficInspector() {
    }

    public static byte[] readHandshakePrefix(InputStream inputStream) throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream(128);
        int packetLength = readVarInt(inputStream, captured);
        if (packetLength <= 0 || packetLength > HANDSHAKE_MAX_BYTES) {
            return null;
        }

        byte[] payload = inputStream.readNBytes(packetLength);
        if (payload.length != packetLength) {
            return null;
        }
        captured.write(payload);

        return isHandshakePayload(payload) ? captured.toByteArray() : null;
    }

    private static int readVarInt(InputStream inputStream, ByteArrayOutputStream captured) throws IOException {
        int value = 0;
        for (int position = 0; position < VARINT_MAX_BYTES; position++) {
            int current = inputStream.read();
            if (current == -1) {
                return -1;
            }
            captured.write(current);
            value |= (current & 0x7F) << (7 * position);
            if ((current & 0x80) == 0) {
                return value;
            }
        }
        return -1;
    }

    private static boolean isHandshakePayload(byte[] payload) {
        PacketReader reader = new PacketReader(payload);
        if (reader.readVarInt() != HANDSHAKE_PACKET_ID
                || reader.readVarInt() < 0
                || !reader.skipString()
                || !reader.skipUnsignedShort()) {
            return false;
        }
        int nextState = reader.readVarInt();
        return (nextState == STATUS_NEXT_STATE || nextState == LOGIN_NEXT_STATE) && reader.isFullyConsumed();
    }

    private static final class PacketReader {
        private final byte[] data;
        private int offset;

        private PacketReader(byte[] data) {
            this.data = data == null ? EMPTY_BYTES : data;
        }

        private int readVarInt() {
            int value = 0;
            for (int position = 0; position < VARINT_MAX_BYTES; position++) {
                if (offset >= data.length) {
                    return -1;
                }
                int current = data[offset++] & 0xFF;
                value |= (current & 0x7F) << (7 * position);
                if ((current & 0x80) == 0) {
                    return value;
                }
            }
            return -1;
        }

        private boolean skipString() {
            int length = readVarInt();
            if (length < 0 || length > Short.MAX_VALUE || offset + length > data.length) {
                return false;
            }
            offset += length;
            return true;
        }

        private boolean skipUnsignedShort() {
            if (offset + Short.BYTES > data.length) {
                return false;
            }
            offset += Short.BYTES;
            return true;
        }

        private boolean isFullyConsumed() {
            return offset == data.length;
        }
    }
}
