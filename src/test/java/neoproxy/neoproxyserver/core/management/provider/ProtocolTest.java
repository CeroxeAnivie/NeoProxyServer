package neoproxy.neoproxyserver.core.management.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Protocol 类测试")
class ProtocolTest {

    @Test
    @DisplayName("测试默认构造函数")
    void testDefaultConstructor() throws Exception {
        Protocol protocol = new Protocol();
        assertNotNull(protocol);
    }

    @Test
    @DisplayName("测试 API_GET_KEY 常量")
    void testApiGetKeyConstant() throws Exception {
        Field field = Protocol.class.getDeclaredField("API_GET_KEY");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("/api/key", value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试 API_HEARTBEAT 常量")
    void testApiHeartbeatConstant() throws Exception {
        Field field = Protocol.class.getDeclaredField("API_HEARTBEAT");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("/api/heartbeat", value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试 API_RELEASE 常量")
    void testApiReleaseConstant() throws Exception {
        Field field = Protocol.class.getDeclaredField("API_RELEASE");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("/api/release", value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试 API_SYNC 常量")
    void testApiSyncConstant() throws Exception {
        Field field = Protocol.class.getDeclaredField("API_SYNC");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("/api/sync", value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试 API_NODE_STATUS 常量")
    void testApiNodeStatusConstant() throws Exception {
        Field field = Protocol.class.getDeclaredField("API_NODE_STATUS");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("/api/node/status", value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试 API_CLIENT_UPDATE_URL 常量")
    void testApiClientUpdateUrlConstant() throws Exception {
        Field field = Protocol.class.getDeclaredField("API_CLIENT_UPDATE_URL");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("/api/node/client/update-url", value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试 HEARTBEAT_INTERVAL_MS 常量")
    void testHeartbeatIntervalMsConstant() throws Exception {
        Field field = Protocol.class.getDeclaredField("HEARTBEAT_INTERVAL_MS");
        field.setAccessible(true);
        long value = (long) field.get(null);
        assertEquals(5000L, value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试 NODE_STATUS_INTERVAL_SECONDS 常量")
    void testNodeStatusIntervalSecondsConstant() throws Exception {
        Field field = Protocol.class.getDeclaredField("NODE_STATUS_INTERVAL_SECONDS");
        field.setAccessible(true);
        long value = (long) field.get(null);
        assertEquals(30L, value);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试 HeartbeatPayload 类存在")
    void testHeartbeatPayloadClassExists() throws Exception {
        Class<?> clazz = Class.forName("neoproxy.neoproxyserver.core.management.provider.Protocol$HeartbeatPayload");
        assertNotNull(clazz);
        assertTrue(Modifier.isStatic(clazz.getModifiers()));
        assertTrue(Modifier.isPublic(clazz.getModifiers()));
    }

    @Test
    @DisplayName("测试 HeartbeatPayload 实现 Serializable")
    void testHeartbeatPayloadImplementsSerializable() throws Exception {
        Class<?> clazz = Class.forName("neoproxy.neoproxyserver.core.management.provider.Protocol$HeartbeatPayload");
        assertTrue(java.io.Serializable.class.isAssignableFrom(clazz));
    }

    @Test
    @DisplayName("测试 NodeStatusPayload 类存在")
    void testNodeStatusPayloadClassExists() throws Exception {
        Class<?> clazz = Class.forName("neoproxy.neoproxyserver.core.management.provider.Protocol$NodeStatusPayload");
        assertNotNull(clazz);
        assertTrue(Modifier.isStatic(clazz.getModifiers()));
        assertTrue(Modifier.isPublic(clazz.getModifiers()));
    }

    @Test
    @DisplayName("测试 NodeStatusPayload 实现 Serializable")
    void testNodeStatusPayloadImplementsSerializable() throws Exception {
        Class<?> clazz = Class.forName("neoproxy.neoproxyserver.core.management.provider.Protocol$NodeStatusPayload");
        assertTrue(java.io.Serializable.class.isAssignableFrom(clazz));
    }

    @Test
    @DisplayName("测试 UpdateUrlResponse 类存在")
    void testUpdateUrlResponseClassExists() throws Exception {
        Class<?> clazz = Class.forName("neoproxy.neoproxyserver.core.management.provider.Protocol$UpdateUrlResponse");
        assertNotNull(clazz);
        assertTrue(Modifier.isStatic(clazz.getModifiers()));
        assertTrue(Modifier.isPublic(clazz.getModifiers()));
    }

    @Test
    @DisplayName("测试 UpdateUrlResponse 实现 Serializable")
    void testUpdateUrlResponseImplementsSerializable() throws Exception {
        Class<?> clazz = Class.forName("neoproxy.neoproxyserver.core.management.provider.Protocol$UpdateUrlResponse");
        assertTrue(java.io.Serializable.class.isAssignableFrom(clazz));
    }

    @Test
    @DisplayName("测试 HeartbeatPayload 默认构造函数")
    void testHeartbeatPayloadDefaultConstructor() throws Exception {
        Class<?> clazz = Class.forName("neoproxy.neoproxyserver.core.management.provider.Protocol$HeartbeatPayload");
        Object instance = clazz.getDeclaredConstructor().newInstance();
        assertNotNull(instance);
    }

    @Test
    @DisplayName("测试 NodeStatusPayload 默认构造函数")
    void testNodeStatusPayloadDefaultConstructor() throws Exception {
        Class<?> clazz = Class.forName("neoproxy.neoproxyserver.core.management.provider.Protocol$NodeStatusPayload");
        Object instance = clazz.getDeclaredConstructor().newInstance();
        assertNotNull(instance);
    }

    @Test
    @DisplayName("测试 UpdateUrlResponse 默认构造函数")
    void testUpdateUrlResponseDefaultConstructor() throws Exception {
        Class<?> clazz = Class.forName("neoproxy.neoproxyserver.core.management.provider.Protocol$UpdateUrlResponse");
        Object instance = clazz.getDeclaredConstructor().newInstance();
        assertNotNull(instance);
    }
}
