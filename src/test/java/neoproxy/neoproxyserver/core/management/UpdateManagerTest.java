package neoproxy.neoproxyserver.core.management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UpdateManager 测试")
class UpdateManagerTest {

    @Test
    @DisplayName("测试私有构造器")
    void testPrivateConstructor() throws Exception {
        Constructor<UpdateManager> constructor = UpdateManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        
        UpdateManager instance = constructor.newInstance();
        
        assertNotNull(instance);
    }

    @Test
    @DisplayName("测试init方法")
    void testInit() {
        assertDoesNotThrow(() -> UpdateManager.init());
    }
}
