package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ServerLogger 详细测试")
class ServerLoggerDetailTest {

    @Test
    @DisplayName("测试静态常量 BUNDLE_NAME")
    void testBundleNameConstant() throws Exception {
        Field field = ServerLogger.class.getDeclaredField("BUNDLE_NAME");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("messages", value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试公共静态变量 alert")
    void testAlertVariable() throws Exception {
        Field field = ServerLogger.class.getDeclaredField("alert");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试私有静态变量 bundle")
    void testBundleVariable() throws Exception {
        Field field = ServerLogger.class.getDeclaredField("bundle");
        field.setAccessible(true);
        assertEquals(java.util.ResourceBundle.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试私有静态变量 currentLocale")
    void testCurrentLocaleVariable() throws Exception {
        Field field = ServerLogger.class.getDeclaredField("currentLocale");
        field.setAccessible(true);
        assertEquals(Locale.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试公共方法 setLocale 存在")
    void testSetLocaleMethodExists() throws Exception {
        Method method = ServerLogger.class.getDeclaredMethod("setLocale", Locale.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 info 存在 - key和args参数")
    void testInfoMethodExists() throws Exception {
        Method method = ServerLogger.class.getDeclaredMethod("info", String.class, Object[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 infoWithSource 存在")
    void testInfoWithSourceMethodExists() throws Exception {
        Method method = ServerLogger.class.getDeclaredMethod("infoWithSource", String.class, String.class, Object[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 warn 存在")
    void testWarnMethodExists() throws Exception {
        Method method = ServerLogger.class.getDeclaredMethod("warn", String.class, Object[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 warnWithSource 存在")
    void testWarnWithSourceMethodExists() throws Exception {
        Method method = ServerLogger.class.getDeclaredMethod("warnWithSource", String.class, String.class, Object[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 error 存在 - key和args参数")
    void testErrorMethodExists() throws Exception {
        Method method = ServerLogger.class.getDeclaredMethod("error", String.class, Object[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 error 存在 - key, Throwable, args参数")
    void testErrorWithThrowableMethodExists() throws Exception {
        Method method = ServerLogger.class.getDeclaredMethod("error", String.class, Throwable.class, Object[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 errorWithSource 存在")
    void testErrorWithSourceMethodExists() throws Exception {
        Method method = ServerLogger.class.getDeclaredMethod("errorWithSource", String.class, String.class, Object[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 getMessage 存在")
    void testGetMessageMethodExists() throws Exception {
        Method method = ServerLogger.class.getDeclaredMethod("getMessage", String.class, Object[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 logRaw 存在")
    void testLogRawMethodExists() throws Exception {
        Method method = ServerLogger.class.getDeclaredMethod("logRaw", String.class, String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 sayHostClientDiscInfo 存在")
    void testSayHostClientDiscInfoMethodExists() throws Exception {
        Method method = ServerLogger.class.getDeclaredMethod("sayHostClientDiscInfo", neoproxy.neoproxyserver.core.HostClient.class, String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 sayClientTCPConnectBuildUpInfo 存在")
    void testSayClientTCPConnectBuildUpInfoMethodExists() throws Exception {
        Method method = ServerLogger.class.getDeclaredMethod("sayClientTCPConnectBuildUpInfo", neoproxy.neoproxyserver.core.HostClient.class, java.net.Socket.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 getMessage 方法返回正确的消息")
    void testGetMessageReturnsCorrectMessage() {
        String message = ServerLogger.getMessage("neoProxyServer.logo");
        assertNotNull(message);
    }

    @Test
    @DisplayName("测试 getMessage 方法处理缺失的key")
    void testGetMessageWithMissingKey() {
        String message = ServerLogger.getMessage("non.existent.key");
        assertNotNull(message);
        assertTrue(message.contains("!!!"));
    }
}
