package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VersionChecker 测试")
class VersionCheckerTest {

    @Test
    @DisplayName("测试完全匹配 - 匹配成功")
    void testExactMatch_Success() {
        String clientVersion = "6.1.0";
        List<String> allowedVersions = Arrays.asList("5.0.0", "6.1.0", "7.0.0");

        assertTrue(VersionChecker.isVersionSupported(clientVersion, allowedVersions));
    }

    @Test
    @DisplayName("测试完全匹配 - 匹配失败")
    void testExactMatch_Failure() {
        String clientVersion = "4.0.0";
        List<String> allowedVersions = Arrays.asList("5.0.0", "6.1.0", "7.0.0");

        assertFalse(VersionChecker.isVersionSupported(clientVersion, allowedVersions));
    }

    @Test
    @DisplayName("测试通配符匹配 - X匹配任意数字")
    void testWildcardMatch_X() {
        String clientVersion = "5.11.5";
        List<String> allowedVersions = Collections.singletonList("5.11.X");

        assertTrue(VersionChecker.isVersionSupported(clientVersion, allowedVersions));
    }

    @Test
    @DisplayName("测试通配符匹配 - 小写x匹配任意数字")
    void testWildcardMatch_LowercaseX() {
        String clientVersion = "5.11.23";
        List<String> allowedVersions = Collections.singletonList("5.11.x");

        assertTrue(VersionChecker.isVersionSupported(clientVersion, allowedVersions));
    }

    @Test
    @DisplayName("测试通配符匹配 - 不匹配")
    void testWildcardMatch_NoMatch() {
        String clientVersion = "5.12.1";
        List<String> allowedVersions = Collections.singletonList("5.11.X");

        assertFalse(VersionChecker.isVersionSupported(clientVersion, allowedVersions));
    }

    @Test
    @DisplayName("测试空客户端版本")
    void testEmptyClientVersion() {
        assertFalse(VersionChecker.isVersionSupported("", Arrays.asList("6.1.0")));
        assertFalse(VersionChecker.isVersionSupported(null, Arrays.asList("6.1.0")));
    }

    @Test
    @DisplayName("测试空允许版本列表")
    void testEmptyAllowedVersions() {
        assertFalse(VersionChecker.isVersionSupported("6.1.0", null));
        assertFalse(VersionChecker.isVersionSupported("6.1.0", Collections.emptyList()));
    }

    @Test
    @DisplayName("测试大小写不敏感匹配")
    void testCaseInsensitiveMatch() {
        String clientVersion = "6.1.0";
        List<String> allowedVersions = Collections.singletonList("6.1.0");

        assertTrue(VersionChecker.isVersionSupported(clientVersion, allowedVersions));
    }

    @Test
    @DisplayName("测试多个通配符版本")
    void testMultipleWildcardVersions() {
        String clientVersion = "5.10.5";
        List<String> allowedVersions = Arrays.asList("5.9.X", "5.10.X", "5.11.X");

        assertTrue(VersionChecker.isVersionSupported(clientVersion, allowedVersions));
    }

    @Test
    @DisplayName("测试混合匹配 - 完全匹配和通配符")
    void testMixedMatch() {
        String clientVersion = "6.1.0";
        List<String> allowedVersions = Arrays.asList("5.X.X", "6.1.0", "7.X.X");

        assertTrue(VersionChecker.isVersionSupported(clientVersion, allowedVersions));
    }

    @Test
    @DisplayName("测试当前发布策略 - 仅兼容6.X.X和7.X.X客户端")
    void testCurrentReleaseCompatibilityPolicy() {
        List<String> allowedVersions = Arrays.asList("6.X.X", "7.X.X");

        assertTrue(VersionChecker.isVersionSupported("6.0.0", allowedVersions));
        assertTrue(VersionChecker.isVersionSupported("6.99.42", allowedVersions));
        assertTrue(VersionChecker.isVersionSupported("7.0.2", allowedVersions));
        assertTrue(VersionChecker.isVersionSupported("7.10.0", allowedVersions));
        assertFalse(VersionChecker.isVersionSupported("5.99.99", allowedVersions));
        assertFalse(VersionChecker.isVersionSupported("8.0.0", allowedVersions));
        assertFalse(VersionChecker.isVersionSupported("7.0.2-beta", allowedVersions));
    }

