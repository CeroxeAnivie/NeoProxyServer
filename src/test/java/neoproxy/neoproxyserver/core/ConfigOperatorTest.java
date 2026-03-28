package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigOperator 测试")
class ConfigOperatorTest {

    @Test
    @DisplayName("测试静态常量 CONFIG_FILE")
    void testConfigFileConstant() throws Exception {
        Field field = ConfigOperator.class.getDeclaredField("CONFIG_FILE");
        field.setAccessible(true);
        File value = (File) field.get(null);
        assertNotNull(value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertNotNull(ConfigOperator.CONFIG_FILE);
        assertTrue(ConfigOperator.CONFIG_FILE.getName().equals("config.cfg"));
    }

    @Test
    @DisplayName("测试静态常量 SYNC_CONFIG_FILE")
    void testSyncConfigFileConstant() throws Exception {
        Field field = ConfigOperator.class.getDeclaredField("SYNC_CONFIG_FILE");
        field.setAccessible(true);
        File value = (File) field.get(null);
        assertNotNull(value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertNotNull(ConfigOperator.SYNC_CONFIG_FILE);
        assertTrue(ConfigOperator.SYNC_CONFIG_FILE.getName().equals("sync.cfg"));
    }

    @Test
    @DisplayName("测试静态常量 CONFIG_PATH")
    void testConfigPathConstant() throws Exception {
        Field field = ConfigOperator.class.getDeclaredField("CONFIG_PATH");
        field.setAccessible(true);
        assertEquals(java.nio.file.Path.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 SYNC_CONFIG_PATH")
    void testSyncConfigPathConstant() throws Exception {
        Field field = ConfigOperator.class.getDeclaredField("SYNC_CONFIG_PATH");
        field.setAccessible(true);
        assertEquals(java.nio.file.Path.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 MANAGER_URL")
    void testManagerUrlVariable() throws Exception {
        Field field = ConfigOperator.class.getDeclaredField("MANAGER_URL");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 MANAGER_TOKEN")
    void testManagerTokenVariable() throws Exception {
        Field field = ConfigOperator.class.getDeclaredField("MANAGER_TOKEN");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        assertEquals("", ConfigOperator.MANAGER_TOKEN);
    }

    @Test
    @DisplayName("测试静态变量 NODE_ID")
    void testNodeIdVariable() throws Exception {
        Field field = ConfigOperator.class.getDeclaredField("NODE_ID");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        assertEquals("Default-Node", ConfigOperator.NODE_ID);
    }

    @Test
    @DisplayName("测试静态变量 isInitialized")
    void testIsInitializedVariable() throws Exception {
        Field field = ConfigOperator.class.getDeclaredField("isInitialized");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        
        boolean value = field.getBoolean(null);
        assertFalse(value);
    }

    @Test
    @DisplayName("测试私有构造函数")
    void testPrivateConstructor() throws Exception {
        java.lang.reflect.Constructor<ConfigOperator> constructor = ConfigOperator.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        
        constructor.setAccessible(true);
        ConfigOperator instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    @DisplayName("测试类是 final 的")
    void testClassIsFinal() {
        assertTrue(Modifier.isFinal(ConfigOperator.class.getModifiers()));
    }

    @Test
    @DisplayName("测试公共方法 readAndSetValue 存在")
    void testReadAndSetValueMethodExists() throws Exception {
        Method method = ConfigOperator.class.getDeclaredMethod("readAndSetValue");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 readMainConfig 存在")
    void testReadMainConfigMethodExists() throws Exception {
        Method method = ConfigOperator.class.getDeclaredMethod("readMainConfig");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 readSyncConfig 存在")
    void testReadSyncConfigMethodExists() throws Exception {
        Method method = ConfigOperator.class.getDeclaredMethod("readSyncConfig");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 copyConfigFromResource 存在")
    void testCopyConfigFromResourceMethodExists() throws Exception {
        Method method = ConfigOperator.class.getDeclaredMethod("copyConfigFromResource", String.class, java.nio.file.Path.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 applyMainSettings 存在")
    void testApplyMainSettingsMethodExists() throws Exception {
        Method method = ConfigOperator.class.getDeclaredMethod("applyMainSettings", fun.ceroxe.api.utils.config.LineConfigReader.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 applySyncSettings 存在")
    void testApplySyncSettingsMethodExists() throws Exception {
        Method method = ConfigOperator.class.getDeclaredMethod("applySyncSettings", fun.ceroxe.api.utils.config.LineConfigReader.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }
}
