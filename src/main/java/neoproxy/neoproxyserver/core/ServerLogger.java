package neoproxy.neoproxyserver.core;

import neoproxy.neoproxyserver.NeoProxyServer;

import java.net.DatagramPacket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 国际化日志工具类
 * 负责根据当前设置的Locale加载资源文件并格式化日志消息。
 */
public class ServerLogger {

    private static final String EN_BUNDLE_NAME = "messages_en";
    private static final String ZH_BUNDLE_NAME = "messages_zh";
    public static boolean alert = true;
    private static ResourceBundle bundle;
    private static Locale currentLocale = Locale.ENGLISH;

    static {
        setLocale(currentLocale);
    }

    // ==================== 信息 ====================

    /**
     * 设置日志使用的语言环境
     *
     * @param locale 目标语言环境
     */
    public static void setLocale(Locale locale) {
        if (locale == null) {
            locale = Locale.ENGLISH;
        }
        currentLocale = locale;
        String bundleName = resolveBundleName(locale);
        try {
            bundle = ResourceBundle.getBundle(bundleName, Locale.ROOT);
        } catch (MissingResourceException e) {
            System.err.println("CRITICAL ERROR: Failed to load ResourceBundle: " + bundleName + " for locale: " + currentLocale);
            try {
                bundle = ResourceBundle.getBundle(EN_BUNDLE_NAME, Locale.ROOT);
            } catch (MissingResourceException fallbackError) {
                bundle = null;
            }
        }
    }

    private static String resolveBundleName(Locale locale) {
        return "zh".equalsIgnoreCase(locale.getLanguage()) ? ZH_BUNDLE_NAME : EN_BUNDLE_NAME;
    }

    /**
     * 记录INFO级别日志
     *
     * @param key  资源文件中的键
     * @param args 格式化参数
     */
    public static void info(String key, Object... args) {
        String message = getMessage(key, args);
        writeInfo("NeoProxyServer", message);
    }

    // ==================== 警告 ====================

    /**
     * 记录INFO级别日志，并指定日志来源
     *
     * @param source 日志来源
     * @param key    资源文件中的键
     * @param args   格式化参数
     */
    public static void infoWithSource(String source, String key, Object... args) {
        String message = getMessage(key, args);
        writeInfo(source, message);
    }

    /**
     * 记录WARN级别日志
     *
     * @param key  资源文件中的键
     * @param args 格式化参数
     */
    public static void warn(String key, Object... args) {
        String message = getMessage(key, args);
        writeWarn("NeoProxyServer", message);
    }

    /**
     * 记录WARN级别日志，并指定日志来源
     *
     * @param source 日志来源
     * @param key    资源文件中的键
     * @param args   格式化参数
     */
    public static void warnWithSource(String source, String key, Object... args) {
        String message = getMessage(key, args);
        writeWarn(source, message);
    }

    // ==================== 错误 ====================

    /**
     * 记录ERROR级别日志
     *
     * @param key  资源文件中的键
     * @param args 格式化参数
     */
    public static void error(String key, Object... args) {
        String message = getMessage(key, args);
        writeError("NeoProxyServer", message, null);
    }

    /**
     * 记录ERROR级别日志，并附带异常堆栈
     *
     * @param key       资源文件中的键
     * @param throwable 异常对象
     * @param args      格式化参数
     */
    public static void error(String key, Throwable throwable, Object... args) {
        String message = getMessage(key, args);
        writeError("NeoProxyServer", message, throwable);
    }

    /**
     * 记录ERROR级别日志，并指定日志来源
     *
     * @param source 日志来源
     * @param key    资源文件中的键
     * @param args   格式化参数
     */
    public static void errorWithSource(String source, String key, Object... args) {
        String message = getMessage(key, args);
        writeError(source, message, null);
    }

    /**
     * 记录ERROR级别日志，并指定日志来源与异常堆栈。
     *
     * @param source    日志来源
     * @param key       资源文件中的键
     * @param throwable 异常对象
     * @param args      格式化参数
     */
    public static void errorWithSource(String source, String key, Throwable throwable, Object... args) {
        String message = getMessage(key, args);
        writeError(source, message, throwable);
    }

