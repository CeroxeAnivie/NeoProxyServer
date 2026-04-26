package neoproxy.neoproxyserver.core.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidationPatterns 验证正则类测试")
class ValidationPatternsTest {

    @Test
    @DisplayName("测试私有构造器抛出AssertionError")
    void testPrivateConstructorThrowsException() throws Exception {
        Constructor<ValidationPatterns> constructor = ValidationPatterns.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException exception = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertTrue(exception.getCause() instanceof AssertionError);
        assertTrue(exception.getCause().getMessage().contains("常量类禁止实例化"));
    }

    @Test
    @DisplayName("测试IPv4验证 - 有效地址")
    void testIsValidIPv4_Valid() {
        assertTrue(ValidationPatterns.isValidIPv4("192.168.1.1"));
        assertTrue(ValidationPatterns.isValidIPv4("10.0.0.1"));
        assertTrue(ValidationPatterns.isValidIPv4("127.0.0.1"));
        assertTrue(ValidationPatterns.isValidIPv4("255.255.255.255"));
        assertTrue(ValidationPatterns.isValidIPv4("0.0.0.0"));
        assertTrue(ValidationPatterns.isValidIPv4("1.2.3.4"));
    }

    @Test
    @DisplayName("测试IPv4验证 - 无效地址")
    void testIsValidIPv4_Invalid() {
        assertFalse(ValidationPatterns.isValidIPv4("256.1.1.1"));
        assertFalse(ValidationPatterns.isValidIPv4("192.168.1"));
        assertFalse(ValidationPatterns.isValidIPv4("192.168.1.1.1"));
        assertFalse(ValidationPatterns.isValidIPv4("abc.def.ghi.jkl"));
        assertFalse(ValidationPatterns.isValidIPv4("192.168.1.1a"));
        assertFalse(ValidationPatterns.isValidIPv4(""));
        assertFalse(ValidationPatterns.isValidIPv4(null));
    }

    @Test
    @DisplayName("测试端口验证 - 有效端口")
    void testIsValidPort_Valid() {
        assertTrue(ValidationPatterns.isValidPort(1));
        assertTrue(ValidationPatterns.isValidPort(80));
        assertTrue(ValidationPatterns.isValidPort(443));
        assertTrue(ValidationPatterns.isValidPort(8080));
        assertTrue(ValidationPatterns.isValidPort(65535));
    }

    @Test
    @DisplayName("测试端口验证 - 无效端口")
    void testIsValidPort_Invalid() {
        assertFalse(ValidationPatterns.isValidPort(0));
        assertFalse(ValidationPatterns.isValidPort(-1));
        assertFalse(ValidationPatterns.isValidPort(65536));
        assertFalse(ValidationPatterns.isValidPort(100000));
    }

    @Test
    @DisplayName("测试访问密钥验证 - 有效密钥")
    void testIsValidAccessKey_Valid() {
        assertTrue(ValidationPatterns.isValidAccessKey("abcdef"));
        assertTrue(ValidationPatterns.isValidAccessKey("ABCDEF"));
        assertTrue(ValidationPatterns.isValidAccessKey("123456"));
        assertTrue(ValidationPatterns.isValidAccessKey("abc123XYZ"));
        assertTrue(ValidationPatterns.isValidAccessKey("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abcde", "12345", "abc!", "abc@123", "a b c", "abcdefg hijklmn"})
    @DisplayName("测试访问密钥验证 - 无效密钥")
    void testIsValidAccessKey_Invalid(String key) {
        assertFalse(ValidationPatterns.isValidAccessKey(key));
    }

    @Test
    @DisplayName("测试访问密钥验证 - 超长密钥")
    void testIsValidAccessKey_TooLong() {
        String longKey = "a".repeat(33);
        assertFalse(ValidationPatterns.isValidAccessKey(longKey));
    }

    @Test
    @DisplayName("测试Pattern对象不为空")
    void testPatternsNotNull() {
        assertNotNull(ValidationPatterns.IPV4_PATTERN);
        assertNotNull(ValidationPatterns.IPV6_PATTERN);
        assertNotNull(ValidationPatterns.PORT_PATTERN);
        assertNotNull(ValidationPatterns.ACCESS_KEY_PATTERN);
        assertNotNull(ValidationPatterns.DOMAIN_PATTERN);
        assertNotNull(ValidationPatterns.EMAIL_PATTERN);
    }
}
