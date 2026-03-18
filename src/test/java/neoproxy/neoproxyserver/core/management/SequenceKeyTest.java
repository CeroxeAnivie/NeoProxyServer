package neoproxy.neoproxyserver.core.management;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import neoproxy.neoproxyserver.core.ConfigOperator;
import neoproxy.neoproxyserver.core.exceptions.NoMoreNetworkFlowException;
import neoproxy.neoproxyserver.core.management.provider.KeyDataProvider;
import neoproxy.neoproxyserver.core.management.provider.LocalKeyProvider;
import neoproxy.neoproxyserver.core.management.provider.RemoteKeyProvider;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SequenceKey 测试")
@ExtendWith(MockitoExtension.class)
class SequenceKeyTest {

    private SequenceKey sequenceKey;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd-HH:mm");

    @BeforeEach
    void setUp() {
        String futureExpireTime = LocalDateTime.now().plusDays(30).format(FORMATTER);
        sequenceKey = new SequenceKey("test-key", 1000.0, futureExpireTime, "8080", 100.0, true, true);
    }

    @Test
    @DisplayName("测试构造器 - 正常参数")
    void testConstructor() {
        assertNotNull(sequenceKey);
        assertEquals("test-key", sequenceKey.getName());
        assertEquals(1000.0, sequenceKey.getBalance());
        assertEquals(100.0, sequenceKey.getRate());
        assertTrue(sequenceKey.isEnable());
        assertTrue(sequenceKey.isHTMLEnabled());
    }

    @Test
    @DisplayName("测试getPort - 固定端口")
    void testGetPort_FixedPort() {
        assertEquals(8080, sequenceKey.getPort());
    }

    @Test
    @DisplayName("测试getPort - 动态端口范围")
    void testGetPort_DynamicRange() {
        SequenceKey key = new SequenceKey("key", 100, "PERMANENT", "10000-20000", 50, true, false);
        
        assertEquals(10000, key.getDyStart());
        assertEquals(20000, key.getDyEnd());
        assertEquals(SequenceKey.DYNAMIC_PORT, key.getPort());
    }

    @Test
    @DisplayName("测试getPort - null端口")
    void testGetPort_NullPort() {
        SequenceKey key = new SequenceKey("key", 100, "PERMANENT", null, 50, true, false);
        
        assertEquals(SequenceKey.DYNAMIC_PORT, key.getPort());
    }

    @Test
    @DisplayName("测试getPort - 无效端口")
    void testGetPort_InvalidPort() {
        SequenceKey key = new SequenceKey("key", 100, "PERMANENT", "invalid", 50, true, false);
        
        assertEquals(SequenceKey.DYNAMIC_PORT, key.getPort());
    }

    @Test
    @DisplayName("测试isOutOfDate - 未过期")
    void testIsOutOfDate_NotExpired() {
        assertFalse(sequenceKey.isOutOfDate());
    }

    @Test
    @DisplayName("测试isOutOfDate - 已过期")
    void testIsOutOfDate_Expired() {
        String pastTime = LocalDateTime.now().minusDays(1).format(FORMATTER);
        SequenceKey key = new SequenceKey("key", 100, pastTime, "8080", 50, true, false);
        
        assertTrue(key.isOutOfDate());
    }

    @Test
    @DisplayName("测试isOutOfDate - 永久有效")
    void testIsOutOfDate_Permanent() {
        SequenceKey key = new SequenceKey("key", 100, "PERMANENT", "8080", 50, true, false);
        
        assertFalse(key.isOutOfDate());
    }

    @Test
    @DisplayName("测试isOutOfDate静态方法 - null参数")
    void testIsOutOfDate_StaticNull() {
        assertFalse(SequenceKey.isOutOfDate(null));
    }

    @Test
    @DisplayName("测试isOutOfDate静态方法 - 空字符串")
    void testIsOutOfDate_StaticEmpty() {
        assertFalse(SequenceKey.isOutOfDate(""));
    }

    @Test
    @DisplayName("测试isOutOfDate静态方法 - PERMANENT")
    void testIsOutOfDate_StaticPermanent() {
        assertFalse(SequenceKey.isOutOfDate("PERMANENT"));
    }

    @Test
    @DisplayName("测试setBalance方法")
    void testSetBalance() {
        sequenceKey.setBalance(500.0);
        assertEquals(500.0, sequenceKey.getBalance());
    }

    @Test
    @DisplayName("测试setRate方法")
    void testSetRate() {
        sequenceKey.setRate(200.0);
        assertEquals(200.0, sequenceKey.getRate());
    }

    @Test
    @DisplayName("测试setEnable方法")
    void testSetEnable() {
        sequenceKey.setEnable(false);
        assertFalse(sequenceKey.isEnable());
    }

    @Test
    @DisplayName("测试setHTMLEnabled方法")
    void testSetHTMLEnabled() {
        sequenceKey.setHTMLEnabled(false);
        assertFalse(sequenceKey.isHTMLEnabled());
    }

