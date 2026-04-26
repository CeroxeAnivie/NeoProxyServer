package neoproxy.neoproxyserver.core.management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IPChecker 测试")
class IPCheckerTest {

    @Test
    @DisplayName("测试私有构造器")
    void testPrivateConstructor() throws Exception {
        Constructor<IPChecker> constructor = IPChecker.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        IPChecker instance = constructor.newInstance();

        assertNotNull(instance);
    }

    @Test
    @DisplayName("测试常量定义")
    void testConstants() {
        assertEquals(0, IPChecker.DO_BAN);
        assertEquals(1, IPChecker.UNBAN);
        assertEquals(2, IPChecker.CHECK_IS_BAN);
    }

    @Test
    @DisplayName("测试isValidIP - 有效IPv4")
    void testIsValidIP_ValidIPv4() {
        assertTrue(IPChecker.isValidIP("192.168.1.1"));
        assertTrue(IPChecker.isValidIP("10.0.0.1"));
        assertTrue(IPChecker.isValidIP("127.0.0.1"));
        assertTrue(IPChecker.isValidIP("8.8.8.8"));
        assertTrue(IPChecker.isValidIP("255.255.255.255"));
    }

    @Test
    @DisplayName("测试isValidIP - 无效IP")
    void testIsValidIP_InvalidIP() {
        assertFalse(IPChecker.isValidIP(null));
        assertFalse(IPChecker.isValidIP(""));
        assertFalse(IPChecker.isValidIP("   "));
        assertFalse(IPChecker.isValidIP("256.1.1.1"));
        assertFalse(IPChecker.isValidIP("1.1.1"));
        assertFalse(IPChecker.isValidIP("1.1.1.1.1"));
        assertFalse(IPChecker.isValidIP("abc.def.ghi.jkl"));
        assertFalse(IPChecker.isValidIP("1.1.1.-1"));
    }

    @Test
    @DisplayName("测试getBannedIPs方法")
    void testGetBannedIPs() {
        assertNotNull(IPChecker.getBannedIPs());
    }

    @Test
    @DisplayName("测试exec方法 - CHECK_IS_BAN模式")
    void testExec_CheckIsBan() {
        boolean result = IPChecker.exec("8.8.8.8", IPChecker.CHECK_IS_BAN);
        assertFalse(result);
    }

    @Test
    @DisplayName("测试exec方法 - null参数")
    void testExec_NullIP() {
        assertFalse(IPChecker.exec(null, IPChecker.CHECK_IS_BAN));
    }

    @Test
    @DisplayName("测试exec方法 - 空字符串参数")
    void testExec_EmptyIP() {
        assertFalse(IPChecker.exec("", IPChecker.CHECK_IS_BAN));
    }

    @Test
    @DisplayName("测试exec方法 - 无效模式")
    void testExec_InvalidMode() {
        assertFalse(IPChecker.exec("8.8.8.8", 999));
    }

    @Test
    @DisplayName("测试listBannedIPs方法")
    void testListBannedIPs() {
        assertDoesNotThrow(() -> IPChecker.listBannedIPs());
    }

    @Test
    @DisplayName("测试BanInfo类")
    void testBanInfo() {
        IPChecker.BanInfo info = new IPChecker.BanInfo("192.168.1.1", "Beijing", "China Telecom");

        assertEquals("192.168.1.1", info.ip);
        assertEquals("Beijing", info.location);
        assertEquals("China Telecom", info.isp);
    }
}
