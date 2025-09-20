package io.github.koosty.xmpp.resource;

import io.github.koosty.xmpp.jid.Jid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourceManagerTest {
    
    private ResourceManager resourceManager;
    private Jid testBareJid;
    
    @BeforeEach
    void setUp() {
        resourceManager = new ResourceManager();
        testBareJid = new Jid("user", "example.com", null);
    }
    
    @Test
    void testGenerateServerResource() {
        String resource = resourceManager.generateResource(testBareJid, null, "conn1");
        
        assertNotNull(resource);
        assertTrue(resource.startsWith("resource-"));
        assertFalse(resourceManager.isResourceAvailable(testBareJid, resource));
    }
    
    @Test
    void testGenerateRequestedResource() {
        String requestedResource = "mobile";
        String resource = resourceManager.generateResource(testBareJid, requestedResource, "conn1");
        
        assertEquals(requestedResource, resource);
        assertFalse(resourceManager.isResourceAvailable(testBareJid, resource));
    }
    
    @Test
    void testResourceConflictResolution() {
        String requestedResource = "mobile";
        
        // First connection gets the requested resource
        String resource1 = resourceManager.generateResource(testBareJid, requestedResource, "conn1");
        assertEquals(requestedResource, resource1);
        
        // Second connection gets a conflict-resolved resource
        String resource2 = resourceManager.generateResource(testBareJid, requestedResource, "conn2");
        assertNotEquals(requestedResource, resource2);
        assertTrue(resource2.startsWith(requestedResource + "-"));
    }
    
    @Test
    void testResourceAvailability() {
        assertTrue(resourceManager.isResourceAvailable(testBareJid, "mobile"));
        
        resourceManager.generateResource(testBareJid, "mobile", "conn1");
        assertFalse(resourceManager.isResourceAvailable(testBareJid, "mobile"));
    }
    
    @Test
    void testReleaseResource() {
        String resource = resourceManager.generateResource(testBareJid, "mobile", "conn1");
        Jid fullJid = testBareJid.withResource(resource);
        
        assertFalse(resourceManager.isResourceAvailable(testBareJid, resource));
        
        boolean released = resourceManager.releaseResource(fullJid, "conn1");
        assertTrue(released);
        assertTrue(resourceManager.isResourceAvailable(testBareJid, resource));
    }
    
    @Test
    void testReleaseResourceUnauthorized() {
        String resource = resourceManager.generateResource(testBareJid, "mobile", "conn1");
        Jid fullJid = testBareJid.withResource(resource);
        
        // Try to release with wrong connection ID
        boolean released = resourceManager.releaseResource(fullJid, "conn2");
        assertFalse(released);
        assertFalse(resourceManager.isResourceAvailable(testBareJid, resource));
    }
    
    @Test
    void testGetActiveResources() {
        assertTrue(resourceManager.getActiveResources(testBareJid).isEmpty());
        
        resourceManager.generateResource(testBareJid, "mobile", "conn1");
        resourceManager.generateResource(testBareJid, "desktop", "conn2");
        
        Set<String> activeResources = resourceManager.getActiveResources(testBareJid);
        assertEquals(2, activeResources.size());
        assertTrue(activeResources.contains("mobile"));
        assertTrue(activeResources.contains("desktop"));
    }
    
    @Test
    void testGetResourceOwner() {
        String resource = resourceManager.generateResource(testBareJid, "mobile", "conn1");
        Jid fullJid = testBareJid.withResource(resource);
        
        assertEquals("conn1", resourceManager.getResourceOwner(fullJid));
        assertTrue(resourceManager.isResourceOwner(fullJid, "conn1"));
        assertFalse(resourceManager.isResourceOwner(fullJid, "conn2"));
    }
    
    @Test
    void testReleaseConnectionResources() {
        resourceManager.generateResource(testBareJid, "mobile", "conn1");
        resourceManager.generateResource(testBareJid, "desktop", "conn1");
        
        Jid otherBareJid = new Jid("other", "example.com", null);
        resourceManager.generateResource(otherBareJid, "tablet", "conn2");
        
        assertEquals(2, resourceManager.getActiveResources(testBareJid).size());
        assertEquals(1, resourceManager.getActiveResources(otherBareJid).size());
        
        int released = resourceManager.releaseConnectionResources("conn1");
        assertEquals(2, released);
        
        assertEquals(0, resourceManager.getActiveResources(testBareJid).size());
        assertEquals(1, resourceManager.getActiveResources(otherBareJid).size());
    }
    
    @Test
    void testResourceCountStatistics() {
        assertEquals(0, resourceManager.getActiveResourceCount());
        
        resourceManager.generateResource(testBareJid, "mobile", "conn1");
        assertEquals(1, resourceManager.getActiveResourceCount());
        
        resourceManager.generateResource(testBareJid, "desktop", "conn2");
        assertEquals(2, resourceManager.getActiveResourceCount());
        
        resourceManager.releaseConnectionResources("conn1");
        assertEquals(1, resourceManager.getActiveResourceCount());
    }
    
    @Test
    void testMultipleUsersResourceIsolation() {
        Jid user1BareJid = new Jid("user1", "example.com", null);
        Jid user2BareJid = new Jid("user2", "example.com", null);
        
        // Both users can have the same resource name
        String resource1 = resourceManager.generateResource(user1BareJid, "mobile", "conn1");
        String resource2 = resourceManager.generateResource(user2BareJid, "mobile", "conn2");
        
        assertEquals("mobile", resource1);
        assertEquals("mobile", resource2);
        
        // Resources are isolated per user
        assertTrue(resourceManager.getActiveResources(user1BareJid).contains("mobile"));
        assertTrue(resourceManager.getActiveResources(user2BareJid).contains("mobile"));
        assertEquals(1, resourceManager.getActiveResources(user1BareJid).size());
        assertEquals(1, resourceManager.getActiveResources(user2BareJid).size());
    }
}