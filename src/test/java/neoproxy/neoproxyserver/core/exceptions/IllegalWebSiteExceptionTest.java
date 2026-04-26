package neoproxy.neoproxyserver.core.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IllegalWebSiteException 测试")
class IllegalWebSiteExceptionTest {

    @Test
    @DisplayName("测试throwException静态方法")
    void testThrowException() {
        String accessCode = "malicious-site.com";

        assertThrows(IllegalWebSiteException.class, () ->
                IllegalWebSiteException.throwException(accessCode)
        );
    }

    @Test
    @DisplayName("测试异常可以被捕获并获取消息")
    void testExceptionCanBeCaught() {
        String accessCode = "blocked-domain.com";

        try {
            IllegalWebSiteException.throwException(accessCode);
            fail("应该抛出IllegalWebSiteException");
        } catch (IllegalWebSiteException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    @DisplayName("测试异常继承自IOException")
    void testExceptionInheritance() {
        assertDoesNotThrow(() -> {
            try {
                IllegalWebSiteException.throwException("test.com");
            } catch (IOException e) {
                assertTrue(e instanceof IllegalWebSiteException);
            }
        });
    }

    @Test
    @DisplayName("测试异常是IOException")
    void testIsIOException() {
        try {
            IllegalWebSiteException.throwException("test.com");
        } catch (IllegalWebSiteException e) {
            assertTrue(e instanceof IOException);
        } catch (IOException e) {
            fail("应该是IllegalWebSiteException");
        }
    }

    @Test
    @DisplayName("测试构造器 - 空访问码")
    void testConstructorWithEmptyAccessCode() {
        assertDoesNotThrow(() -> {
            try {
                IllegalWebSiteException.throwException("");
            } catch (IllegalWebSiteException e) {
                assertNotNull(e.getMessage());
            }
        });
    }

    @Test
    @DisplayName("测试构造器 - null访问码")
    void testConstructorWithNullAccessCode() {
        assertDoesNotThrow(() -> {
            try {
                IllegalWebSiteException.throwException(null);
            } catch (IllegalWebSiteException e) {
                assertNotNull(e);
            }
        });
    }
}
