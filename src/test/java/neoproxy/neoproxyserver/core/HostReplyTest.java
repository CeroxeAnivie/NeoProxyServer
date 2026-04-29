package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HostReply 测试")
class HostReplyTest {

    @Test
    @DisplayName("测试HostReply record创建")
    void testHostReplyCreation() {
        HostReply reply = new HostReply(12345L, null);

        assertEquals(12345L, reply.socketID());
        assertNull(reply.host());
    }

    @Test
    @DisplayName("测试HostReply record - 负数socketID")
    void testHostReply_NegativeSocketID() {
        HostReply reply = new HostReply(-1L, null);

        assertEquals(-1L, reply.socketID());
    }

    @Test
    @DisplayName("测试HostReply record - 零socketID")
    void testHostReply_ZeroSocketID() {
        HostReply reply = new HostReply(0L, null);

        assertEquals(0L, reply.socketID());
    }

    @Test
    @DisplayName("测试HostReply equals方法")
    void testHostReply_Equals() {
        HostReply reply1 = new HostReply(12345L, null);
        HostReply reply2 = new HostReply(12345L, null);

        assertEquals(reply1, reply2);
    }

    @Test
    @DisplayName("测试HostReply hashCode方法")
    void testHostReply_HashCode() {
        HostReply reply1 = new HostReply(12345L, null);
        HostReply reply2 = new HostReply(12345L, null);

        assertEquals(reply1.hashCode(), reply2.hashCode());
    }

    @Test
    @DisplayName("测试HostReply toString方法")
    void testHostReply_ToString() {
        HostReply reply = new HostReply(12345L, null);

        String str = reply.toString();

        assertTrue(str.contains("12345"));
    }

    @Test
    @DisplayName("测试 HostReply 是 record 类型")
    void testHostReplyIsRecord() {
        assertTrue(HostReply.class.isRecord());
    }

    @Test
    @DisplayName("测试 record 组件数量")
    void testRecordComponentCount() {
        RecordComponent[] components = HostReply.class.getRecordComponents();
        assertEquals(2, components.length);
    }

    @Test
    @DisplayName("测试 record 组件 socketID")
    void testSocketIdComponent() {
        RecordComponent[] components = HostReply.class.getRecordComponents();
        assertEquals("socketID", components[0].getName());
        assertEquals(long.class, components[0].getType());
    }

    @Test
    @DisplayName("测试 record 组件 host")
    void testHostComponent() {
        RecordComponent[] components = HostReply.class.getRecordComponents();
        assertEquals("host", components[1].getName());
        assertEquals(top.ceroxe.api.net.SecureSocket.class, components[1].getType());
    }

    @Test
    @DisplayName("测试 socketID 方法存在")
    void testSocketIdMethodExists() throws Exception {
        java.lang.reflect.Method method = HostReply.class.getDeclaredMethod("socketID");
        assertNotNull(method);
        assertEquals(long.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 host 方法存在")
    void testHostMethodExists() throws Exception {
        java.lang.reflect.Method method = HostReply.class.getDeclaredMethod("host");
        assertNotNull(method);
        assertEquals(top.ceroxe.api.net.SecureSocket.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 toString 方法存在")
    void testToStringMethodExists() throws Exception {
        java.lang.reflect.Method method = HostReply.class.getDeclaredMethod("toString");
        assertNotNull(method);
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 hashCode 方法存在")
    void testHashCodeMethodExists() throws Exception {
        java.lang.reflect.Method method = HostReply.class.getDeclaredMethod("hashCode");
        assertNotNull(method);
        assertEquals(int.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 equals 方法存在")
    void testEqualsMethodExists() throws Exception {
        java.lang.reflect.Method method = HostReply.class.getDeclaredMethod("equals", Object.class);
        assertNotNull(method);
        assertEquals(boolean.class, method.getReturnType());
    }
}
