package neoproxy.neoproxyserver.core.management.provider;

import java.io.Serializable;

public class Protocol {
    public static final String API_HEARTBEAT = "/api/heartbeat";
    public static final String API_SYNC = "/api/sync";
    public static final String API_NODE_STATUS = "/api/node/status";
    public static final String API_CLIENT_UPDATE_URL = "/api/node/client/update-url";

    public static final long HEARTBEAT_INTERVAL_MS = 5000L;

    // 【核心修复】：改为 30 秒。确保在 NKM 的 60 秒超时到来前，能稳定发送 2 次心跳！
    public static final long NODE_STATUS_INTERVAL_SECONDS = 30L;

    public static class HeartbeatPayload implements Serializable {
        public String serial;
        public String nodeId;
        public String port;
        public long timestamp;
        public int currentConnections;
        public String connectionDetail;
    }

    public static class NodeStatusPayload implements Serializable {
        public String nodeId;
        public String address;
        public int hookPort;
        public int connectPort;
        public String version;
        public long timestamp;
        public int activeTunnels;
    }

    public static class UpdateUrlResponse implements Serializable {
        public String url;
        public boolean valid;
    }
}
