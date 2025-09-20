package io.github.koosty.xmpp.resource;

import io.github.koosty.xmpp.jid.Jid;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ResourceManager handles generation and management of unique resource identifiers.
 * Ensures resource uniqueness per bare JID and manages resource cleanup.
 */
@Component
public class ResourceManager {
    
    // Track active resources per bare JID
    private final Map<String, Set<String>> activeResources = new ConcurrentHashMap<>();
    
    // Resource to connection mapping for cleanup
    private final Map<String, String> resourceToConnection = new ConcurrentHashMap<>();
    
    // Counter for generating sequential resource IDs
    private final AtomicLong resourceCounter = new AtomicLong(0);
    
    /**
     * Generate a unique resource identifier for the given bare JID.
     * If requestedResource is provided and available, use it. Otherwise generate one.
     */
    public String generateResource(Jid bareJid, String requestedResource, String connectionId) {
        String bareJidString = bareJid.toBareJidString();
        
        // Initialize resource set if needed
        Set<String> resources = activeResources.computeIfAbsent(
            bareJidString, 
            k -> ConcurrentHashMap.newKeySet()
        );
        
        String finalResource;
        
        if (requestedResource != null && !requestedResource.isEmpty()) {
            // Check if requested resource is available
            if (resources.contains(requestedResource)) {
                // Generate conflict resolution resource
                finalResource = generateConflictResource(requestedResource);
            } else {
                finalResource = requestedResource;
            }
        } else {
            // Generate server-assigned resource
            finalResource = generateServerResource();
        }
        
        // Ensure uniqueness
        while (resources.contains(finalResource)) {
            finalResource = generateServerResource();
        }
        
        // Register the resource
        resources.add(finalResource);
        resourceToConnection.put(createResourceKey(bareJidString, finalResource), connectionId);
        
        return finalResource;
    }
    
    /**
     * Check if a resource is available for the given bare JID
     */
    public boolean isResourceAvailable(Jid bareJid, String resource) {
        String bareJidString = bareJid.toBareJidString();
        Set<String> resources = activeResources.get(bareJidString);
        
        return resources == null || !resources.contains(resource);
    }
    
    /**
     * Release a resource when session ends
     */
    public boolean releaseResource(Jid fullJid, String connectionId) {
        if (fullJid.resourcepart() == null) {
            return false;
        }
        
        String bareJidString = fullJid.toBareJidString();
        String resource = fullJid.resourcepart();
        String resourceKey = createResourceKey(bareJidString, resource);
        
        // Verify connection owns this resource
        String owningConnection = resourceToConnection.get(resourceKey);
        if (!connectionId.equals(owningConnection)) {
            return false;
        }
        
        // Remove resource
        Set<String> resources = activeResources.get(bareJidString);
        if (resources != null) {
            resources.remove(resource);
            
            // Clean up empty resource sets
            if (resources.isEmpty()) {
                activeResources.remove(bareJidString);
            }
        }
        
        resourceToConnection.remove(resourceKey);
        return true;
    }
    
    /**
     * Get all active resources for a bare JID
     */
    public Set<String> getActiveResources(Jid bareJid) {
        String bareJidString = bareJid.toBareJidString();
        Set<String> resources = activeResources.get(bareJidString);
        
        return resources != null ? Set.copyOf(resources) : Set.of();
    }
    
    /**
     * Get connection ID that owns a specific resource
     */
    public String getResourceOwner(Jid fullJid) {
        if (fullJid.resourcepart() == null) {
            return null;
        }
        
        String resourceKey = createResourceKey(fullJid.toBareJidString(), fullJid.resourcepart());
        return resourceToConnection.get(resourceKey);
    }
    
    /**
     * Check if a connection owns a specific resource
     */
    public boolean isResourceOwner(Jid fullJid, String connectionId) {
        String owner = getResourceOwner(fullJid);
        return connectionId.equals(owner);
    }
    
    /**
     * Release all resources owned by a connection (cleanup on disconnect)
     */
    public int releaseConnectionResources(String connectionId) {
        int releasedCount = 0;
        
        // Find all resources owned by this connection
        var resourcesToRelease = resourceToConnection.entrySet().stream()
            .filter(entry -> connectionId.equals(entry.getValue()))
            .map(Map.Entry::getKey)
            .toList();
        
        // Release each resource
        for (String resourceKey : resourcesToRelease) {
            String[] parts = resourceKey.split(":", 2);
            if (parts.length == 2) {
                String bareJidString = parts[0];
                String resource = parts[1];
                
                Set<String> resources = activeResources.get(bareJidString);
                if (resources != null) {
                    resources.remove(resource);
                    
                    if (resources.isEmpty()) {
                        activeResources.remove(bareJidString);
                    }
                }
                
                resourceToConnection.remove(resourceKey);
                releasedCount++;
            }
        }
        
        return releasedCount;
    }
    
    /**
     * Get total number of active resources across all users
     */
    public int getActiveResourceCount() {
        return resourceToConnection.size();
    }
    
    /**
     * Generate a server-assigned resource identifier
     */
    private String generateServerResource() {
        return "resource-" + resourceCounter.incrementAndGet() + "-" + 
               UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Generate a conflict resolution resource when requested resource is taken
     */
    private String generateConflictResource(String requestedResource) {
        return requestedResource + "-" + System.currentTimeMillis() % 10000;
    }
    
    /**
     * Create resource key for internal mapping
     */
    private String createResourceKey(String bareJid, String resource) {
        return bareJid + ":" + resource;
    }
}