package neoproxy.neoproxyserver.core.webadmin;

import plethora.utils.MyConsole;

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
        super.log(source, message);
        // 普通日志 -> INFO
        broadcast("INFO", source, message);
    }

    @Override
    public void warn(String source, String message) {
        super.warn(source, message);
        // 警告日志 -> WARN
        broadcast("WARN", source, message);
    }

    @Override
    public void error(String source, String message) {
        super.error(source, message);
        // 错误日志 -> ERROR
        broadcast("ERROR", source, message);
    }

    @Override
    public void error(String source, String message, Throwable throwable) {
        super.error(source, message, throwable);
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
}