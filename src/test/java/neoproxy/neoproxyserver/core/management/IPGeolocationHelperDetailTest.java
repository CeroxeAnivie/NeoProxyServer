package neoproxy.neoproxyserver.core.management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IPGeolocationHelper 详细测试")
class IPGeolocationHelperDetailTest {

    @Test
    @DisplayName("测试静态常量 FILE_V4")
    void testFileV4Constant() throws Exception {
        Field field = IPGeolocationHelper.class.getDeclaredField("FILE_V4");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("ip2region_v4.xdb", value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 FILE_V6")
    void testFileV6Constant() throws Exception {
        Field field = IPGeolocationHelper.class.getDeclaredField("FILE_V6");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("ip2region_v6.xdb", value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 searcherV4")
    void testSearcherV4Variable() throws Exception {
        Field field = IPGeolocationHelper.class.getDeclaredField("searcherV4");
        field.setAccessible(true);
        assertEquals(org.lionsoul.ip2region.xdb.Searcher.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 searcherV6")
    void testSearcherV6Variable() throws Exception {
        Field field = IPGeolocationHelper.class.getDeclaredField("searcherV6");
        field.setAccessible(true);
        assertEquals(org.lionsoul.ip2region.xdb.Searcher.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 v4Loaded")
    void testV4LoadedVariable() throws Exception {
        Field field = IPGeolocationHelper.class.getDeclaredField("v4Loaded");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态变量 v6Loaded")
    void testV6LoadedVariable() throws Exception {
        Field field = IPGeolocationHelper.class.getDeclaredField("v6Loaded");
        field.setAccessible(true);
        assertEquals(boolean.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试私有方法 initialize 存在")
    void testInitializeMethodExists() throws Exception {
        Method method = IPGeolocationHelper.class.getDeclaredMethod("initialize");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 loadFileToBytes 存在")
    void testLoadFileToBytesMethodExists() throws Exception {
        Method method = IPGeolocationHelper.class.getDeclaredMethod("loadFileToBytes", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(byte[].class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 getLocationInfo 存在")
    void testGetLocationInfoMethodExists() throws Exception {
        Method method = IPGeolocationHelper.class.getDeclaredMethod("getLocationInfo", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(IPGeolocationHelper.LocationInfo.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 parseRegionStr 存在")
    void testParseRegionStrMethodExists() throws Exception {
        Method method = IPGeolocationHelper.class.getDeclaredMethod("parseRegionStr", String.class, String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(IPGeolocationHelper.LocationInfo.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 isInternalIp 存在")
    void testIsInternalIpMethodExists() throws Exception {
        Method method = IPGeolocationHelper.class.getDeclaredMethod("isInternalIp", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 shutdown 存在")
    void testShutdownMethodExists() throws Exception {
        Method method = IPGeolocationHelper.class.getDeclaredMethod("shutdown");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 LocationInfo record 存在")
    void testLocationInfoRecordExists() {
        assertNotNull(IPGeolocationHelper.LocationInfo.class);
        assertTrue(java.lang.reflect.Modifier.isStatic(IPGeolocationHelper.LocationInfo.class.getModifiers()));
    }

    @Test
    @DisplayName("测试 LocationInfo record 字段")
    void testLocationInfoRecordFields() throws Exception {
        Class<?> locationInfoClass = IPGeolocationHelper.LocationInfo.class;
        
        java.lang.reflect.RecordComponent[] components = locationInfoClass.getRecordComponents();
        assertEquals(4, components.length);
        
        assertEquals("location", components[0].getName());
        assertEquals(String.class, components[0].getType());
        
        assertEquals("isp", components[1].getName());
        assertEquals(String.class, components[1].getType());
        
        assertEquals("success", components[2].getName());
        assertEquals(boolean.class, components[2].getType());
        
        assertEquals("source", components[3].getName());
        assertEquals(String.class, components[3].getType());
    }

    @Test
    @DisplayName("测试 LocationInfo.failed 方法")
    void testLocationInfoFailedMethod() {
        IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.LocationInfo.failed();
        assertNotNull(info);
        assertEquals("N/A", info.location());
        assertEquals("N/A", info.isp());
        assertFalse(info.success());
        assertEquals("Failed", info.source());
    }

    @Test
    @DisplayName("测试 LocationInfo.failed 方法带原因")
    void testLocationInfoFailedWithReasonMethod() {
        IPGeolocationHelper.LocationInfo info = IPGeolocationHelper.LocationInfo.failed("Test Reason");
        assertNotNull(info);
        assertEquals("N/A", info.location());
        assertEquals("N/A", info.isp());
        assertFalse(info.success());
        assertEquals("Test Reason", info.source());
    }
}
