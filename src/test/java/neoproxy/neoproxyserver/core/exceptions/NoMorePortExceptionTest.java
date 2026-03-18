package neoproxy.neoproxyserver.core.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NoMorePortException 测试")
class NoMorePortExceptionTest {

    @Test
    @DisplayName("测试throwException无参数 - 动态端口耗尽")
    void testThrowException_NoArgs() {
        assertThrows(NoMorePortException.class, NoMorePortException::throwException);
    }

    @Test
    @DisplayName("测试throwException单参数 - 特定端口被占用")
    void testThrowException_SinglePort() {
        int port = 8080;
        
        assertThrows(NoMorePortException.class, () -> 
            NoMorePortException.throwException(port)
        );
    }

    @Test
    @DisplayName("测试throwException双参数 - 端口范围耗尽")
    void testThrowException_PortRange() {
        int start = 10000;
        int end = 20000;
        
        assertThrows(NoMorePortException.class, () -> 
            NoMorePortException.throwException(start, end)
        );
    }

    @Test
    @DisplayName("测试异常可以被捕获并获取消息 - 无参数")
    void testExceptionCanBeCaught_NoArgs() {
        try {
            NoMorePortException.throwException();
            fail("应该抛出NoMorePortException");
        } catch (NoMorePortException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    @DisplayName("测试异常可以被捕获并获取消息 - 单参数")
    void testExceptionCanBeCaught_SinglePort() {
        int port = 443;
        
        try {
            NoMorePortException.throwException(port);
            fail("应该抛出NoMorePortException");
        } catch (NoMorePortException e) {
            assertNotNull(e.getMessage());
            assertTrue(e.getMessage().contains(String.valueOf(port)));
        }
    }

    @Test
    @DisplayName("测试异常可以被捕获并获取消息 - 双参数")
    void testExceptionCanBeCaught_PortRange() {
        int start = 10000;
        int end = 20000;
        
        try {
            NoMorePortException.throwException(start, end);
            fail("应该抛出NoMorePortException");
        } catch (NoMorePortException e) {
            assertNotNull(e.getMessage());
            assertTrue(e.getMessage().contains(String.valueOf(start)));
            assertTrue(e.getMessage().contains(String.valueOf(end)));
        }
    }

    @Test
    @DisplayName("测试异常继承自Exception")
    void testExceptionInheritance() {
        assertDoesNotThrow(() -> {
            try {
                NoMorePortException.throwException();
            } catch (Exception e) {
                assertTrue(e instanceof NoMorePortException);
            }
        });
    }
}
