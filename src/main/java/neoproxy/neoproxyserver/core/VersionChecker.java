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
        if (clientVersion == null || allowedVersions == null || clientVersion.isBlank()) {
            return false;
        }
        String normalizedClientVersion = clientVersion.trim();

        for (String allowed : allowedVersions) {
            if (allowed == null || allowed.isBlank()) {
                continue;
            }
            String normalizedAllowedVersion = allowed.trim();
            // 1. 完全匹配
            if (normalizedAllowedVersion.equalsIgnoreCase(normalizedClientVersion)) {
                return true;
            }

            // 2. 通配符匹配 (处理 X 或 x)
            if (normalizedAllowedVersion.toUpperCase().contains("X")) {
                try {
                    // X 只代表一个数字版本段，避免 5.11.X 意外匹配 5.11.beta 或 5.11.5.1。
                    String regex = wildcardToRegex(normalizedAllowedVersion);

                    // 使用正则进行不区分大小写的匹配
                    if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(normalizedClientVersion).matches()) {
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

    private static String wildcardToRegex(String allowedVersion) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < allowedVersion.length(); i++) {
            char ch = allowedVersion.charAt(i);
            if (ch == 'X' || ch == 'x') {
                regex.append("\\d+");
            } else {
                regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        regex.append("$");
        return regex.toString();
    }
}
