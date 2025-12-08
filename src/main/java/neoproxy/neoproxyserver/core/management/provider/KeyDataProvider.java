package neoproxy.neoproxyserver.core.management.provider;

import neoproxy.neoproxyserver.core.exceptions.PortOccupiedException;
import neoproxy.neoproxyserver.core.management.SequenceKey;

public interface KeyDataProvider {
    /**
     * 初始化 Provider
     */
    void init();

    /**
     * 根据名称获取 Key 信息
     * 此操作隐含“申请连接”的动作。
     *
     * @param name Key 名称
     * @return SequenceKey 对象，若不存在则返回 null
     * @throws PortOccupiedException 如果被中心端明确拒绝（HTTP 409）
     */
    SequenceKey getKey(String name) throws PortOccupiedException;

    /**
     * 消耗流量
     *
     * @param name Key 名称
     * @param mib  消耗的流量 (MiB)
     */
    void consumeFlow(String name, double mib);

    /**
     * 释放 Key 的占用状态
     * 当 HostClient 断开连接时调用
     *
     * @param name Key 名称
     */
    void releaseKey(String name);

    /**
     * 关闭资源
     */
    void shutdown();
}