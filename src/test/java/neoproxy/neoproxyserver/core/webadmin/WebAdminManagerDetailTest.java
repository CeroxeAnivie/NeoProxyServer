package neoproxy.neoproxyserver.core.webadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebAdminManager 详细测试")
class WebAdminManagerDetailTest {

    @Test
    @DisplayName("测试静态常量 TEMP_TOKEN_VALIDITY_MS")
    void testTempTokenValidityMsConstant() throws Exception {
        Field field = WebAdminManager.class.getDeclaredField("TEMP_TOKEN_VALIDITY_MS");
        field.setAccessible(true);
        long value = (long) field.get(null);
        assertEquals(5 * 60 * 1000, value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 MANAGER_LOCK")
    void testManagerLockConstant() throws Exception {
        Field field = WebAdminManager.class.getDeclaredField("MANAGER_LOCK");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.locks.ReentrantLock.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 RESTART_CONDITION")
    void testRestartConditionConstant() throws Exception {
        Field field = WebAdminManager.class.getDeclaredField("RESTART_CONDITION");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.locks.Condition.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 WEB_ADMIN_PORT")
    void testWebAdminPortVariable() throws Exception {
        Field field = WebAdminManager.class.getDeclaredField("WEB_ADMIN_PORT");
        field.setAccessible(true);
        assertEquals(int.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 internalServer")
    void testInternalServerVariable() throws Exception {
        Field field = WebAdminManager.class.getDeclaredField("internalServer");
        field.setAccessible(true);
        assertEquals(WebAdminServer.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 gatewaySocket")
    void testGatewaySocketVariable() throws Exception {
        Field field = WebAdminManager.class.getDeclaredField("gatewaySocket");
        field.setAccessible(true);
        assertEquals(java.net.ServerSocket.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 isRunning")
    void testIsRunningVariable() throws Exception {
        Field field = WebAdminManager.class.getDeclaredField("isRunning");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isVolatile(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 isStarting")
    void testIsStartingVariable() throws Exception {
        Field field = WebAdminManager.class.getDeclaredField("isStarting");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isVolatile(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 currentTempToken")
    void testCurrentTempTokenVariable() throws Exception {
        Field field = WebAdminManager.class.getDeclaredField("currentTempToken");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 tempTokenCreatedAt")
    void testTempTokenCreatedAtVariable() throws Exception {
        Field field = WebAdminManager.class.getDeclaredField("tempTokenCreatedAt");
        field.setAccessible(true);
        assertEquals(long.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 permanentToken")
    void testPermanentTokenVariable() throws Exception {
        Field field = WebAdminManager.class.getDeclaredField("permanentToken");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试公共方法 setPermanentToken 存在")
    void testSetPermanentTokenMethodExists() throws Exception {
        Method method = WebAdminManager.class.getDeclaredMethod("setPermanentToken", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 init 存在")
    void testInitMethodExists() throws Exception {
        Method method = WebAdminManager.class.getDeclaredMethod("init");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 shutdown 存在")
    void testShutdownMethodExists() throws Exception {
        Method method = WebAdminManager.class.getDeclaredMethod("shutdown");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 restart 存在")
    void testRestartMethodExists() throws Exception {
        Method method = WebAdminManager.class.getDeclaredMethod("restart");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 broadcastLog 存在")
    void testBroadcastLogMethodExists() throws Exception {
        Method method = WebAdminManager.class.getDeclaredMethod("broadcastLog", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 generateNewSessionUrl 存在")
    void testGenerateNewSessionUrlMethodExists() throws Exception {
        Method method = WebAdminManager.class.getDeclaredMethod("generateNewSessionUrl");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 verifyTokenAndGetType 存在")
    void testVerifyTokenAndGetTypeMethodExists() throws Exception {
        Method method = WebAdminManager.class.getDeclaredMethod("verifyTokenAndGetType", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(int.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 verifyToken 存在")
    void testVerifyTokenMethodExists() throws Exception {
        Method method = WebAdminManager.class.getDeclaredMethod("verifyToken", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 isRunning 存在")
    void testIsRunningMethodExists() throws Exception {
        Method method = WebAdminManager.class.getDeclaredMethod("isRunning");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 startServer 存在")
    void testStartServerMethodExists() throws Exception {
        Method method = WebAdminManager.class.getDeclaredMethod("startServer");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleShieldConnection 存在")
    void testHandleShieldConnectionMethodExists() throws Exception {
        Method method = WebAdminManager.class.getDeclaredMethod("handleShieldConnection", java.net.Socket.class, int.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 pipe - InputStream和Socket参数")
    void testPipeInputStreamSocketMethodExists() throws Exception {
        Method method = WebAdminManager.class.getDeclaredMethod("pipe", java.io.InputStream.class, java.net.Socket.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 pipe - Socket和Socket参数")
    void testPipeSocketSocketMethodExists() throws Exception {
        Method method = WebAdminManager.class.getDeclaredMethod("pipe", java.net.Socket.class, java.net.Socket.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }
}
