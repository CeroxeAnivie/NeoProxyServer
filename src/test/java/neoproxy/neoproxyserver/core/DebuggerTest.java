package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

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
}
