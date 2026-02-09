package neoproxy.neoproxyserver.core.management.provider;

import java.io.Serializable;

public class Protocol {
    public static final String API_HEARTBEAT = "/api/heartbeat";
    public static final String API_SYNC = "/api/sync";
    public static final String API_NODE_STATUS = "/api/node/status";

    // [新增] 获取客户端更新 URL 的 API
    public static final String API_CLIENT_UPDATE_URL = "/api/node/client/update-url";

    // 心跳间隔：5秒
    public static final long HEARTBEAT_INTERVAL_MS = 5000L;

    // 节点上报间隔：5分钟
    public static final long NODE_STATUS_INTERVAL_SECONDS = 300L;

    // [POST] /api/heartbeat (Key 维度)
    public static class HeartbeatPayload implements Serializable {
        public String serial;           // Key名
        public String nodeId;           // 节点ID
        public String port;             // 当前端口
        public long timestamp;
        public int currentConnections;  // 这是一个遗留字段，为了兼容性保留
        public String connectionDetail; // 详细的外部连接字符串 (T:X U:X)
    }

    // [新增] 节点状态 Payload (Node 维度)
    public static class NodeStatusPayload implements Serializable {
        public String nodeId;
        public String version;
        public long timestamp;
        public int activeTunnels; // 当前有多少个 HostClient 在运行
    }

    // [新增] 更新 URL 响应结构
    public static class UpdateUrlResponse implements Serializable {
        public String url;
        public boolean valid; // 可选，用于判断是否可用
    }
}