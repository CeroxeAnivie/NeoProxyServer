package neoproxy.neoproxyserver.core.management.provider;

import java.io.Serializable;

public class Protocol {
    public static final String API_HEARTBEAT = "/api/heartbeat";
    public static final String API_SYNC = "/api/sync";

    // 心跳间隔：5秒 (配合服务端 20秒超时)
    public static final long HEARTBEAT_INTERVAL_MS = 5000L;

    // [POST] /api/heartbeat
    public static class HeartbeatPayload implements Serializable {
        public String serial;           // Key名
        public String nodeId;           // 节点ID
        public String port;             // 当前端口 (INIT 或 真实端口)
        public long timestamp;
        public int currentConnections;  // 负载信息
    }
}