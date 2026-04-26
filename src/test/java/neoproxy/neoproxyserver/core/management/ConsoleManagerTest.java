package neoproxy.neoproxyserver.core.management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConsoleManager 测试")
class ConsoleManagerTest {

    @Test
    @DisplayName("测试COMMAND_SOURCE ThreadLocal初始值")
    void testCommandSourceInitialValue() {
        String value = ConsoleManager.COMMAND_SOURCE.get();
        assertEquals("Admin", value);
    }

    @Test
    @DisplayName("测试COMMAND_SOURCE可以设置")
    void testCommandSourceSettable() {
        String original = ConsoleManager.COMMAND_SOURCE.get();
        try {
            ConsoleManager.COMMAND_SOURCE.set("TestSource");
            assertEquals("TestSource", ConsoleManager.COMMAND_SOURCE.get());
        } finally {
            ConsoleManager.COMMAND_SOURCE.set(original);
        }
    }

    @Test
    @DisplayName("测试TIME_FORMAT_PATTERN正则表达式")
    void testTimeFormatPattern() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("TIME_PATTERN");
        field.setAccessible(true);
        Pattern pattern = (Pattern) field.get(null);

        assertTrue(pattern.matcher("2024/1/1-12:30").matches());
        assertTrue(pattern.matcher("2024/12/31-23:59").matches());
        assertFalse(pattern.matcher("2024-01-01 12:30").matches());
        assertFalse(pattern.matcher("invalid").matches());
    }

    @Test
    @DisplayName("测试PORT_INPUT_PATTERN正则表达式")
    void testPortInputPattern() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("PORT_INPUT_REGEX");
        field.setAccessible(true);
        Pattern pattern = (Pattern) field.get(null);

        assertTrue(pattern.matcher("8080").matches());
        assertTrue(pattern.matcher("8080-8090").matches());
        assertFalse(pattern.matcher("abc").matches());
        assertFalse(pattern.matcher("").matches());
    }

    @Test
    @DisplayName("测试init方法签名")
    void testInitMethod() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("init");
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }

    @Test
    @DisplayName("测试私有构造器 - 类只有静态方法")
    void testPrivateConstructor() throws Exception {
        Constructor<ConsoleManager> constructor = ConsoleManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        ConsoleManager instance = constructor.newInstance();

        assertNotNull(instance);
    }

    @Test
    @DisplayName("测试TIME_FORMAT_PATTERN常量")
    void testTimeFormatPatternConstant() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("TIME_FORMAT_PATTERN");
        field.setAccessible(true);
        String value = (String) field.get(null);

        assertEquals("^(\\d{4})/(\\d{1,2})/(\\d{1,2})-(\\d{1,2}):(\\d{1,2})$", value);
    }

    @Test
    @DisplayName("测试PORT_INPUT_PATTERN常量")
    void testPortInputPatternConstant() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("PORT_INPUT_PATTERN");
        field.setAccessible(true);
        String value = (String) field.get(null);

        assertEquals("^(\\d+)(?:-(\\d+))?$", value);
    }

    @Test
    @DisplayName("测试registerWrapper方法签名")
    void testRegisterWrapperMethod() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("registerWrapper",
                String.class, String.class, java.util.function.Consumer.class);
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()));
    }

    @Test
    @DisplayName("测试initCommand方法签名")
    void testInitCommandMethod() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("initCommand");
        assertNotNull(method);
        assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()));
    }
}
