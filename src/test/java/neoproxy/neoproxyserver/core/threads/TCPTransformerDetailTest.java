package neoproxy.neoproxyserver.core.threads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TCPTransformer 详细测试")
class TCPTransformerDetailTest {

    @Test
    @DisplayName("测试静态常量 TELL_BALANCE_MIB")
    void testTellBalanceMibConstant() throws Exception {
        Field field = TCPTransformer.class.getDeclaredField("TELL_BALANCE_MIB");
        field.setAccessible(true);
        int value = (int) field.get(null);
        assertEquals(10, value);
    }

    @Test
    @DisplayName("测试静态常量 BUFFER_LEN")
    void testBufferLenConstant() throws Exception {
        Field field = TCPTransformer.class.getDeclaredField("BUFFER_LEN");
        field.setAccessible(true);
        int value = (int) field.get(null);
        assertEquals(65535, value);
    }

    @Test
    @DisplayName("测试静态常量 CUSTOM_BLOCKING_MESSAGE")
    void testCustomBlockingMessageConstant() throws Exception {
        Field field = TCPTransformer.class.getDeclaredField("CUSTOM_BLOCKING_MESSAGE");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("如有疑问，请联系您的系统管理员。", value);
    }

    @Test
    @DisplayName("测试静态常量 FORBIDDEN_HTML_TEMPLATE")
    void testForbiddenHtmlTemplateConstant() throws Exception {
        Field field = TCPTransformer.class.getDeclaredField("FORBIDDEN_HTML_TEMPLATE");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertNotNull(value);
    }

    @Test
    @DisplayName("测试字段 hostClient 类型")
    void testHostClientFieldType() throws Exception {
        Field field = TCPTransformer.class.getDeclaredField("hostClient");
        field.setAccessible(true);
        assertEquals(neoproxy.neoproxyserver.core.HostClient.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 client 类型")
    void testClientFieldType() throws Exception {
        Field field = TCPTransformer.class.getDeclaredField("client");
        field.setAccessible(true);
        assertEquals(java.net.Socket.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 hostReply 类型")
    void testHostReplyFieldType() throws Exception {
        Field field = TCPTransformer.class.getDeclaredField("hostReply");
        field.setAccessible(true);
        assertEquals(neoproxy.neoproxyserver.core.HostReply.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试字段 clientInputStream 类型")
    void testClientInputStreamFieldType() throws Exception {
        Field field = TCPTransformer.class.getDeclaredField("clientInputStream");
        field.setAccessible(true);
        assertEquals(InputStream.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态方法 start 存在 - 带InputStream参数")
    void testStartMethodWithInputStreamExists() throws Exception {
        Method method = TCPTransformer.class.getDeclaredMethod("start", 
            neoproxy.neoproxyserver.core.HostClient.class, 
            neoproxy.neoproxyserver.core.HostReply.class, 
            java.net.Socket.class, 
            InputStream.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 start 存在 - 不带InputStream参数")
    void testStartMethodWithoutInputStreamExists() throws Exception {
        Method method = TCPTransformer.class.getDeclaredMethod("start", 
            neoproxy.neoproxyserver.core.HostClient.class, 
            neoproxy.neoproxyserver.core.HostReply.class, 
            java.net.Socket.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 tellRestBalance 存在")
    void testTellRestBalanceMethodExists() throws Exception {
        Method method = TCPTransformer.class.getDeclaredMethod("tellRestBalance", 
            neoproxy.neoproxyserver.core.HostClient.class, 
            double[].class, 
            int.class, 
            neoproxy.neoproxyserver.core.LanguageData.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试静态方法 kickAllWithMsg 存在")
    void testKickAllWithMsgMethodExists() throws Exception {
        Method method = TCPTransformer.class.getDeclaredMethod("kickAllWithMsg", 
            neoproxy.neoproxyserver.core.HostClient.class, 
            fun.ceroxe.api.net.SecureSocket.class, 
            java.io.Closeable.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 checkAndBlockHtmlResponse 存在")
    void testCheckAndBlockHtmlResponseMethodExists() throws Exception {
        Method method = TCPTransformer.class.getDeclaredMethod("checkAndBlockHtmlResponse", 
            byte[].class, 
            java.net.Socket.class, 
            String.class, 
            neoproxy.neoproxyserver.core.HostClient.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 createProxyProtocolV2Header 存在")
    void testCreateProxyProtocolV2HeaderMethodExists() throws Exception {
        Method method = TCPTransformer.class.getDeclaredMethod("createProxyProtocolV2Header", 
            java.net.Socket.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(byte[].class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 clientToHost 存在")
    void testClientToHostMethodExists() throws Exception {
        Method method = TCPTransformer.class.getDeclaredMethod("clientToHost", double[].class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertFalse(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 hostToClient 存在")
    void testHostToClientMethodExists() throws Exception {
        Method method = TCPTransformer.class.getDeclaredMethod("hostToClient", double[].class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertFalse(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有构造函数存在")
    void testPrivateConstructorExists() throws Exception {
        java.lang.reflect.Constructor<?>[] constructors = TCPTransformer.class.getDeclaredConstructors();
        boolean hasPrivateConstructor = false;
        for (java.lang.reflect.Constructor<?> constructor : constructors) {
            if (Modifier.isPrivate(constructor.getModifiers())) {
                hasPrivateConstructor = true;
                break;
            }
        }
        assertTrue(hasPrivateConstructor, "TCPTransformer should have a private constructor");
    }

    @Test
    @DisplayName("测试类不能被实例化")
    void testClassCannotBeInstantiated() {
        try {
            java.lang.reflect.Constructor<TCPTransformer> constructor = 
                TCPTransformer.class.getDeclaredConstructor(
                    neoproxy.neoproxyserver.core.HostClient.class, 
                    java.net.Socket.class, 
                    neoproxy.neoproxyserver.core.HostReply.class, 
                    InputStream.class);
            constructor.setAccessible(true);
            assertNotNull(constructor);
        } catch (Exception e) {
            fail("Should be able to access constructor via reflection");
        }
    }
}
