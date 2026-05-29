package neoproxy.neoproxyserver.core.webadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neoproxy.neoproxyserver.NeoProxyServer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("WebAdminServer 测试")
class WebAdminServerTest {

    @Test
    @DisplayName("测试构造器")
    void testConstructor() {
        WebAdminServer server = new WebAdminServer(8080);

        assertNotNull(server);
    }

    @Test
    @DisplayName("测试broadcastLog方法")
    void testBroadcastLog() {
        assertDoesNotThrow(() -> WebAdminServer.broadcastLog("Test log message"));
    }

    @Test
    @DisplayName("测试broadcastLog方法 - 包含特殊字符")
    void testBroadcastLog_SpecialChars() {
        assertDoesNotThrow(() -> WebAdminServer.broadcastLog("Test ____ message"));
    }

    @Test
    @DisplayName("文件管理路径解析拒绝逃离虚拟根")
    void testResolveManagedFileRejectsTraversal(@TempDir Path root) throws IOException {
        assertEquals(root.toAbsolutePath().normalize(), WebAdminServer.resolveManagedFile(root.toFile(), "").toPath());
        assertThrows(SecurityException.class, () -> WebAdminServer.resolveManagedFile(root.toFile(), "../outside.txt"));
        assertThrows(SecurityException.class, () -> WebAdminServer.resolveManagedFile(root.toFile(), root.resolve("abs.txt").toString()));
    }

    @Test
    @DisplayName("文件管理路径解析不把虚拟入口规范化到真实物理路径")
    void testResolveManagedFileKeepsVirtualEntryPath(@TempDir Path root) throws IOException {
        File resolved = WebAdminServer.resolveManagedFile(root.toFile(), "linked/secret.txt");

        assertEquals(root.resolve("linked").resolve("secret.txt").toAbsolutePath().normalize(), resolved.toPath());
    }

    @Test
    @DisplayName("静态资源版本号只跟随应用版本常量")
    void testStaticAssetVersionFollowsApplicationVersion() {
        String originalVersion = NeoProxyServer.VERSION;
        try {
            NeoProxyServer.VERSION = "9.8.7 test";

            String html = WebAdminServer.applyStaticAssetVersion("<script src=\"/js/files.js\"></script>");

            assertEquals("<script src=\"/js/files.js?v=9.8.7%20test\"></script>", html);
        } finally {
            NeoProxyServer.VERSION = originalVersion;
        }
    }
}
