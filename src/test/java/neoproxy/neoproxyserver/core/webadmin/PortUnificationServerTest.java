package neoproxy.neoproxyserver.core.webadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PortUnificationServer 测试")
class PortUnificationServerTest {

    @Test
    @DisplayName("测试构造器")
    void testConstructor() {
        PortUnificationServer server = new PortUnificationServer(8080, 9090);
        
        assertNotNull(server);
    }

    @Test
    @DisplayName("测试stop方法 - 未启动时")
    void testStop_NotStarted() {
        PortUnificationServer server = new PortUnificationServer(8080, 9090);
        
        assertDoesNotThrow(() -> server.stop());
    }
}
