package neoproxy.neoproxyserver.core.management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IPGeolocationHelper 测试")
class IPGeolocationHelperTest {

    @Test
    @DisplayName("测试getLocationInfo方法 - null IP")
    void testGetLocationInfo_NullIp() {
        IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.getLocationInfo(null);
        
        assertFalse(info.success());
        assertEquals("N/A", info.location());
    }

    @Test
    @DisplayName("测试getLocationInfo方法 - 空IP")
    void testGetLocationInfo_EmptyIp() {
        IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.getLocationInfo("");
        
        assertFalse(info.success());
    }

    @Test
    @DisplayName("测试getLocationInfo方法 - 空白IP")
    void testGetLocationInfo_BlankIp() {
        IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.getLocationInfo("   ");
        
        assertFalse(info.success());
    }

    @Test
    @DisplayName("测试getLocationInfo方法 - 内网IP 127.0.0.1")
    void testGetLocationInfo_Localhost() {
        IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.getLocationInfo("127.0.0.1");
        
        assertTrue(info.success());
        assertEquals("Localhost", info.location());
        assertEquals("Intranet", info.isp());
    }

    @Test
    @DisplayName("测试getLocationInfo方法 - 内网IP 192.168.x.x")
    void testGetLocationInfo_InternalIp() {
        IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.getLocationInfo("192.168.1.1");
        
        assertTrue(info.success());
        assertEquals("Localhost", info.location());
    }

    @Test
    @DisplayName("测试getLocationInfo方法 - 内网IP 10.x.x.x")
    void testGetLocationInfo_InternalIp10() {
        IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.getLocationInfo("10.0.0.1");
        
        assertTrue(info.success());
        assertEquals("Localhost", info.location());
    }

    @Test
    @DisplayName("测试getLocationInfo方法 - IPv6 localhost")
    void testGetLocationInfo_Ipv6Localhost() {
        IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.getLocationInfo("::1");
        
        assertTrue(info.success());
        assertEquals("Localhost", info.location());
    }

    @Test
    @DisplayName("测试LocationInfo record")
    void testLocationInfoRecord() {
        IPGeolocationHelper.LocationInfo info = new IPGeolocationHelper.LocationInfo("中国 广东 深圳", "电信", true, "Ip2region");
        
        assertEquals("中国 广东 深圳", info.location());
        assertEquals("电信", info.isp());
        assertTrue(info.success());
        assertEquals("Ip2region", info.source());
    }

    @Test
    @DisplayName("测试LocationInfo.failed方法")
    void testLocationInfoFailed() {
        IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.LocationInfo.failed();
        
        assertFalse(info.success());
        assertEquals("N/A", info.location());
        assertEquals("N/A", info.isp());
        assertEquals("Failed", info.source());
    }

    @Test
    @DisplayName("测试LocationInfo.failed方法 - 带原因")
    void testLocationInfoFailedWithReason() {
        IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.LocationInfo.failed("Test Reason");
        
        assertFalse(info.success());
        assertEquals("Test Reason", info.source());
    }

    @Test
    @DisplayName("测试shutdown方法")
    void testShutdown() {
        assertDoesNotThrow(() -> IPGeolocationHelper.shutdown());
    }
}
