package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LanguageData 测试")
class LanguageDataTest {

    @Test
    @DisplayName("测试默认构造器")
    void testDefaultConstructor() {
        LanguageData data = new LanguageData();

        assertNotNull(data);
        assertEquals("en", data.getCurrentLanguage());
    }

    @Test
    @DisplayName("测试getChineseLanguage方法")
    void testGetChineseLanguage() {
        LanguageData data = LanguageData.getChineseLanguage();

        assertNotNull(data);
        assertEquals("zh", data.getCurrentLanguage());
    }

    @Test
    @DisplayName("测试英文默认值")
    void testEnglishDefaultValues() {
        LanguageData data = new LanguageData();

        assertEquals("This port is already in use. Please try with a different node.", data.THE_PORT_HAS_ALREADY_BIND);
        assertEquals("Connection rejected: Port occupied by another node or limit reached.", data.REMOTE_PORT_OCCUPIED);
        assertEquals("If you use this software, you understand and agree with eula .", data.IF_YOU_SEE_EULA);
        assertEquals("Version : ", data.VERSION);
        assertEquals("Please enter the access code:", data.PLEASE_ENTER_ACCESS_CODE);
    }

    @Test
    @DisplayName("测试中文值")
    void testChineseValues() {
        LanguageData data = LanguageData.getChineseLanguage();

        assertEquals("这个端口已经被占用了，请你更换节点重试。", data.THE_PORT_HAS_ALREADY_BIND);
        assertEquals("连接被拒绝：该端口已被其他节点占用，或已达到最大允许连接数。", data.REMOTE_PORT_OCCUPIED);
        assertEquals("如果你已经开始使用的本软件，说明你已经知晓并同意了本软件的eula协议", data.IF_YOU_SEE_EULA);
        assertEquals("版本 ： ", data.VERSION);
        assertEquals("请输入密钥：", data.PLEASE_ENTER_ACCESS_CODE);
    }

    @Test
    @DisplayName("测试getCurrentLanguage方法")
    void testGetCurrentLanguage() {
        LanguageData enData = new LanguageData();
        LanguageData zhData = LanguageData.getChineseLanguage();

        assertEquals("en", enData.getCurrentLanguage());
        assertEquals("zh", zhData.getCurrentLanguage());
    }

    @Test
    @DisplayName("测试公共方法 getChineseLanguage 存在")
    void testGetChineseLanguageMethodExists() throws Exception {
        Method method = LanguageData.class.getDeclaredMethod("getChineseLanguage");
        assertNotNull(method);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(LanguageData.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试公共方法 getCurrentLanguage 存在")
    void testGetCurrentLanguageMethodExists() throws Exception {
        Method method = LanguageData.class.getDeclaredMethod("getCurrentLanguage");
        assertNotNull(method);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(String.class, method.getReturnType());
    }

    @Test
    @DisplayName("测试实现 Serializable 接口")
    void testImplementsSerializable() {
        assertTrue(java.io.Serializable.class.isAssignableFrom(LanguageData.class));
    }

    @Test
    @DisplayName("测试 THE_PORT_HAS_ALREADY_BIND 字段")
    void testThePortHasAlreadyBindField() throws Exception {
        Field field = LanguageData.class.getDeclaredField("THE_PORT_HAS_ALREADY_BIND");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试 REMOTE_PORT_OCCUPIED 字段")
    void testRemotePortOccupiedField() throws Exception {
        Field field = LanguageData.class.getDeclaredField("REMOTE_PORT_OCCUPIED");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertTrue(Modifier.isPublic(field.getModifiers()));
    }

    @Test
    @DisplayName("测试 currentLanguage 字段")
    void testCurrentLanguageField() throws Exception {
        Field field = LanguageData.class.getDeclaredField("currentLanguage");
        field.setAccessible(true);
        assertEquals(String.class, field.getType());
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    @DisplayName("测试 getChineseLanguage 返回的中文内容")
    void testChineseLanguageContent() {
        LanguageData data = LanguageData.getChineseLanguage();
        assertTrue(data.REMOTE_PORT_OCCUPIED.contains("端口"));
        assertTrue(data.IF_YOU_SEE_EULA.contains("eula"));
    }

    @Test
    @DisplayName("测试 getChineseLanguage 返回不同的 REMOTE_PORT_OCCUPIED")
    void testChineseRemotePortOccupiedDifferent() {
        LanguageData defaultData = new LanguageData();
        LanguageData chineseData = LanguageData.getChineseLanguage();
        assertNotEquals(defaultData.REMOTE_PORT_OCCUPIED, chineseData.REMOTE_PORT_OCCUPIED);
    }
}
