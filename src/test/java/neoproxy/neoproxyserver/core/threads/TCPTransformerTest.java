package neoproxy.neoproxyserver.core.threads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TCPTransformer 测试")
class TCPTransformerTest {

    @Test
    @DisplayName("测试TELL_BALANCE_MIB常量")
    void testTellBalanceMib() {
        assertEquals(10, TCPTransformer.TELL_BALANCE_MIB);
    }

    @Test
    @DisplayName("测试BUFFER_LEN常量")
    void testBufferLen() {
        assertEquals(65535, TCPTransformer.BUFFER_LEN);
    }

    @Test
    @DisplayName("测试CUSTOM_BLOCKING_MESSAGE常量")
    void testCustomBlockingMessage() {
        assertNotNull(TCPTransformer.CUSTOM_BLOCKING_MESSAGE);
    }

    @Test
    @DisplayName("测试静态初始化块 - FORBIDDEN_HTML_TEMPLATE")
    void testForbiddenHtmlTemplate() throws Exception {
        java.lang.reflect.Field field = TCPTransformer.class.getDeclaredField("FORBIDDEN_HTML_TEMPLATE");
        field.setAccessible(true);
        String template = (String) field.get(null);
        
        assertNotNull(template);
        assertTrue(template.contains("{{CUSTOM_MESSAGE}}"));
    }
}
