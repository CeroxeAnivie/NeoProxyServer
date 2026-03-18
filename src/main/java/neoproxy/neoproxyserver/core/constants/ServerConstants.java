package neoproxy.neoproxyserver.core.constants;

/**
 * 服务器核心常量定义
 * 
 * <p>集中管理服务器运行所需的所有常量，避免魔法值分散在代码各处。</p>
 * 
 * @author Ceroxe
 * @version 6.1.0
 * @since 6.1.0
 */
public final class ServerConstants {
    
    private ServerConstants() {
        throw new AssertionError("常量类禁止实例化");
    }
    
    /** 服务器版本号 */
    public static final String VERSION = "6.1.0";
    
    /** 协议版本 */
    public static final String PROTOCOL_VERSION = "1.0";
    
    /** 默认服务器端口 */
    public static final int DEFAULT_SERVER_PORT = 8080;
    
    /** 默认Web管理端口 */
    public static final int DEFAULT_WEB_ADMIN_PORT = 8081;
    
    /** 默认心跳超时时间（毫秒） */
    public static final int DEFAULT_HEARTBEAT_TIMEOUT = 30000;
    
    /** 默认保存延迟（毫秒） */
    public static final int DEFAULT_SAVE_DELAY = 3000;
    
    /** 默认检测延迟（毫秒） */
    public static final int DEFAULT_DETECTION_DELAY = 1000;
    
    /** AES密钥长度 */
    public static final int AES_KEY_SIZE = 128;
    
    /** 缓冲区大小（字节） */
    public static final int BUFFER_SIZE = 8192;
    
    /** 最大并发连接数 */
    public static final int MAX_CONCURRENT_CONNECTIONS = 1000;
    
    /** 动态端口起始值 */
    public static final int DYNAMIC_PORT_START = 10000;
    
    /** 动态端口结束值 */
    public static final int DYNAMIC_PORT_END = 65535;
    
    /** 默认语言 */
    public static final String DEFAULT_LANGUAGE = "zh-CN";
}
