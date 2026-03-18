package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HostReply 详细测试")
class HostReplyDetailTest {

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
        assertEquals(fun.ceroxe.api.net.SecureSocket.class, components[1].getType());
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
        assertEquals(fun.ceroxe.api.net.SecureSocket.class, method.getReturnType());
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
