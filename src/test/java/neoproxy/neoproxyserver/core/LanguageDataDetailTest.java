package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LanguageData 详细测试")
class LanguageDataDetailTest {

    @Test
    @DisplayName("测试默认构造函数")
    void testDefaultConstructor() throws Exception {
        LanguageData data = new LanguageData();
        assertNotNull(data);
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
    @DisplayName("测试默认语言为 en")
    void testDefaultLanguageIsEn() {
        LanguageData data = new LanguageData();
        assertEquals("en", data.getCurrentLanguage());
    }

    @Test
    @DisplayName("测试中文语言为 zh")
    void testChineseLanguageIsZh() {
        LanguageData data = LanguageData.getChineseLanguage();
        assertEquals("zh", data.getCurrentLanguage());
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
