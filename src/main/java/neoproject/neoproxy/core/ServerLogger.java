package neoproject.neoproxy.core;

import neoproject.neoproxy.NeoProxyServer;

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

    private static final String BUNDLE_NAME = "messages";
    public static boolean alert = true;
    private static ResourceBundle bundle;
    private static Locale currentLocale = Locale.getDefault(); // 默认使用系统语言

    static {
        setLocale(currentLocale);
    }

    // ==================== INFO ====================

    /**
     * 设置日志使用的语言环境
     *
     * @param locale 目标语言环境
     */
    public static void setLocale(Locale locale) {
        currentLocale = locale;
        try {
            bundle = ResourceBundle.getBundle(BUNDLE_NAME, currentLocale);
        } catch (MissingResourceException e) {
            // 如果加载失败，将 bundle 设为 null，后续调用会失败并提示
            System.err.println("CRITICAL ERROR: Failed to load ResourceBundle: " + BUNDLE_NAME + " for locale: " + currentLocale);
            bundle = null;
        }
    }

    /**
     * 记录INFO级别日志
     *
     * @param key  资源文件中的键
     * @param args 格式化参数
     */
    public static void info(String key, Object... args) {
        String message = getMessage(key, args);
        if (NeoProxyServer.myConsole != null) {
            NeoProxyServer.myConsole.log("NeoProxyServer", message);
        }
    }

    // ==================== WARN ====================

    /**
     * 记录INFO级别日志，并指定日志来源
     *
     * @param source 日志来源
     * @param key    资源文件中的键
     * @param args   格式化参数
     */
    public static void infoWithSource(String source, String key, Object... args) {
        String message = getMessage(key, args);
        if (NeoProxyServer.myConsole != null) {
            NeoProxyServer.myConsole.log(source, message);
        }
    }

    /**
     * 记录WARN级别日志
     *
     * @param key  资源文件中的键
     * @param args 格式化参数
     */
    public static void warn(String key, Object... args) {
        String message = getMessage(key, args);
        if (NeoProxyServer.myConsole != null) {
            NeoProxyServer.myConsole.warn("NeoProxyServer", message);
        }
    }

    // ==================== ERROR ====================

    /**
     * 记录WARN级别日志，并指定日志来源
     *
     * @param source 日志来源
     * @param key    资源文件中的键
     * @param args   格式化参数
     */
    public static void warnWithSource(String source, String key, Object... args) {
        String message = getMessage(key, args);
        if (NeoProxyServer.myConsole != null) {
            NeoProxyServer.myConsole.warn(source, message);
        }
    }

    /**
     * 记录ERROR级别日志
     *
     * @param key  资源文件中的键
     * @param args 格式化参数
     */
    public static void error(String key, Object... args) {
        String message = getMessage(key, args);
        if (NeoProxyServer.myConsole != null) {
            NeoProxyServer.myConsole.error("NeoProxyServer", message);
        }
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
        if (NeoProxyServer.myConsole != null) {
            NeoProxyServer.myConsole.error("NeoProxyServer", message, throwable);
        }
    }

    // ==================== HELPER ====================

    /**
     * 记录ERROR级别日志，并指定日志来源
     *
     * @param source 日志来源
     * @param key    资源文件中的键
     * @param args   格式化参数
     */
    public static void errorWithSource(String source, String key, Object... args) {
        String message = getMessage(key, args);
        if (NeoProxyServer.myConsole != null) {
            NeoProxyServer.myConsole.error(source, message);
        }
    }
// ... 在 ServerLogger 类中添加以下方法 ...

    /**
     * 从资源文件中获取并格式化消息
     *
     * @param key  资源文件中的键
     * @param args 格式化参数
     * @return 格式化后的字符串
     */
    public static String getMessage(String key, Object... args) {
        if (bundle == null) {
            return "!!! ResourceBundle is not loaded. Key: " + key + " !!!";
        }
        try {
            String pattern = bundle.getString(key);
            return MessageFormat.format(pattern, args);
        } catch (MissingResourceException e) {
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
        NeoProxyServer.myConsole.log(source, message);
    }

    public static void sayHostClientDiscInfo(HostClient hostClient, String subject) {
        // MODIFIED: 使用 infoWithSource
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