package neoproxy.neoproxyserver.core.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnRecognizedKeyException 测试")
class UnRecognizedKeyExceptionTest {

    @Test
    @DisplayName("测试throwException静态方法")
    void testThrowException() {
        String key = "invalid-key-123";

        assertThrows(UnRecognizedKeyException.class, () ->
                UnRecognizedKeyException.throwException(key)
        );
    }

    @Test
    @DisplayName("测试异常可以被捕获并获取消息")
    void testExceptionCanBeCaught() {
        String key = "unknown-key";

        try {
            UnRecognizedKeyException.throwException(key);
            fail("应该抛出UnRecognizedKeyException");
        } catch (UnRecognizedKeyException e) {
            assertNotNull(e.getMessage());
            assertTrue(e.getMessage().contains(key));
        }
    }

    @Test
    @DisplayName("测试异常继承自Exception")
    void testExceptionInheritance() {
        assertDoesNotThrow(() -> {
            try {
                UnRecognizedKeyException.throwException("key");
            } catch (Exception e) {
                assertTrue(e instanceof UnRecognizedKeyException);
            }
        });
    }

    @Test
    @DisplayName("测试构造器 - 空密钥")
    void testConstructorWithEmptyKey() {
        assertDoesNotThrow(() -> {
            try {
                UnRecognizedKeyException.throwException("");
            } catch (UnRecognizedKeyException e) {
                assertNotNull(e.getMessage());
            }
        });
    }

    @Test
    @DisplayName("测试构造器 - null密钥")
    void testConstructorWithNullKey() {
        assertDoesNotThrow(() -> {
            try {
                UnRecognizedKeyException.throwException(null);
            } catch (UnRecognizedKeyException e) {
                assertNotNull(e.getMessage());
            }
        });
    }
}
