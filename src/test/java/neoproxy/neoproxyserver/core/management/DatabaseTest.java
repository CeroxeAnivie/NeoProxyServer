package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.NeoProxyServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Database 测试")
class DatabaseTest {

    @Test
    @DisplayName("测试私有构造器 - 类只有静态方法")
    void testPrivateConstructor() throws Exception {
        Constructor<Database> constructor = Database.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        Database instance = constructor.newInstance();

        assertNotNull(instance);
    }

    @Test
    @DisplayName("测试静态常量 DB_DRIVER")
    void testDbDriverConstant() throws Exception {
        Field field = Database.class.getDeclaredField("DB_DRIVER");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("org.sqlite.JDBC", value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 DB_URL")
    void testDbUrlConstant() throws Exception {
        Field field = Database.class.getDeclaredField("DB_URL");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("jdbc:sqlite:" + new File(NeoProxyServer.CURRENT_DIR_PATH, "sk").getAbsolutePath(), value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 keepAliveConn")
    void testKeepAliveConnVariable() throws Exception {
        Field field = Database.class.getDeclaredField("keepAliveConn");
        field.setAccessible(true);
        assertEquals(Connection.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试keepAliveConn初始值")
    void testKeepAliveConnInitialValue() throws Exception {
        Field field = Database.class.getDeclaredField("keepAliveConn");
        field.setAccessible(true);
        Connection value = (Connection) field.get(null);

        assertNull(value);
    }

    @Test
    @DisplayName("测试公共方法 init 存在")
    void testInitMethodExists() throws Exception {
        Method method = Database.class.getDeclaredMethod("init");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 reload 存在")
    void testReloadMethodExists() throws Exception {
        Method method = Database.class.getDeclaredMethod("reload");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isSynchronized(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 connectAndConfigure 存在")
    void testConnectAndConfigureMethodExists() throws Exception {
        Method method = Database.class.getDeclaredMethod("connectAndConfigure");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 getConnection 存在")
    void testGetConnectionMethodExists() throws Exception {
        Method method = Database.class.getDeclaredMethod("getConnection");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(Connection.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 safeAddColumn 存在")
    void testSafeAddColumnMethodExists() throws Exception {
        Method method = Database.class.getDeclaredMethod("safeAddColumn", java.sql.Statement.class, String.class, String.class, String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 getKey 存在")
    void testGetKeyMethodExists() throws Exception {
        Method method = Database.class.getDeclaredMethod("getKey", String.class, boolean.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(SequenceKey.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 exists 存在")
    void testExistsMethodExists() throws Exception {
        Method method = Database.class.getDeclaredMethod("exists", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 saveKey 存在")
    void testSaveKeyMethodExists() throws Exception {
        Method method = Database.class.getDeclaredMethod("saveKey", SequenceKey.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 createKey 存在")
    void testCreateKeyMethodExists() throws Exception {
        Method method = Database.class.getDeclaredMethod("createKey", String.class, double.class, String.class, String.class, double.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 deleteKey 存在")
    void testDeleteKeyMethodExists() throws Exception {
        Method method = Database.class.getDeclaredMethod("deleteKey", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 updateBalance 存在")
    void testUpdateBalanceMethodExists() throws Exception {
        Method method = Database.class.getDeclaredMethod("updateBalance", String.class, double.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 updateStatus 存在")
    void testUpdateStatusMethodExists() throws Exception {
        Method method = Database.class.getDeclaredMethod("updateStatus", String.class, boolean.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 getAllKeys 存在")
    void testGetAllKeysMethodExists() throws Exception {
        Method method = Database.class.getDeclaredMethod("getAllKeys");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(java.util.List.class, method.getReturnType());
    }
}