    @Test
    @DisplayName("测试通配符匹配主版本号")
    void testWildcardMatchMajorVersion() {
        String clientVersion = "5.5.5";
        List<String> allowedVersions = Collections.singletonList("5.X.X");

        assertTrue(VersionChecker.isVersionSupported(clientVersion, allowedVersions));
    }

    @Test
    @DisplayName("测试无效正则表达式不会崩溃")
    void testInvalidRegexDoesNotCrash() {
        String clientVersion = "6.1.0";
        List<String> allowedVersions = Collections.singletonList("6.1.[");

        assertDoesNotThrow(() -> VersionChecker.isVersionSupported(clientVersion, allowedVersions));
    }

    @Test
    @DisplayName("测试公共方法 isVersionSupported 存在")
    void testIsVersionSupportedMethodExists() throws Exception {
        Method method = VersionChecker.class.getDeclaredMethod("isVersionSupported", String.class, java.util.List.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 isVersionSupported - 完全匹配")
    void testIsVersionSupportedExactMatch() {
        List<String> allowedVersions = Arrays.asList("1.0.0", "2.0.0");
        assertTrue(VersionChecker.isVersionSupported("1.0.0", allowedVersions));
        assertTrue(VersionChecker.isVersionSupported("2.0.0", allowedVersions));
        assertFalse(VersionChecker.isVersionSupported("3.0.0", allowedVersions));
    }

    @Test
    @DisplayName("测试 isVersionSupported - 通配符匹配")
    void testIsVersionSupportedWildcard() {
        List<String> allowedVersions = Arrays.asList("5.11.X", "6.X.X");
        assertTrue(VersionChecker.isVersionSupported("5.11.1", allowedVersions));
        assertTrue(VersionChecker.isVersionSupported("5.11.23", allowedVersions));
        assertTrue(VersionChecker.isVersionSupported("6.0.0", allowedVersions));
        assertTrue(VersionChecker.isVersionSupported("6.1.2", allowedVersions));
        assertFalse(VersionChecker.isVersionSupported("7.0.0", allowedVersions));
    }

    @Test
    @DisplayName("测试 isVersionSupported - 大小写不敏感")
    void testIsVersionSupportedCaseInsensitive() {
        List<String> allowedVersions = Arrays.asList("1.0.0", "5.11.X");
        assertTrue(VersionChecker.isVersionSupported("1.0.0", allowedVersions));
        assertTrue(VersionChecker.isVersionSupported("1.0.0", allowedVersions));
        assertTrue(VersionChecker.isVersionSupported("5.11.x", allowedVersions));
        assertTrue(VersionChecker.isVersionSupported("5.11.X", allowedVersions));
    }

    @Test
    @DisplayName("测试 isVersionSupported - null参数")
    void testIsVersionSupportedNullParams() {
        List<String> allowedVersions = Arrays.asList("1.0.0");
        assertFalse(VersionChecker.isVersionSupported(null, allowedVersions));
        assertFalse(VersionChecker.isVersionSupported("1.0.0", null));
        assertFalse(VersionChecker.isVersionSupported(null, null));
    }

    @Test
    @DisplayName("测试 isVersionSupported - 空字符串")
    void testIsVersionSupportedEmptyString() {
        List<String> allowedVersions = Arrays.asList("1.0.0");
        assertFalse(VersionChecker.isVersionSupported("", allowedVersions));
        assertFalse(VersionChecker.isVersionSupported(" ", allowedVersions));
    }

    @Test
    @DisplayName("测试 isVersionSupported - 空列表")
    void testIsVersionSupportedEmptyList() {
        assertFalse(VersionChecker.isVersionSupported("1.0.0", Collections.emptyList()));
    }
}
