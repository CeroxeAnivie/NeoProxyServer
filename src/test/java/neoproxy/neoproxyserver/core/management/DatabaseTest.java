package neoproxy.neoproxyserver.core.management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
    @DisplayName("测试DB_DRIVER常量")
    void testDbDriverConstant() throws Exception {
        Field field = Database.class.getDeclaredField("DB_DRIVER");
        field.setAccessible(true);
        String value = (String) field.get(null);
        
        assertEquals("org.sqlite.JDBC", value);
    }

    @Test
    @DisplayName("测试DB_URL常量")
    void testDbUrlConstant() throws Exception {
        Field field = Database.class.getDeclaredField("DB_URL");
        field.setAccessible(true);
        String value = (String) field.get(null);
        
        assertTrue(value.startsWith("jdbc:sqlite:"));
    }

    @Test
    @DisplayName("测试keepAliveConn初始值")
    void testKeepAliveConnInitialValue() throws Exception {
        Field field = Database.class.getDeclaredField("keepAliveConn");
        field.setAccessible(true);
        Connection value = (Connection) field.get(null);
        
        assertNull(value);
    }
}
