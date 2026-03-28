package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Debugger 测试")
class DebuggerTest {

    @Test
    @DisplayName("测试私有构造器")
    void testPrivateConstructor() throws Exception {
        Constructor<Debugger> constructor = Debugger.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        Debugger instance = constructor.newInstance();

        assertNotNull(instance);
    }

    @Test
    @DisplayName("测试debugOperation方法 - String参数")
    void testDebugOperation_String() {
        assertDoesNotThrow(() -> Debugger.debugOperation("Test message"));
    }

    @Test
    @DisplayName("测试debugOperation方法 - null String")
    void testDebugOperation_NullString() {
        assertDoesNotThrow(() -> Debugger.debugOperation((String) null));
    }

    @Test
    @DisplayName("测试debugOperation方法 - Exception参数")
    void testDebugOperation_Exception() {
        Exception e = new RuntimeException("Test exception");
        assertDoesNotThrow(() -> Debugger.debugOperation(e));
    }

    @Test
    @DisplayName("测试debugOperation方法 - null Exception")
    void testDebugOperation_NullException() {
        assertDoesNotThrow(() -> Debugger.debugOperation((Exception) null));
    }

    @Test
    @DisplayName("测试公共方法 debugOperation 存在 - Exception参数")
    void testDebugOperationExceptionMethodExists() throws Exception {
        Method method = Debugger.class.getDeclaredMethod("debugOperation", Exception.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 debugOperation 存在 - String参数")
    void testDebugOperationStringMethodExists() throws Exception {
        Method method = Debugger.class.getDeclaredMethod("debugOperation", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }
}