    @Test
    @DisplayName("测试setPort方法")
    void testSetPort() {
        sequenceKey.setPort("9090");
        assertEquals(9090, sequenceKey.getPort());
    }

    @Test
    @DisplayName("测试setExpireTime方法")
    void testSetExpireTime() {
        String newExpireTime = LocalDateTime.now().plusDays(60).format(FORMATTER);
        sequenceKey.setExpireTime(newExpireTime);
        assertEquals(newExpireTime, sequenceKey.getExpireTime());
    }

    @Test
    @DisplayName("测试getBalanceNoLock方法")
    void testGetBalanceNoLock() {
        assertEquals(1000.0, sequenceKey.getBalanceNoLock());
    }

    @Test
    @DisplayName("测试getRateNoLock方法")
    void testGetRateNoLock() {
        assertEquals(100.0, sequenceKey.getRateNoLock());
    }

    @Test
    @DisplayName("测试refreshFrom方法 - null参数")
    void testRefreshFrom_Null() {
        assertDoesNotThrow(() -> sequenceKey.refreshFrom(null));
    }

    @Test
    @DisplayName("测试refreshFrom方法 - 正常刷新")
    void testRefreshFrom_Normal() {
        String futureExpireTime = LocalDateTime.now().plusDays(60).format(FORMATTER);
        SequenceKey freshKey = new SequenceKey("test-key", 2000.0, futureExpireTime, "9090", 200.0, false, false);
        
        sequenceKey.refreshFrom(freshKey);
        
        assertEquals(2000.0, sequenceKey.getBalance());
        assertEquals(200.0, sequenceKey.getRate());
        assertFalse(sequenceKey.isEnable());
        assertFalse(sequenceKey.isHTMLEnabled());
        assertEquals(9090, sequenceKey.getPort());
    }

    @Test
    @DisplayName("测试mineMib方法 - 正常扣减")
    void testMineMib_Normal() {
        sequenceKey.mineMib("test", 100.0);
        assertEquals(900.0, sequenceKey.getBalance());
    }

    @Test
    @DisplayName("测试mineMib方法 - 零流量不扣减")
    void testMineMib_Zero() {
        double originalBalance = sequenceKey.getBalance();
        sequenceKey.mineMib("test", 0);
        assertEquals(originalBalance, sequenceKey.getBalance());
    }

    @Test
    @DisplayName("测试mineMib方法 - 负流量不扣减")
    void testMineMib_Negative() {
        double originalBalance = sequenceKey.getBalance();
        sequenceKey.mineMib("test", -100.0);
        assertEquals(originalBalance, sequenceKey.getBalance());
    }

    @Test
    @DisplayName("测试mineMib方法 - 已过期密钥抛出异常")
    void testMineMib_Expired() {
        String pastTime = LocalDateTime.now().minusDays(1).format(FORMATTER);
        SequenceKey expiredKey = new SequenceKey("expired-key", 100, pastTime, "8080", 50, true, false);
        
        assertThrows(NoMoreNetworkFlowException.class, () -> expiredKey.mineMib("test", 10.0));
    }

    @Test
    @DisplayName("测试mineMib方法 - 禁用密钥抛出异常")
    void testMineMib_Disabled() {
        SequenceKey disabledKey = new SequenceKey("disabled-key", 100, "PERMANENT", "8080", 50, false, false);
        
        assertThrows(NoMoreNetworkFlowException.class, () -> disabledKey.mineMib("test", 10.0));
    }

    @Test
    @DisplayName("测试DYNAMIC_PORT常量")
    void testDynamicPortConstant() {
        assertEquals(-1, SequenceKey.DYNAMIC_PORT);
    }

    @Test
    @DisplayName("测试getKeyCacheSnapshot方法")
    void testGetKeyCacheSnapshot() {
        assertNotNull(SequenceKey.getKeyCacheSnapshot());
    }

    @Test
    @DisplayName("测试parseRange - 无效范围格式")
    void testParseRange_InvalidFormat() {
        SequenceKey key = new SequenceKey("key", 100, "PERMANENT", "invalid-range", 50, true, false);
        
        assertEquals(SequenceKey.DYNAMIC_PORT, key.getDyStart());
        assertEquals(SequenceKey.DYNAMIC_PORT, key.getDyEnd());
    }

    @Test
    @DisplayName("测试parseRange - 范围超出有效值")
    void testParseRange_OutOfRange() {
        SequenceKey key = new SequenceKey("key", 100, "PERMANENT", "70000-80000", 50, true, false);
        
        assertEquals(SequenceKey.DYNAMIC_PORT, key.getDyStart());
        assertEquals(SequenceKey.DYNAMIC_PORT, key.getDyEnd());
    }

    @Test
    @DisplayName("测试getPortStr方法")
    void testGetPortStr() {
        assertEquals("8080", sequenceKey.getPortStr());
    }
}
