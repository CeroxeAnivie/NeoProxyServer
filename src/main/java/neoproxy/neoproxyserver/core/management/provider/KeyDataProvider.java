package neoproxy.neoproxyserver.core.management.provider;

import neoproxy.neoproxyserver.core.exceptions.PortOccupiedException;
import neoproxy.neoproxyserver.core.management.SequenceKey;

public interface KeyDataProvider {
    /**
     * 初始化资源 (数据库连接 或 定时任务)
     */
    void init();

    /**
     * 获取 Key 信息 (登录/启动时调用)
     */
    SequenceKey getKey(String name) throws PortOccupiedException;

    /**
     * 消费流量通知
     * <p>
     * 实现类应使用缓冲区或脏标记机制，避免在此方法中直接执行 IO 操作
     * </p>
     *
     * @param name Key 名称
     * @param mib  消耗的流量 (MB)
     */
    void consumeFlow(String name, double mib);

    /**
     * 释放 Key (下线)
     */
    void releaseKey(String name);

    /**
     * 关闭 Provider，确保缓冲区数据刷盘
     */
    void shutdown();

    /**
     * 发送心跳包
     *
     * @return true=保持连接, false=立即断开(kill)
     */
    boolean sendHeartbeat(Protocol.HeartbeatPayload payload);
}