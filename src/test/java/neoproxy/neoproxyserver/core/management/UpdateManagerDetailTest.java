package neoproxy.neoproxyserver.core.management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UpdateManager 详细测试")
class UpdateManagerDetailTest {

    @Test
    @DisplayName("测试公共方法 init 存在")
    void testInitMethodExists() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("init");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 handle 存在")
    void testHandleMethodExists() throws Exception {
        Method method = UpdateManager.class.getDeclaredMethod("handle", neoproxy.neoproxyserver.core.HostClient.class);
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }
}
