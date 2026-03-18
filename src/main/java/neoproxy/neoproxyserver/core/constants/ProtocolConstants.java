package neoproxy.neoproxyserver.core.constants;

/**
 * 协议相关常量定义
 * 
 * <p>定义客户端-服务器通信协议的所有常量。</p>
 * 
 * @author Ceroxe
 * @version 6.1.0
 * @since 6.1.0
 */
public final class ProtocolConstants {
    
    private ProtocolConstants() {
        throw new AssertionError("常量类禁止实例化");
    }
    
    // 命令类型
    /** 连接命令 */
    public static final byte CMD_CONNECT = 0x01;
    
    /** 断开命令 */
    public static final byte CMD_DISCONNECT = 0x02;
    
    /** 数据传输命令 */
    public static final byte CMD_DATA = 0x03;
    
    /** 心跳命令 */
    public static final byte CMD_HEARTBEAT = 0x04;
    
    /** 错误命令 */
    public static final byte CMD_ERROR = 0x05;
    
    // 协议头
    /** 协议魔数 */
    public static final byte[] PROTOCOL_MAGIC = new byte[] {0x4E, 0x50}; // "NP"
    
    /** 协议头长度 */
    public static final int HEADER_LENGTH = 8;
    
    /** 最大包体长度 */
    public static final int MAX_BODY_LENGTH = 65535;
    
    // 心跳相关
    /** 心跳请求标识 */
    public static final String HEARTBEAT_REQUEST = "PING";
    
    /** 心跳响应标识 */
    public static final String HEARTBEAT_RESPONSE = "PONG";
    
    // 状态码
    /** 成功状态码 */
    public static final int STATUS_SUCCESS = 200;
    
    /** 密钥错误状态码 */
    public static final int STATUS_INVALID_KEY = 401;
    
    /** 流量耗尽状态码 */
    public static final int STATUS_NO_FLOW = 402;
    
    /** 服务器错误状态码 */
    public static final int STATUS_SERVER_ERROR = 500;
}