    /**
     * 从资源文件中获取并格式化消息。
     * 为确保全球格式一致，所有数字参数在格式化前都会被转换为字符串，以避免千位分隔符等问题。
     *
     * @param key  资源文件中的键
     * @param args 格式化参数
     * @return 格式化后的字符串
     */
    public static String getMessage(String key, Object... args) {
        if (bundle == null) {
            return "!!! ResourceBundle not loaded. Cannot get message for key: " + key + " !!!";
        }
        try {
            String pattern = bundle.getString(key);
            // 【新方案】在格式化之前，将所有数字参数转换为字符串
            Object[] formattedArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Number) {
                    // 使用 String.valueOf 来转换数字，它不会添加任何千位分隔符
                    formattedArgs[i] = String.valueOf(args[i]);
                } else {
                    formattedArgs[i] = args[i];
                }
            }
            // 现在使用标准的 MessageFormat，但它的参数已经是字符串了，不会再格式化数字
            return MessageFormat.format(pattern, formattedArgs);
        } catch (MissingResourceException e) {
            // 如果找不到key，返回一个友好的提示
            return "!!! Log message not found for key: " + key + " !!!";
        }
    }

    /**
     * 记录原始的、不需要国际化的日志消息。
     *
     * @param source  日志来源
     * @param message 原始消息
     */
    public static void logRaw(String source, String message) {
        writeInfo(source, message);
    }

    private static void writeInfo(String source, String message) {
        if (NeoProxyServer.myConsole != null) {
            NeoProxyServer.myConsole.log(source, message);
            return;
        }
        System.out.println(formatFallbackLine("INFO", source, message));
    }

    private static void writeWarn(String source, String message) {
        if (NeoProxyServer.myConsole != null) {
            NeoProxyServer.myConsole.warn(source, message);
            return;
        }
        System.out.println(formatFallbackLine("WARN", source, message));
    }

    private static void writeError(String source, String message, Throwable throwable) {
        if (NeoProxyServer.myConsole != null) {
            if (throwable != null) {
                NeoProxyServer.myConsole.error(source, message, throwable);
            } else {
                NeoProxyServer.myConsole.error(source, message);
            }
            return;
        }
        System.err.println(formatFallbackLine("ERROR", source, message));
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }

    private static String formatFallbackLine(String level, String source, String message) {
        return "[" + level + "] [" + source + "]: " + message;
    }

    // ==================== 兼容旧方法（已废弃） ====================
    // 以下方法是为了兼容旧代码而保留的，建议在新代码中直接使用 ServerLogger 的方法
    // 这些方法的存在是 InfoBox 等类中调用它们的原因。

    public static void sayHostClientDiscInfo(HostClient hostClient, String subject) {
        ServerLogger.infoWithSource(subject, "infoBox.hostClientDisconnected", hostClient.getAddressAndPort());
    }

    public static void sayClientTCPConnectBuildUpInfo(HostClient hostClient, Socket client) {
        if (alert) {
            ServerLogger.info("infoBox.tcpConnectionBuild", InternetOperator.getInternetAddressAndPort(client), hostClient.getAddressAndPort());
        }
    }

    public static void sayClientUDPConnectBuildUpInfo(HostClient hostClient, DatagramPacket datagramPacket) {
        if (alert) {
            ServerLogger.info("infoBox.udpConnectionBuild", InternetOperator.getInternetAddressAndPort(datagramPacket), hostClient.getAddressAndPort());
        }
    }

    public static void sayClientTCPConnectDestroyInfo(HostClient hostClient, Socket client) {
        if (alert) {
            ServerLogger.info("infoBox.tcpConnectionDestroy", InternetOperator.getInternetAddressAndPort(client), hostClient.getAddressAndPort());
        }
    }

    public static void sayClientUDPConnectDestroyInfo(HostClient hostClient, String ipAndPort) {
        if (alert) {
            ServerLogger.info("infoBox.udpConnectionDestroy", ipAndPort, hostClient.getAddressAndPort());
        }
    }

    public static void sayClientSuccConnectToChaSerButHostClientTimeOut(HostClient hostClient) {
        if (alert) {
            ServerLogger.info("infoBox.clientConnectButHostTimeout", hostClient.getAddressAndPort());
        }
    }

    public static void sayKillingClientSideConnection(Socket client) {
        if (alert) {
            ServerLogger.info("infoBox.killingClientConnection", InternetOperator.getInternetAddressAndPort(client));
        }
    }
}
