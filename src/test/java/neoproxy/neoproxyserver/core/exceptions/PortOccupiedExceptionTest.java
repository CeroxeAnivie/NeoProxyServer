package neoproxy.neoproxyserver.core.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PortOccupiedException 测试")
class PortOccupiedExceptionTest {

    @Test
    @DisplayName("测试构造器 - 带密钥")
    void testConstructorWithKey() {
        String key = "test-key-123";
        PortOccupiedException exception = new PortOccupiedException(key);
        
        assertNotNull(exception.getMessage());
    }

    @Test
    @DisplayName("测试throwException静态方法")
    void testThrowException() throws PortOccupiedException {
        String key = "test-key";
        
        assertThrows(PortOccupiedException.class, () -> PortOccupiedException.throwException(key));
    }

    @Test
    @DisplayName("测试异常可以被捕获并获取消息")
    void testExceptionCanBeCaught() {
        String key = "port-key";
        
        try {
            PortOccupiedException.throwException(key);
            fail("应该抛出PortOccupiedException");
        } catch (PortOccupiedException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    @DisplayName("测试异常继承自Exception")
    void testExceptionInheritance() {
        PortOccupiedException exception = new PortOccupiedException("key");
        
        assertTrue(exception instanceof Exception);
    }

    @Test
    @DisplayName("测试构造器 - 空密钥")
    void testConstructorWithEmptyKey() {
        PortOccupiedException exception = new PortOccupiedException("");
        
        assertNotNull(exception.getMessage());
    }

    @Test
    @DisplayName("测试构造器 - null密钥")
    void testConstructorWithNullKey() {
        PortOccupiedException exception = new PortOccupiedException(null);
        
        assertNotNull(exception.getMessage());
    }
}
