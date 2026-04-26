package neoproxy.neoproxyserver.core.constants;

import java.util.regex.Pattern;

/**
 * 验证正则表达式常量
 *
 * <p>集中管理所有用于数据验证的正则表达式。</p>
 *
 * @author Ceroxe
 * @version 6.1.0
 * @since 6.1.0
 */
public final class ValidationPatterns {

    /**
     * IPv4地址正则表达式
     */
    public static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    /**
     * IPv6地址正则表达式
     */
    public static final Pattern IPV6_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$"
    );
    /**
     * 端口号正则表达式（1-65535）
     */
    public static final Pattern PORT_PATTERN = Pattern.compile(
            "^([1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])$"
    );
    /**
     * 访问密钥正则表达式（字母数字，6-32位）
     */
    public static final Pattern ACCESS_KEY_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9]{6,32}$"
    );
    /**
     * 域名正则表达式
     */
    public static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?$"
    );
    /**
     * 邮箱正则表达式
     */
    public static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private ValidationPatterns() {
        throw new AssertionError("常量类禁止实例化");
    }

    /**
     * 验证IPv4地址是否有效
     *
     * @param ip 待验证的IP地址
     * @return true如果有效，false否则
     */
    public static boolean isValidIPv4(String ip) {
        return ip != null && IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * 验证端口号是否有效
     *
     * @param port 待验证的端口号
     * @return true如果有效，false否则
     */
    public static boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }

    /**
     * 验证访问密钥格式是否有效
     *
     * @param key 待验证的密钥
     * @return true如果有效，false否则
     */
    public static boolean isValidAccessKey(String key) {
        return key != null && ACCESS_KEY_PATTERN.matcher(key).matches();
    }
}
