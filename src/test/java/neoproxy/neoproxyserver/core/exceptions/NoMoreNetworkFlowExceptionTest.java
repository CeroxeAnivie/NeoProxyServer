package neoproxy.neoproxyserver.core.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NoMoreNetworkFlowException 测试")
class NoMoreNetworkFlowExceptionTest {

    @Test
    @DisplayName("测试throwException无参数")
    void testThrowException_NoArgs() {
        assertThrows(NoMoreNetworkFlowException.class, NoMoreNetworkFlowException::throwException);
    }

    @Test
    @DisplayName("测试throwException带参数")
    void testThrowException_WithArgs() {
        String source = "TestSource";
        String messageKey = "exception.insufficientBalance";
        String keyName = "test-key";
        
        assertThrows(NoMoreNetworkFlowException.class, () -> 
            NoMoreNetworkFlowException.throwException(source, messageKey, keyName)
        );
    }

    @Test
    @DisplayName("测试异常可以被捕获并获取消息 - 无参数")
    void testExceptionCanBeCaught_NoArgs() {
        try {
            NoMoreNetworkFlowException.throwException();
            fail("应该抛出NoMoreNetworkFlowException");
        } catch (NoMoreNetworkFlowException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    @DisplayName("测试异常继承自RuntimeException")
    void testExceptionInheritance() {
        assertDoesNotThrow(() -> {
            try {
                NoMoreNetworkFlowException.throwException();
            } catch (RuntimeException e) {
                assertTrue(e instanceof NoMoreNetworkFlowException);
            }
        });
    }

    @Test
    @DisplayName("测试异常是RuntimeException")
    void testIsRuntimeException() {
        try {
            NoMoreNetworkFlowException.throwException();
        } catch (NoMoreNetworkFlowException e) {
            assertTrue(e instanceof RuntimeException);
        }
    }
}
