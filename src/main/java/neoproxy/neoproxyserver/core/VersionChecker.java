package neoproxy.neoproxyserver.core;

import java.util.List;
import java.util.regex.Pattern;

/**
 * NeoProxy 版本检查工具类
 */
public class VersionChecker {

    /**
     * 检查客户端版本是否在支持列表中
     * 支持通配符 'X' (如 5.11.X 将匹配 5.11.1, 5.11.23 等)
     *
     * @param clientVersion   客户端上传的版本号
     * @param allowedVersions 服务器配置文件中定义的允许版本列表
     * @return 如果匹配则返回 true
     */
    public static boolean isVersionSupported(String clientVersion, List<String> allowedVersions) {
        if (clientVersion == null || allowedVersions == null || clientVersion.isEmpty()) {
            return false;
        }

        for (String allowed : allowedVersions) {
            // 1. 完全匹配
            if (allowed.equalsIgnoreCase(clientVersion)) {
                return true;
            }

            // 2. 通配符匹配 (处理 X 或 x)
            if (allowed.toUpperCase().contains("X")) {
                try {
                    // 将 . 转换为 \. (转义)
                    // 将 X 转换为 .* (正则匹配任意字符)
                    String regex = allowed.toUpperCase()
                            .replace(".", "\\.")
                            .replace("X", ".*");

                    // 使用正则进行不区分大小写的匹配
                    if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(clientVersion).matches()) {
                        return true;
                    }
                } catch (Exception e) {
                    // 如果正则转换失败，则跳过此条规则
                    Debugger.debugOperation("VersionChecker regex error: " + e.getMessage());
                }
            }
        }
        return false;
    }
}