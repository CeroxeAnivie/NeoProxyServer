package neoproxy.neoproxyserver.core.webadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PortUnificationServer 详细测试")
class PortUnificationServerDetailTest {

    @Test
    @DisplayName("测试构造函数")
    void testConstructor() throws Exception {
        java.lang.reflect.Constructor<?>[] constructors = PortUnificationServer.class.getDeclaredConstructors();
        assertTrue(constructors.length > 0);
        
        for (java.lang.reflect.Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() == 2) {
                assertTrue(Modifier.isPublic(constructor.getModifiers()));
                break;
            }
        }
    }

    @Test
    @DisplayName("测试字段 publicPort")
    void testPublicPortField() throws Exception {
        Field field = PortUnificationServer.class.getDeclaredField("publicPort");
        field.setAccessible(true);
        assertEquals(int.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 internalWsPort")
    void testInternalWsPortField() throws Exception {
        Field field = PortUnificationServer.class.getDeclaredField("internalWsPort");
        field.setAccessible(true);
        assertEquals(int.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 serverSocket")
    void testServerSocketField() throws Exception {
        Field field = PortUnificationServer.class.getDeclaredField("serverSocket");
        field.setAccessible(true);
        assertEquals(java.net.ServerSocket.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 isRunning")
    void testIsRunningField() throws Exception {
        Field field = PortUnificationServer.class.getDeclaredField("isRunning");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertTrue(Modifier.isVolatile(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试公共方法 start 存在")
    void testStartMethodExists() throws Exception {
        Method method = PortUnificationServer.class.getDeclaredMethod("start");
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 stop 存在")
    void testStopMethodExists() throws Exception {
        Method method = PortUnificationServer.class.getDeclaredMethod("stop");
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleConnection 存在")
    void testHandleConnectionMethodExists() throws Exception {
        Method method = PortUnificationServer.class.getDeclaredMethod("handleConnection", java.net.Socket.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 serveHtml 存在")
    void testServeHtmlMethodExists() throws Exception {
        Method method = PortUnificationServer.class.getDeclaredMethod("serveHtml", java.net.Socket.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 proxyToWebSocket 存在")
    void testProxyToWebSocketMethodExists() throws Exception {
        Method method = PortUnificationServer.class.getDeclaredMethod("proxyToWebSocket", java.net.Socket.class, java.io.InputStream.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 pipe - InputStream和Socket参数")
    void testPipeInputStreamSocketMethodExists() throws Exception {
        Method method = PortUnificationServer.class.getDeclaredMethod("pipe", java.io.InputStream.class, java.net.Socket.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 pipe - Socket和Socket参数")
    void testPipeSocketSocketMethodExists() throws Exception {
        Method method = PortUnificationServer.class.getDeclaredMethod("pipe", java.net.Socket.class, java.net.Socket.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }
}
