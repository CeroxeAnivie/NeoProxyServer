package neoproxy.neoproxyserver.core.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProtocolConstants 常量类测试")
class ProtocolConstantsTest {

    @Test
    @DisplayName("测试私有构造器抛出AssertionError")
    void testPrivateConstructorThrowsException() throws Exception {
        Constructor<ProtocolConstants> constructor = ProtocolConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException exception = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertTrue(exception.getCause() instanceof AssertionError);
        assertTrue(exception.getCause().getMessage().contains("常量类禁止实例化"));
    }

    @Test
    @DisplayName("测试命令类型常量值")
    void testCommandConstants() {
        assertEquals((byte) 0x01, ProtocolConstants.CMD_CONNECT);
        assertEquals((byte) 0x02, ProtocolConstants.CMD_DISCONNECT);
        assertEquals((byte) 0x03, ProtocolConstants.CMD_DATA);
        assertEquals((byte) 0x04, ProtocolConstants.CMD_HEARTBEAT);
        assertEquals((byte) 0x05, ProtocolConstants.CMD_ERROR);
    }

    @Test
    @DisplayName("测试协议头常量")
    void testProtocolHeaderConstants() {
        assertArrayEquals(new byte[]{0x4E, 0x50}, ProtocolConstants.PROTOCOL_MAGIC);
        assertEquals(8, ProtocolConstants.HEADER_LENGTH);
        assertEquals(65535, ProtocolConstants.MAX_BODY_LENGTH);
    }

    @Test
    @DisplayName("测试心跳常量")
    void testHeartbeatConstants() {
        assertEquals("PING", ProtocolConstants.HEARTBEAT_REQUEST);
        assertEquals("PONG", ProtocolConstants.HEARTBEAT_RESPONSE);
    }

    @Test
    @DisplayName("测试状态码常量")
    void testStatusCodeConstants() {
        assertEquals(200, ProtocolConstants.STATUS_SUCCESS);
        assertEquals(401, ProtocolConstants.STATUS_INVALID_KEY);
        assertEquals(402, ProtocolConstants.STATUS_NO_FLOW);
        assertEquals(500, ProtocolConstants.STATUS_SERVER_ERROR);
    }
}
