package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigOperator 测试")
class ConfigOperatorTest {

    @Test
    @DisplayName("测试CONFIG_FILE常量")
    void testConfigFileConstant() {
        assertNotNull(ConfigOperator.CONFIG_FILE);
        assertTrue(ConfigOperator.CONFIG_FILE.getName().equals("config.cfg"));
    }

    @Test
    @DisplayName("测试SYNC_CONFIG_FILE常量")
    void testSyncConfigFileConstant() {
        assertNotNull(ConfigOperator.SYNC_CONFIG_FILE);
        assertTrue(ConfigOperator.SYNC_CONFIG_FILE.getName().equals("sync.cfg"));
    }

    @Test
    @DisplayName("测试NODE_ID默认值")
    void testNodeIdDefaultValue() {
        assertEquals("Default-Node", ConfigOperator.NODE_ID);
    }

    @Test
    @DisplayName("测试MANAGER_TOKEN默认值")
    void testManagerTokenDefaultValue() {
        assertEquals("", ConfigOperator.MANAGER_TOKEN);
    }

    @Test
    @DisplayName("测试私有构造器")
    void testPrivateConstructor() throws Exception {
        Constructor<ConfigOperator> constructor = ConfigOperator.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        
        ConfigOperator instance = constructor.newInstance();
        
        assertNotNull(instance);
    }

    @Test
    @DisplayName("测试isInitialized初始值")
    void testIsInitializedInitialValue() throws Exception {
        java.lang.reflect.Field field = ConfigOperator.class.getDeclaredField("isInitialized");
        field.setAccessible(true);
        
        boolean value = field.getBoolean(null);
        assertFalse(value);
    }
}
