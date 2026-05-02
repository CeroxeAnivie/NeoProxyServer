package neoproxy.neoproxyserver.core;

import static neoproxy.neoproxyserver.NeoProxyServer.IS_DEBUG_MODE;

public class Debugger {
    public static void debugOperation(Exception e) {
        if (IS_DEBUG_MODE) {
            ServerLogger.error("neoProxyServer.debugOperation", e, e.getMessage());
            e.printStackTrace();
        }
    }

    // ...（其余工具方法）...
    public static void debugOperation(String msg) {
        if (IS_DEBUG_MODE) {
            ServerLogger.logRaw("Debugger", msg);
        }
    }
}
