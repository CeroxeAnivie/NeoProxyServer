package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Debugger 详细测试")
class DebuggerDetailTest {

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
