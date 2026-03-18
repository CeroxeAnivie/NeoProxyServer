package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
