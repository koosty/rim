package io.github.koosty.xmpp.server;

import io.github.koosty.xmpp.actor.ActorSystem;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Phase 1 implementation
 */
@SpringBootTest
@TestPropertySource(properties = {
    "xmpp.server.port=15222" // Use different port for testing
})
class XmppTcpServerTest {
    
    @MockBean
    private ActorSystem actorSystem;
    
    @Test
    void contextLoads() {
        // Verify Spring context loads successfully with all beans
        assertTrue(true, "Spring context should load without errors");
    }
    
    @Test
    void serverCanBeCreated() {
        XmppTcpServer server = new XmppTcpServer(actorSystem, 15222);
        assertNotNull(server);
        assertFalse(server.isRunning());
    }
}