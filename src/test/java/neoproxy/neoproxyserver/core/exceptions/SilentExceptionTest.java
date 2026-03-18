package neoproxy.neoproxyserver.core.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SilentException 测试")
class SilentExceptionTest {

    @Test
    @DisplayName("测试throwException静态方法抛出异常")
    void testThrowException() {
        assertThrows(SilentException.class, SilentException::throwException);
    }

    @Test
    @DisplayName("测试异常可以被捕获")
    void testExceptionCanBeCaught() {
        try {
            SilentException.throwException();
            fail("应该抛出SilentException");
        } catch (SilentException e) {
            assertNotNull(e);
        }
    }

    @Test
    @DisplayName("测试异常继承自Exception")
    void testExceptionInheritance() {
        assertDoesNotThrow(() -> {
            try {
                SilentException.throwException();
            } catch (Exception e) {
                assertTrue(e instanceof SilentException);
            }
        });
    }

    @Test
    @DisplayName("测试异常消息为空")
    void testExceptionMessage() throws SilentException {
        try {
            SilentException.throwException();
        } catch (SilentException e) {
            assertNull(e.getMessage());
        }
    }
}
