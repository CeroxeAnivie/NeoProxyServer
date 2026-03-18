package neoproxy.neoproxyserver.core.management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TransferSocketAdapter 测试")
class TransferSocketAdapterTest {

    @Test
    @DisplayName("测试CONN_TYPE常量")
    void testConnTypeConstants() {
        assertEquals(0, TransferSocketAdapter.CONN_TYPE.TCP);
        assertEquals(1, TransferSocketAdapter.CONN_TYPE.UDP);
    }

    @Test
    @DisplayName("测试SO_TIMEOUT初始值")
    void testSoTimeoutInitialValue() {
        assertEquals(5000, TransferSocketAdapter.SO_TIMEOUT);
    }

    @Test
    @DisplayName("测试CONN_TYPE私有构造器")
    void testConnTypePrivateConstructor() throws Exception {
        Constructor<TransferSocketAdapter.CONN_TYPE> constructor = 
            TransferSocketAdapter.CONN_TYPE.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        
        TransferSocketAdapter.CONN_TYPE instance = constructor.newInstance();
        
        assertNotNull(instance);
    }

    @Test
    @DisplayName("测试TransferSocketAdapter构造器")
    void testConstructor() throws Exception {
        Constructor<TransferSocketAdapter> constructor = TransferSocketAdapter.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        
        TransferSocketAdapter adapter = constructor.newInstance();
        
        assertNotNull(adapter);
    }

    @Test
    @DisplayName("测试TransferSocketAdapter实现Runnable")
    void testImplementsRunnable() {
        assertTrue(Runnable.class.isAssignableFrom(TransferSocketAdapter.class));
    }
}
