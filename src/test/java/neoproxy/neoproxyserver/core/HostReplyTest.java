package neoproxy.neoproxyserver.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HostReply 测试")
class HostReplyTest {

    @Test
    @DisplayName("测试HostReply record创建")
    void testHostReplyCreation() {
        HostReply reply = new HostReply(12345L, null);
        
        assertEquals(12345L, reply.socketID());
        assertNull(reply.host());
    }

    @Test
    @DisplayName("测试HostReply record - 负数socketID")
    void testHostReply_NegativeSocketID() {
        HostReply reply = new HostReply(-1L, null);
        
        assertEquals(-1L, reply.socketID());
    }

    @Test
    @DisplayName("测试HostReply record - 零socketID")
    void testHostReply_ZeroSocketID() {
        HostReply reply = new HostReply(0L, null);
        
        assertEquals(0L, reply.socketID());
    }

    @Test
    @DisplayName("测试HostReply equals方法")
    void testHostReply_Equals() {
        HostReply reply1 = new HostReply(12345L, null);
        HostReply reply2 = new HostReply(12345L, null);
        
        assertEquals(reply1, reply2);
    }

    @Test
    @DisplayName("测试HostReply hashCode方法")
    void testHostReply_HashCode() {
        HostReply reply1 = new HostReply(12345L, null);
        HostReply reply2 = new HostReply(12345L, null);
        
        assertEquals(reply1.hashCode(), reply2.hashCode());
    }

    @Test
    @DisplayName("测试HostReply toString方法")
    void testHostReply_ToString() {
        HostReply reply = new HostReply(12345L, null);
        
        String str = reply.toString();
        
        assertTrue(str.contains("12345"));
    }
}
