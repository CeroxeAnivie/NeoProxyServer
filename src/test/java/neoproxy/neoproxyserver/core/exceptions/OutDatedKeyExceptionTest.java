package neoproxy.neoproxyserver.core.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OutDatedKeyException 测试")
class OutDatedKeyExceptionTest {

    @Test
    @DisplayName("测试throwException静态方法")
    void testThrowException() {
        String keyName = "expired-key-123";
        
        assertThrows(OutDatedKeyException.class, () -> 
            OutDatedKeyException.throwException(keyName)
        );
    }

    @Test
    @DisplayName("测试异常可以被捕获并获取消息")
    void testExceptionCanBeCaught() {
        String keyName = "test-key";
        
        try {
            OutDatedKeyException.throwException(keyName);
            fail("应该抛出OutDatedKeyException");
        } catch (OutDatedKeyException e) {
            assertNotNull(e.getMessage());
            assertTrue(e.getMessage().contains(keyName));
        }
    }

    @Test
    @DisplayName("测试异常继承自Exception")
    void testExceptionInheritance() {
        assertDoesNotThrow(() -> {
            try {
                OutDatedKeyException.throwException("key");
            } catch (Exception e) {
                assertTrue(e instanceof OutDatedKeyException);
            }
        });
    }

    @Test
    @DisplayName("测试构造器 - 空密钥名")
    void testConstructorWithEmptyKeyName() {
        assertDoesNotThrow(() -> {
            try {
                OutDatedKeyException.throwException("");
            } catch (OutDatedKeyException e) {
                assertNotNull(e.getMessage());
            }
        });
    }

    @Test
    @DisplayName("测试构造器 - null密钥名")
    void testConstructorWithNullKeyName() {
        assertDoesNotThrow(() -> {
            try {
                OutDatedKeyException.throwException(null);
            } catch (OutDatedKeyException e) {
                assertNotNull(e.getMessage());
            }
        });
    }
}
