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
     * TCP 缓冲区下限。低于这个值后，加密和帧开销会开始压过有效载荷。
     */
    public static final int MIN_TCP_BUFFER_SIZE = 512;

    /**
     * 低内存模式的 TCP 缓冲区。这样会主动增加 CPU / 系统调用开销，以换取更小的单连接堆占用。
     */
    public static final int LOW_RAM_TCP_BUFFER_SIZE = 1024;

    /**
     * 即使在低内存模式下，也必须能容纳完整的 UDP 数据报；否则代理会静默截断 UDP。
     */
    public static final int UDP_PACKET_BUFFER_SIZE = 65535;

    /**
     * 默认的每个 UDP 会话待发送包数量。
     */
    public static final int UDP_SEND_QUEUE_CAPACITY = 100;

    /**
     * 低内存模式的每个 UDP 会话待发送包数量。优先施加背压，而不是无限增长堆内存。
     */
    public static final int LOW_RAM_UDP_SEND_QUEUE_CAPACITY = 1;

    /**
     * 常规安全帧上限。传输帧会被分片，因此代理不需要更大的单包上限。
     */
    public static final int SECURE_PACKET_SIZE = 1024 * 1024;

    /**
     * 低内存模式的安全帧上限。这个值仍能容纳完整 UDP 数据报及其加密开销。
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
