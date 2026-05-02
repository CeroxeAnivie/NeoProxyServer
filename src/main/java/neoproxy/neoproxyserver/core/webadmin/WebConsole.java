package neoproxy.neoproxyserver.core.webadmin;

import top.ceroxe.api.utils.MyConsole;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 继承自 MyConsole，拦截日志输出并转发给 WebAdmin。
 * 实现了日志格式的统一标准化。
 */
public class WebConsole extends MyConsole {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public WebConsole(String appName) throws IOException {
        super(appName);
    }

    @Override
    public void log(String source, String message) {
        writeSafely(() -> super.log(source, message), "INFO", source, message, null);
        // 普通日志 -> INFO
        broadcast("INFO", source, message);
    }

    @Override
    public void warn(String source, String message) {
        writeSafely(() -> super.warn(source, message), "WARN", source, message, null);
        // 警告日志 -> WARN
        broadcast("WARN", source, message);
    }

    @Override
    public void error(String source, String message) {
        writeSafely(() -> super.error(source, message), "ERROR", source, message, null);
        // 错误日志 -> ERROR
        broadcast("ERROR", source, message);
    }

    @Override
    public void error(String source, String message, Throwable throwable) {
        writeSafely(() -> super.error(source, message, throwable), "ERROR", source, message, throwable);
        // 带堆栈的错误 -> ERROR
        broadcast("ERROR", source, message + " (" + throwable.toString() + ")");
    }

    /**
     * 统一广播格式：[时间] [等级] [来源]: 内容
     */
    private void broadcast(String level, String source, String message) {
        String time = TIME_FORMATTER.format(LocalDateTime.now());
        // 格式严格匹配前端正则：^\[(.*?)\] \[(.*?)\] \[(.*?)\]: (.*)$
        String formattedMsg = String.format("[%s] [%s] [%s]: %s", time, level, source, message);
        WebAdminManager.broadcastLog(formattedMsg);
    }
    private void writeSafely(Runnable consoleWrite, String level, String source, String message, Throwable throwable) {
        try {
            consoleWrite.run();
        } catch (IllegalStateException terminalClosed) {
            String fallback = String.format("[%s] [%s]: %s", level, source, message);
            if ("ERROR".equals(level)) {
                System.err.println(fallback);
                if (throwable != null) {
                    throwable.printStackTrace(System.err);
                }
            } else {
                System.out.println(fallback);
            }
        }
    }
}
