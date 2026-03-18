package neoproxy.neoproxyserver.core.webadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SystemInfoHelper 详细测试")
class SystemInfoHelperDetailTest {

    @Test
    @DisplayName("测试静态常量 si")
    void testSiConstant() throws Exception {
        Field field = SystemInfoHelper.class.getDeclaredField("si");
        field.setAccessible(true);
        assertEquals(oshi.SystemInfo.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 hal")
    void testHalConstant() throws Exception {
        Field field = SystemInfoHelper.class.getDeclaredField("hal");
        field.setAccessible(true);
        assertEquals(oshi.hardware.HardwareAbstractionLayer.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 cpuModelName")
    void testCpuModelNameConstant() throws Exception {
        Field field = SystemInfoHelper.class.getDeclaredField("cpuModelName");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 os")
    void testOsConstant() throws Exception {
        Field field = SystemInfoHelper.class.getDeclaredField("os");
        field.setAccessible(true);
        assertEquals(oshi.software.os.OperatingSystem.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试静态常量 osVersionStr")
    void testOsVersionStrConstant() throws Exception {
        Field field = SystemInfoHelper.class.getDeclaredField("osVersionStr");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试公共方法 getSystemSnapshotJson 存在")
    void testGetSystemSnapshotJsonMethodExists() throws Exception {
        Method method = SystemInfoHelper.class.getDeclaredMethod("getSystemSnapshotJson");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 getPortUsageJson 存在")
    void testGetPortUsageJsonMethodExists() throws Exception {
        Method method = SystemInfoHelper.class.getDeclaredMethod("getPortUsageJson");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 getIpString 存在")
    void testGetIpStringMethodExists() throws Exception {
        Method method = SystemInfoHelper.class.getDeclaredMethod("getIpString", byte[].class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 listToJson 存在")
    void testListToJsonMethodExists() throws Exception {
        Method method = SystemInfoHelper.class.getDeclaredMethod("listToJson", java.util.List.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试私有方法 escapeJson 存在")
    void testEscapeJsonMethodExists() throws Exception {
        Method method = SystemInfoHelper.class.getDeclaredMethod("escapeJson", String.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试 getSystemSnapshotJson 返回有效的JSON")
    void testGetSystemSnapshotJsonReturnsValidJson() {
        String json = SystemInfoHelper.getSystemSnapshotJson();
        assertNotNull(json);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"cpu\""));
        assertTrue(json.contains("\"mem\""));
        assertTrue(json.contains("\"disk\""));
        assertTrue(json.contains("\"os\""));
    }

    @Test
    @DisplayName("测试 getPortUsageJson 返回有效的JSON")
    void testGetPortUsageJsonReturnsValidJson() {
        String json = SystemInfoHelper.getPortUsageJson();
        assertNotNull(json);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"tcp\""));
        assertTrue(json.contains("\"udp\""));
    }
}
