package neoproxy.neoproxyserver.core.management;

import neoproxy.neoproxyserver.core.Debugger;
import neoproxy.neoproxyserver.core.HostClient;
import neoproxy.neoproxyserver.core.ServerLogger;
import neoproxy.neoproxyserver.core.management.provider.KeyDataProvider;
import neoproxy.neoproxyserver.core.management.provider.RemoteKeyProvider;

import java.io.IOException;

import static neoproxy.neoproxyserver.core.Debugger.debugOperation;

public class UpdateManager {

    // [修改] init 方法现在主要用于兼容性检查，或者可以简化
    public static void init() {
        Debugger.debugOperation("UpdateManager init.");
        // 如果你需要保留本地文件夹作为备份逻辑，可以保留目录创建代码
        // 但根据新的逻辑 (从 NKM 获取 URL)，本地文件夹不再是必须的
    }

    public static void handle(HostClient hostClient) {
        Debugger.debugOperation("Handling update request for client: " + hostClient.getIP());
        boolean isWantedToUpdate;
        try {
            // 1. 读取是否需要更新 (客户端发送的 "true")
            // 但根据客户端逻辑，它在收到不支持版本消息后，会发送 "true"
            String updateFlag = hostClient.getHostServerHook().receiveStr(2000);
            isWantedToUpdate = Boolean.parseBoolean(updateFlag);
            Debugger.debugOperation("Client update requested: " + isWantedToUpdate);
        } catch (IOException e) {
            debugOperation(e);
            hostClient.close();
            return;
        }

        if (!isWantedToUpdate) {
            Debugger.debugOperation("Client does not want update. Exiting update handler.");
            return;
        }

        try {
            // 2. 读取客户端需要的 OS 类型 ("7z" 或 "jar")
            String osType = hostClient.getHostServerHook().receiveStr();
            Debugger.debugOperation("Client requested OS type: " + osType);

            // 3. 获取更新 URL
            String updateUrl = null;

            // 获取当前的 Provider
            KeyDataProvider provider = SequenceKey.getKeyDataProvider();

            if (provider instanceof RemoteKeyProvider remoteProvider) {
                // 如果是远程模式，向 NKM 请求 URL
                String keySerial = hostClient.getKey() != null ? hostClient.getKey().getName() : "unknown";

                Debugger.debugOperation("Requesting update URL from RemoteKeyProvider for Key: " + keySerial);
                updateUrl = remoteProvider.getClientUpdateUrl(osType, keySerial);
            } else {
                // 本地模式不支持自动获取 URL (或者你需要实现本地 URL 逻辑)
                Debugger.debugOperation("Current provider is not RemoteKeyProvider. Cannot fetch URL.");
            }

            // 4. 发送响应给客户端
            if (updateUrl != null && !updateUrl.isBlank()) {
                Debugger.debugOperation("Sending Update URL to client: " + updateUrl);
                hostClient.getHostServerHook().sendStr(updateUrl);

                if (ServerLogger.alert) {
                    ServerLogger.infoWithSource("Update-Manager", "updateManager.sentUrlToClient",
                            hostClient.getAddressAndPort(), updateUrl);
                }
            } else {
                Debugger.debugOperation("No update URL available. Sending 'false'.");
                hostClient.getHostServerHook().sendStr("false");
            }

        } catch (Exception e) {
            debugOperation(e);
            try {
                hostClient.getHostServerHook().sendStr("false");
            } catch (Exception ignored) {
            }
        } finally {
            // 更新流程结束后，通常断开连接让客户端去下载
            hostClient.close();
        }
    }
}