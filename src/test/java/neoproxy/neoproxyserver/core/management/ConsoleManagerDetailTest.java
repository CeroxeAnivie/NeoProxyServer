package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.core.webadmin.WebConsole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConsoleManager 详细测试")
class ConsoleManagerDetailTest {

    @Test
    @DisplayName("测试静态常量 COMMAND_SOURCE")
    void testCommandSourceConstant() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("COMMAND_SOURCE");
        field.setAccessible(true);
        assertEquals(ThreadLocal.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("测试 COMMAND_SOURCE 初始值")
    void testCommandSourceInitialValue() throws Exception {
        String value = ConsoleManager.COMMAND_SOURCE.get();
        assertEquals("Admin", value);
    }

    @Test
    @DisplayName("测试静态常量 TIME_FORMAT_PATTERN")
    void testTimeFormatPatternConstant() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("TIME_FORMAT_PATTERN");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("^(\\d{4})/(\\d{1,2})/(\\d{1,2})-(\\d{1,2}):(\\d{1,2})$", value);
    }

    @Test
    @DisplayName("测试静态常量 TIME_PATTERN")
    void testTimePatternConstant() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("TIME_PATTERN");
        field.setAccessible(true);
        Pattern pattern = (Pattern) field.get(null);
        assertNotNull(pattern);
    }

    @Test
    @DisplayName("测试 TIME_PATTERN 匹配有效日期")
    void testTimePatternMatchesValidDate() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("TIME_PATTERN");
        field.setAccessible(true);
        Pattern pattern = (Pattern) field.get(null);
        
        assertTrue(pattern.matcher("2024/1/1-12:30").matches());
        assertTrue(pattern.matcher("2024/12/31-23:59").matches());
        assertTrue(pattern.matcher("2024/6/15-9:5").matches());
    }

    @Test
    @DisplayName("测试 TIME_PATTERN 不匹配无效日期")
    void testTimePatternDoesNotMatchInvalidDate() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("TIME_PATTERN");
        field.setAccessible(true);
        Pattern pattern = (Pattern) field.get(null);
        
        assertFalse(pattern.matcher("2024-01-01 12:30").matches());
        assertFalse(pattern.matcher("invalid").matches());
        assertFalse(pattern.matcher("2024/1/1").matches());
        assertFalse(pattern.matcher("12:30").matches());
    }

    @Test
    @DisplayName("测试静态常量 PORT_INPUT_PATTERN")
    void testPortInputPatternConstant() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("PORT_INPUT_PATTERN");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("^(\\d+)(?:-(\\d+))?$", value);
    }

    @Test
    @DisplayName("测试静态常量 PORT_INPUT_REGEX")
    void testPortInputRegexConstant() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("PORT_INPUT_REGEX");
        field.setAccessible(true);
        Pattern pattern = (Pattern) field.get(null);
        assertNotNull(pattern);
    }

    @Test
    @DisplayName("测试 PORT_INPUT_REGEX 匹配单个端口")
    void testPortInputRegexMatchesSinglePort() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("PORT_INPUT_REGEX");
        field.setAccessible(true);
        Pattern pattern = (Pattern) field.get(null);
        
        assertTrue(pattern.matcher("8080").matches());
        assertTrue(pattern.matcher("80").matches());
        assertTrue(pattern.matcher("443").matches());
    }

    @Test
    @DisplayName("测试 PORT_INPUT_REGEX 匹配端口范围")
    void testPortInputRegexMatchesPortRange() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("PORT_INPUT_REGEX");
        field.setAccessible(true);
        Pattern pattern = (Pattern) field.get(null);
        
        assertTrue(pattern.matcher("8080-8090").matches());
        assertTrue(pattern.matcher("1-65535").matches());
    }

    @Test
    @DisplayName("测试 PORT_INPUT_REGEX 不匹配无效输入")
    void testPortInputRegexDoesNotMatchInvalidInput() throws Exception {
        Field field = ConsoleManager.class.getDeclaredField("PORT_INPUT_REGEX");
        field.setAccessible(true);
        Pattern pattern = (Pattern) field.get(null);
        
        assertFalse(pattern.matcher("abc").matches());
        assertFalse(pattern.matcher("8080-").matches());
        assertFalse(pattern.matcher("-8080").matches());
    }

    @Test
    @DisplayName("测试方法 init 存在")
    void testInitMethodExists() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("init");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 initCommand 存在")
    void testInitCommandMethodExists() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("initCommand");
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 registerWrapper 存在")
    void testRegisterWrapperMethodExists() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("registerWrapper", 
            String.class, String.class, java.util.function.Consumer.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleAlert 存在")
    void testHandleAlertMethodExists() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("handleAlert", boolean.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleAddCommand 存在")
    void testHandleAddCommandMethodExists() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("handleAddCommand", java.util.List.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleDelCommand 存在")
    void testHandleDelCommandMethodExists() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("handleDelCommand", java.util.List.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleListCommand 存在")
    void testHandleListCommandMethodExists() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("handleListCommand", java.util.List.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleLookupCommand 存在")
    void testHandleLookupCommandMethodExists() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("handleLookupCommand", java.util.List.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleSetCommand 存在")
    void testHandleSetCommandMethodExists() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("handleSetCommand", java.util.List.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleEnableCommand 存在")
    void testHandleEnableCommandMethodExists() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("handleEnableCommand", java.util.List.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 handleDisableCommand 存在")
    void testHandleDisableCommandMethodExists() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("handleDisableCommand", java.util.List.class);
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 printKeyUsage 存在")
    void testPrintKeyUsageMethodExists() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("printKeyUsage");
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 listActiveHostClients 存在")
    void testListActiveHostClientsMethodExists() throws Exception {
        Method method = ConsoleManager.class.getDeclaredMethod("listActiveHostClients");
        assertNotNull(method);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }
}
