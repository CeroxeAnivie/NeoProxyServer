package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InternetOperator 测试")
class InternetOperatorTest {

    @Test
    @DisplayName("测试私有构造函数")
    void testPrivateConstructor() throws Exception {
        java.lang.reflect.Constructor<InternetOperator> constructor = InternetOperator.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    }

    @Test
    @DisplayName("测试类是 final 的")
    void testClassIsFinal() {
        assertTrue(Modifier.isFinal(InternetOperator.class.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 COMMAND_PREFIX")
    void testCommandPrefixConstant() throws Exception {
        Field field = InternetOperator.class.getDeclaredField("COMMAND_PREFIX");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals(":>", value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态方法 close 存在 - 可变参数Closeable")
    void testCloseMethodExists() throws Exception {
        Method method = InternetOperator.class.getDeclaredMethod("close", java.io.Closeable[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 sendStr 存在")
    void testSendStrMethodExists() throws Exception {
        Method method = InternetOperator.class.getDeclaredMethod("sendStr", HostClient.class, String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 sendCommand 存在")
    void testSendCommandMethodExists() throws Exception {
        Method method = InternetOperator.class.getDeclaredMethod("sendCommand", HostClient.class, String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 getInternetAddressAndPort 存在 - Socket参数")
    void testGetInternetAddressAndPortSocketMethodExists() throws Exception {
        Method method = InternetOperator.class.getDeclaredMethod("getInternetAddressAndPort", java.net.Socket.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 getInternetAddressAndPort 存在 - SecureSocket参数")
    void testGetInternetAddressAndPortSecureSocketMethodExists() throws Exception {
        Method method = InternetOperator.class.getDeclaredMethod("getInternetAddressAndPort", fun.ceroxe.api.net.SecureSocket.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 getInternetAddressAndPort 存在 - DatagramPacket参数")
    void testGetInternetAddressAndPortDatagramPacketMethodExists() throws Exception {
        Method method = InternetOperator.class.getDeclaredMethod("getInternetAddressAndPort", java.net.DatagramPacket.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 getIP 存在")
    void testGetIpMethodExists() throws Exception {
        Method method = InternetOperator.class.getDeclaredMethod("getIP", fun.ceroxe.api.net.SecureSocket.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 isTCPAvailable 存在")
    void testIsTcpAvailableMethodExists() throws Exception {
        Method method = InternetOperator.class.getDeclaredMethod("isTCPAvailable", int.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 isUDPAvailable 存在")
    void testIsUdpAvailableMethodExists() throws Exception {
        Method method = InternetOperator.class.getDeclaredMethod("isUDPAvailable", int.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 shutdownOutput 存在")
    void testShutdownOutputMethodExists() throws Exception {
        Method method = InternetOperator.class.getDeclaredMethod("shutdownOutput", fun.ceroxe.api.net.SecureSocket.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 shutdownInput 存在 - Socket参数")
    void testShutdownInputSocketMethodExists() throws Exception {
        Method method = InternetOperator.class.getDeclaredMethod("shutdownInput", java.net.Socket.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 isTCPAvailable 方法 - 端口0应该可用")
    void testIsTcpAvailablePortZero() {
        boolean result = InternetOperator.isTCPAvailable(0);
        assertTrue(result);
    }

    @Test
    @DisplayName("测试 isUDPAvailable 方法 - 端口0应该可用")
    void testIsUdpAvailablePortZero() {
        boolean result = InternetOperator.isUDPAvailable(0);
        assertTrue(result);
    }
}
