package neoproxy.neoproxyserver.core.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BlockingMessageException 测试")
class BlockingMessageExceptionTest {

    @Test
    @DisplayName("测试构造器 - 带消息")
    void testConstructorWithMessage() {
        String message = "测试阻塞消息";
        BlockingMessageException exception = new BlockingMessageException(message);
        
        assertEquals(message, exception.getMessage());
        assertEquals(message, exception.getCustomMessage());
    }

    @Test
    @DisplayName("测试构造器 - 空消息")
    void testConstructorWithEmptyMessage() {
        BlockingMessageException exception = new BlockingMessageException("");
        
        assertEquals("", exception.getMessage());
        assertEquals("", exception.getCustomMessage());
    }

    @Test
    @DisplayName("测试构造器 - null消息")
    void testConstructorWithNullMessage() {
        BlockingMessageException exception = new BlockingMessageException(null);
        
        assertNull(exception.getMessage());
        assertNull(exception.getCustomMessage());
    }

    @Test
    @DisplayName("测试getCustomMessage返回正确值")
    void testGetCustomMessage() {
        String expected = "自定义错误提示";
        BlockingMessageException exception = new BlockingMessageException(expected);
        
        assertEquals(expected, exception.getCustomMessage());
    }

    @Test
    @DisplayName("测试异常可以被抛出和捕获")
    void testExceptionCanBeThrownAndCaught() {
        String message = "测试异常抛出";
        
        assertThrows(BlockingMessageException.class, () -> {
            throw new BlockingMessageException(message);
        });
    }

    @Test
    @DisplayName("测试异常继承自Exception")
    void testExceptionInheritance() {
        BlockingMessageException exception = new BlockingMessageException("test");
        
        assertTrue(exception instanceof Exception);
    }
}
