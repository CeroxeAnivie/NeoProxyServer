package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VersionChecker 详细测试")
class VersionCheckerDetailTest {

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
