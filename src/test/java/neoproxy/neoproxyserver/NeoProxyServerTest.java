package neoproxy.neoproxyserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NeoProxyServer 测试")
class NeoProxyServerTest {

    @Test
    @DisplayName("测试VERSION常量")
    void testVersionConstant() {
        assertNotNull(NeoProxyServer.VERSION);
    }

    @Test
    @DisplayName("测试EXPECTED_CLIENT_VERSION常量")
    void testExpectedClientVersionConstant() {
        assertNotNull(NeoProxyServer.EXPECTED_CLIENT_VERSION);
    }

    @Test
    @DisplayName("测试HOST_HOOK_PORT初始值")
    void testHostHookPortInitialValue() {
        assertEquals(44801, NeoProxyServer.HOST_HOOK_PORT);
    }

    @Test
    @DisplayName("测试HOST_CONNECT_PORT初始值")
    void testHostConnectPortInitialValue() {
        assertEquals(44802, NeoProxyServer.HOST_CONNECT_PORT);
    }

    @Test
    @DisplayName("测试LOCAL_DOMAIN_NAME初始值")
    void testLocalDomainNameInitialValue() {
        assertEquals("localhost", NeoProxyServer.LOCAL_DOMAIN_NAME);
    }

    @Test
    @DisplayName("测试IS_DEBUG_MODE初始值")
    void testIsDebugModeInitialValue() {
        assertFalse(NeoProxyServer.IS_DEBUG_MODE);
    }

    @Test
    @DisplayName("测试isStopped初始值")
    void testIsStoppedInitialValue() {
        assertFalse(NeoProxyServer.isStopped);
    }

    @Test
    @DisplayName("测试availableHostClient初始值")
    void testAvailableHostClientInitialValue() {
        assertNotNull(NeoProxyServer.availableHostClient);
        assertTrue(NeoProxyServer.availableHostClient.isEmpty());
    }

    @Test
    @DisplayName("测试availableVersions初始值")
    void testAvailableVersionsInitialValue() {
        assertNotNull(NeoProxyServer.availableVersions);
    }

    @Test
    @DisplayName("测试ASCII_LOGO常量")
    void testAsciiLogoConstant() {
        assertNotNull(NeoProxyServer.ASCII_LOGO);
        assertTrue(NeoProxyServer.ASCII_LOGO.length() > 0);
    }

    @Test
    @DisplayName("测试CURRENT_DIR_PATH常量")
    void testCurrentDirPathConstant() {
        assertNotNull(NeoProxyServer.CURRENT_DIR_PATH);
    }

    @Test
    @DisplayName("测试TOTAL_BYTES_COUNTER常量")
    void testTotalBytesCounterConstant() {
        assertNotNull(NeoProxyServer.TOTAL_BYTES_COUNTER);
    }
}
