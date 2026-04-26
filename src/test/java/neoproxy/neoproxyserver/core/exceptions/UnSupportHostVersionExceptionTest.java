package neoproxy.neoproxyserver.core.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnSupportHostVersionException 测试")
class UnSupportHostVersionExceptionTest {

    @Test
    @DisplayName("测试throwException静态方法")
    void testThrowException() {
        String ip = "192.168.1.1";
        String version = "5.0.0";

        assertThrows(UnSupportHostVersionException.class, () ->
                UnSupportHostVersionException.throwException(ip, version)
        );
    }

    @Test
    @DisplayName("测试异常可以被捕获")
    void testExceptionCanBeCaught() {
        String ip = "10.0.0.1";
        String version = "6.0.0";

        try {
            UnSupportHostVersionException.throwException(ip, version);
            fail("应该抛出UnSupportHostVersionException");
        } catch (UnSupportHostVersionException e) {
            assertNotNull(e);
        }
    }

    @Test
    @DisplayName("测试异常继承自Exception")
    void testExceptionInheritance() {
        assertDoesNotThrow(() -> {
            try {
                UnSupportHostVersionException.throwException("127.0.0.1", "1.0");
            } catch (Exception e) {
                assertTrue(e instanceof UnSupportHostVersionException);
            }
        });
    }

    @Test
    @DisplayName("测试异常消息为空（私有构造器不设置消息）")
    void testExceptionMessage() throws UnSupportHostVersionException {
        try {
            UnSupportHostVersionException.throwException("localhost", "test-version");
        } catch (UnSupportHostVersionException e) {
            assertNull(e.getMessage());
        }
    }

    @Test
    @DisplayName("测试构造器 - 空参数")
    void testConstructorWithEmptyParams() {
        assertDoesNotThrow(() -> {
            try {
                UnSupportHostVersionException.throwException("", "");
            } catch (UnSupportHostVersionException e) {
                assertTrue(e instanceof UnSupportHostVersionException);
            }
        });
    }

    @Test
    @DisplayName("测试构造器 - null参数")
    void testConstructorWithNullParams() {
        assertDoesNotThrow(() -> {
            try {
                UnSupportHostVersionException.throwException(null, null);
            } catch (UnSupportHostVersionException e) {
                assertTrue(e instanceof UnSupportHostVersionException);
            }
        });
    }
}
