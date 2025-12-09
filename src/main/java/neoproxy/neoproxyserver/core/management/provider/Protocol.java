package neoproxy.neoproxyserver.core.management.provider;

import java.io.Serializable;

/**
 * 通信协议定义类
 * 保持与 NKM Server 端完全一致的 JSON 结构
 */
public class Protocol {

    public static final String API_HEARTBEAT = "/api/heartbeat";
    public static final String API_SYNC = "/api/sync";

    // NPS -> NKM 心跳间隔 (毫秒)
    public static final long HEARTBEAT_INTERVAL_MS = 5000L;

    /**
     * [POST] /api/heartbeat 请求体
     */
    public static class HeartbeatPayload implements Serializable {
        public String serial;           // Key
        public String nodeId;           // 节点唯一ID
        public String port;             // 当前分配端口
        public long timestamp;          // 客户端时间
        public int currentConnections;  // 当前 TCP 连接数
    }
}