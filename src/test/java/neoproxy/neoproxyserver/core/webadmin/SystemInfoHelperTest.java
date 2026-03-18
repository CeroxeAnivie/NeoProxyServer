package neoproxy.neoproxyserver.core.webadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SystemInfoHelper 测试")
class SystemInfoHelperTest {

    @Test
    @DisplayName("测试私有构造器")
    void testPrivateConstructor() throws Exception {
        Constructor<SystemInfoHelper> constructor = SystemInfoHelper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        
        SystemInfoHelper instance = constructor.newInstance();
        
        assertNotNull(instance);
    }

    @Test
    @DisplayName("测试getSystemSnapshotJson方法")
    void testGetSystemSnapshotJson() {
        String json = SystemInfoHelper.getSystemSnapshotJson();
        
        assertNotNull(json);
        assertTrue(json.contains("cpu"));
        assertTrue(json.contains("mem"));
        assertTrue(json.contains("disk"));
        assertTrue(json.contains("os"));
    }

    @Test
    @DisplayName("测试getPortUsageJson方法")
    void testGetPortUsageJson() {
        String json = SystemInfoHelper.getPortUsageJson();
        
        assertNotNull(json);
        assertTrue(json.contains("tcp"));
        assertTrue(json.contains("udp"));
    }
}
