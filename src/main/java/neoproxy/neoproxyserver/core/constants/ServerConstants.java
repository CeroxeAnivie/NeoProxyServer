package neoproxy.neoproxyserver.core.constants;

/**
 * 服务器核心常量定义
 *
 * <p>集中管理服务器运行所需的所有常量，避免魔法值分散在代码各处。
 * 所有默认值必须与 config.cfg 模板和 app.properties 保持严格一致。</p>
 *
 * @author Ceroxe
 * @version 6.1.0
 * @since 6.1.0
 */
public final class ServerConstants {

    /**
     * 协议版本
     */
    public static final String PROTOCOL_VERSION = "1.0";

    /**
     * 默认 Hook 端口 (客户端连接握手端口) — 与 config.cfg HOST_HOOK_PORT 一致
     */
    public static final int DEFAULT_HOST_HOOK_PORT = 44801;

    /**
     * 默认 Connect 端口 (数据传输通道端口) — 与 config.cfg HOST_CONNECT_PORT 一致
     */
    public static final int DEFAULT_HOST_CONNECT_PORT = 44802;

    /**
     * 默认Web管理端口 — 与 config.cfg WEB_ADMIN_PORT 一致
     */
    public static final int DEFAULT_WEB_ADMIN_PORT = 44803;

    /**
     * 默认心跳超时时间（毫秒） — 与 config.cfg HEARTBEAT_TIMEOUT 一致
     */
    public static final int DEFAULT_HEARTBEAT_TIMEOUT = 5000;

    /**
     * 默认保存延迟（毫秒） — 与 config.cfg SAVE_DELAY 一致
     */
    public static final int DEFAULT_SAVE_DELAY = 3000;

    /**
     * 默认检测延迟（毫秒）
     */
    public static final int DEFAULT_DETECTION_DELAY = 1000;

    /**
     * AES密钥长度 — 与 config.cfg AES_KEY_SIZE 一致
     */
    public static final int AES_KEY_SIZE = 128;

    /**
     * TCP 缓冲区大小（字节） — 与 TCPTransformer.BUFFER_LEN 一致
     */
    public static final int TCP_BUFFER_SIZE = 65535;

    /**
     * TCP buffer floor. Going below this value makes encryption/frame overhead dominate useful payload.
     */
    public static final int MIN_TCP_BUFFER_SIZE = 512;

    /**
     * Low-RAM TCP buffer. This intentionally spends more CPU/syscalls to keep per-connection heap small.
     */
    public static final int LOW_RAM_TCP_BUFFER_SIZE = 1024;

    /**
     * One full UDP datagram must still fit even in low-RAM mode; otherwise the proxy would silently truncate UDP.
     */
    public static final int UDP_PACKET_BUFFER_SIZE = 65535;

    /**
     * Default per-UDP-session pending packet count.
     */
    public static final int UDP_SEND_QUEUE_CAPACITY = 100;

    /**
     * Low-RAM per-UDP-session pending packet count. Backpressure is preferred over unbounded heap growth.
     */
    public static final int LOW_RAM_UDP_SEND_QUEUE_CAPACITY = 1;

    /**
     * Normal secure-frame ceiling. Transfer frames are chunked, so larger packets are not required for proxying.
     */
    public static final int SECURE_PACKET_SIZE = 1024 * 1024;

    /**
     * Low-RAM secure-frame ceiling. Large enough for a full UDP datagram plus encryption overhead.
     */
    public static final int LOW_RAM_SECURE_PACKET_SIZE = 128 * 1024;

    /**
     * 动态端口标识值 — 与 SequenceKey.DYNAMIC_PORT 一致
     */
    public static final int DYNAMIC_PORT = -1;

    /**
     * 默认本地域名 — 与 config.cfg LOCAL_DOMAIN_NAME 一致
     */
    public static final String DEFAULT_LOCAL_DOMAIN_NAME = "localhost";

    /**
     * 默认语言
     */
    public static final String DEFAULT_LANGUAGE = "zh-CN";

    private ServerConstants() {
        throw new AssertionError("常量类禁止实例化");
    }
}
