package neoproxy.neoproxyserver.core.webadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebAdminServer 详细测试")
class WebAdminServerDetailTest {

    @Test
    @DisplayName("测试静态常量 TIME_FORMATTER")
    void testTimeFormatterConstant() throws Exception {
        Field field = WebAdminServer.class.getDeclaredField("TIME_FORMATTER");
        field.setAccessible(true);
        assertEquals(java.time.format.DateTimeFormatter.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 logHistory")
    void testLogHistoryConstant() throws Exception {
        Field field = WebAdminServer.class.getDeclaredField("logHistory");
        field.setAccessible(true);
        assertEquals(LinkedList.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 MAX_HISTORY_SIZE")
    void testMaxHistorySizeConstant() throws Exception {
        Field field = WebAdminServer.class.getDeclaredField("MAX_HISTORY_SIZE");
        field.setAccessible(true);
        int value = (int) field.get(null);
        assertEquals(1000, value);
    }

    @Test
    @DisplayName("测试静态常量 GLOBAL_LOCK")
    void testGlobalLockConstant() throws Exception {
        Field field = WebAdminServer.class.getDeclaredField("GLOBAL_LOCK");
        field.setAccessible(true);
        assertEquals(java.util.concurrent.locks.ReentrantLock.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 ZOMBIE_TIMEOUT_MS")
    void testZombieTimeoutMsConstant() throws Exception {
        Field field = WebAdminServer.class.getDeclaredField("ZOMBIE_TIMEOUT_MS");
        field.setAccessible(true);
        long value = (long) field.get(null);
        assertEquals(10000L, value);
    }

    @Test
    @DisplayName("测试静态方法 broadcastLog 存在")
    void testBroadcastLogMethodExists() throws Exception {
        Method method = WebAdminServer.class.getDeclaredMethod("broadcastLog", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有静态方法 broadcastJson 存在")
    void testBroadcastJsonMethodExists() throws Exception {
        Method method = WebAdminServer.class.getDeclaredMethod("broadcastJson", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有静态方法 saveAndBroadcast 存在")
    void testSaveAndBroadcastMethodExists() throws Exception {
        Method method = WebAdminServer.class.getDeclaredMethod("saveAndBroadcast", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有静态方法 formatLog 存在")
    void testFormatLogMethodExists() throws Exception {
        Method method = WebAdminServer.class.getDeclaredMethod("formatLog", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有静态方法 escapeJson 存在")
    void testEscapeJsonMethodExists() throws Exception {
        Method method = WebAdminServer.class.getDeclaredMethod("escapeJson", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 getRealRemoteIp 存在")
    void testGetRealRemoteIpMethodExists() throws Exception {
        Method method = WebAdminServer.class.getDeclaredMethod("getRealRemoteIp", fi.iki.elonen.NanoHTTPD.IHTTPSession.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 serve 存在")
    void testServeMethodExists() throws Exception {
        Method method = WebAdminServer.class.getDeclaredMethod("serve", fi.iki.elonen.NanoHTTPD.IHTTPSession.class);
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(fi.iki.elonen.NanoHTTPD.Response.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleCheckExists 存在")
    void testHandleCheckExistsMethodExists() throws Exception {
        Method method = WebAdminServer.class.getDeclaredMethod("handleCheckExists", fi.iki.elonen.NanoHTTPD.IHTTPSession.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(fi.iki.elonen.NanoHTTPD.Response.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleFileUpload 存在")
    void testHandleFileUploadMethodExists() throws Exception {
        Method method = WebAdminServer.class.getDeclaredMethod("handleFileUpload", fi.iki.elonen.NanoHTTPD.IHTTPSession.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(fi.iki.elonen.NanoHTTPD.Response.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleFileDownload 存在")
    void testHandleFileDownloadMethodExists() throws Exception {
        Method method = WebAdminServer.class.getDeclaredMethod("handleFileDownload", fi.iki.elonen.NanoHTTPD.IHTTPSession.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(fi.iki.elonen.NanoHTTPD.Response.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 determineMimeType 存在")
    void testDetermineMimeTypeMethodExists() throws Exception {
        Method method = WebAdminServer.class.getDeclaredMethod("determineMimeType", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试类继承 NanoWSD")
    void testExtendsNanoWSD() {
        assertTrue(fi.iki.elonen.NanoWSD.class.isAssignableFrom(WebAdminServer.class));
    }
}
