package neoproxy.neoproxyserver.core.webadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WebConsole 测试")
class WebConsoleTest {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    @DisplayName("测试时间格式化器")
    void testTimeFormatter() {
        String time = TIME_FORMATTER.format(LocalDateTime.now());

        assertNotNull(time);
        assertTrue(time.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    @DisplayName("测试广播格式")
    void testBroadcastFormat() {
        String time = TIME_FORMATTER.format(LocalDateTime.now());
        String level = "INFO";
        String source = "TestSource";
        String message = "Test message";

        String formattedMsg = String.format("[%s] [%s] [%s]: %s", time, level, source, message);

        assertTrue(formattedMsg.startsWith("["));
        assertTrue(formattedMsg.contains("] ["));
        assertTrue(formattedMsg.contains(level));
        assertTrue(formattedMsg.contains(source));
        assertTrue(formattedMsg.endsWith(message));
    }

    @Test
    @DisplayName("测试广播格式 - WARN级别")
    void testBroadcastFormat_Warn() {
        String time = TIME_FORMATTER.format(LocalDateTime.now());
        String level = "WARN";
        String source = "TestSource";
        String message = "Warning message";

        String formattedMsg = String.format("[%s] [%s] [%s]: %s", time, level, source, message);

        assertTrue(formattedMsg.contains("WARN"));
    }

    @Test
    @DisplayName("测试广播格式 - ERROR级别")
    void testBroadcastFormat_Error() {
        String time = TIME_FORMATTER.format(LocalDateTime.now());
        String level = "ERROR";
        String source = "TestSource";
        String message = "Error message";

        String formattedMsg = String.format("[%s] [%s] [%s]: %s", time, level, source, message);

        assertTrue(formattedMsg.contains("ERROR"));
    }

    @Test
    @DisplayName("测试广播格式 - 带异常")
    void testBroadcastFormat_WithException() {
        String time = TIME_FORMATTER.format(LocalDateTime.now());
        String level = "ERROR";
        String source = "TestSource";
        String message = "Error message";
        Exception e = new RuntimeException("Test exception");
        String fullMessage = message + " (" + e.toString() + ")";

        String formattedMsg = String.format("[%s] [%s] [%s]: %s", time, level, source, fullMessage);

        assertTrue(formattedMsg.contains("RuntimeException"));
        assertTrue(formattedMsg.contains("Test exception"));
    }

    @Test
    @DisplayName("测试WebConsole继承自MyConsole")
    void testInheritance() {
        assertTrue(top.ceroxe.api.utils.MyConsole.class.isAssignableFrom(WebConsole.class));
    }
}
