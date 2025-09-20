package io.github.koosty.xmpp.jid;

import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.regex.Pattern;
import java.text.Normalizer;

/**
 * JID (Jabber ID) validator implementing RFC6120 Section 3 compliance.
 * Validates and normalizes localpart, domainpart, and resourcepart components.
 */
@Component
public class JidValidator {
    
    // RFC6120 Section 3.3.1 - Localpart restrictions
    private static final Pattern LOCALPART_DISALLOWED = Pattern.compile(
        "[\\x00-\\x1F\\x7F\"&'/:<>@\\s]"
    );
    
    // RFC6120 Section 3.2.1 - Domainpart validation (simplified)
    private static final Pattern DOMAINPART_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"
    );
    
    // RFC6120 Section 3.4.1 - Resourcepart restrictions
    private static final Pattern RESOURCEPART_DISALLOWED = Pattern.compile(
        "[\\x00-\\x1F\\x7F]"
    );
    
    private static final int MAX_LOCALPART_LENGTH = 1023;
    private static final int MAX_DOMAINPART_LENGTH = 1023;
    private static final int MAX_RESOURCEPART_LENGTH = 1023;
    
    /**
     * Parse and validate a full JID string.
     * Format: [localpart@]domainpart[/resourcepart]
     */
    public Optional<Jid> parseJid(String jidString) {
        if (jidString == null || jidString.trim().isEmpty()) {
            return Optional.empty();
        }
        
        String trimmed = jidString.trim();
        
        // Parse components
        String localpart = null;
        String domainpart;
        String resourcepart = null;
        
        // Extract resourcepart if present
        int slashIndex = trimmed.lastIndexOf('/');
        if (slashIndex != -1) {
            resourcepart = trimmed.substring(slashIndex + 1);
            trimmed = trimmed.substring(0, slashIndex);
        }
        
        // Extract localpart if present
        int atIndex = trimmed.lastIndexOf('@');
        if (atIndex != -1) {
            localpart = trimmed.substring(0, atIndex);
            domainpart = trimmed.substring(atIndex + 1);
        } else {
            domainpart = trimmed;
        }
        
        // Validate components
        if (!isValidDomainpart(domainpart)) {
            return Optional.empty();
        }
        
        if (localpart != null && !isValidLocalpart(localpart)) {
            return Optional.empty();
        }
        
        if (resourcepart != null && !isValidResourcepart(resourcepart)) {
            return Optional.empty();
        }
        
        // Normalize components
        String normalizedLocalpart = localpart != null ? normalizeLocalpart(localpart) : null;
        String normalizedDomainpart = normalizeDomainpart(domainpart);
        String normalizedResourcepart = resourcepart != null ? normalizeResourcepart(resourcepart) : null;
        
        return Optional.of(new Jid(normalizedLocalpart, normalizedDomainpart, normalizedResourcepart));
    }
    
    /**
     * Validate localpart according to RFC6120 Section 3.3
     */
    public boolean isValidLocalpart(String localpart) {
        if (localpart == null || localpart.isEmpty()) {
            return false;
        }
        
        if (localpart.length() > MAX_LOCALPART_LENGTH) {
            return false;
        }
        
        // Check for disallowed characters
        if (LOCALPART_DISALLOWED.matcher(localpart).find()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate domainpart according to RFC6120 Section 3.2
     */
    public boolean isValidDomainpart(String domainpart) {
        if (domainpart == null || domainpart.isEmpty()) {
            return false;
        }
        
        if (domainpart.length() > MAX_DOMAINPART_LENGTH) {
            return false;
        }
        
        // Basic domain validation
        if (!DOMAINPART_PATTERN.matcher(domainpart).matches()) {
            return false;
        }
        
        // Additional checks for valid domain format
        if (domainpart.startsWith(".") || domainpart.endsWith(".") || 
            domainpart.contains("..")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate resourcepart according to RFC6120 Section 3.4
     */
    public boolean isValidResourcepart(String resourcepart) {
        if (resourcepart == null || resourcepart.isEmpty()) {
            return false;
        }
        
        if (resourcepart.length() > MAX_RESOURCEPART_LENGTH) {
            return false;
        }
        
        // Check for disallowed control characters
        if (RESOURCEPART_DISALLOWED.matcher(resourcepart).find()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Normalize localpart using stringprep profile
     */
    private String normalizeLocalpart(String localpart) {
        // Basic normalization - in production would use stringprep
        return Normalizer.normalize(localpart.toLowerCase(), Normalizer.Form.NFKC);
    }
    
    /**
     * Normalize domainpart to lowercase
     */
    private String normalizeDomainpart(String domainpart) {
        return Normalizer.normalize(domainpart.toLowerCase(), Normalizer.Form.NFKC);
    }
    
    /**
     * Normalize resourcepart preserving case sensitivity
     */
    private String normalizeResourcepart(String resourcepart) {
        return Normalizer.normalize(resourcepart, Normalizer.Form.NFKC);
    }
    
    /**
     * Create a bare JID (without resourcepart)
     */
    public Optional<Jid> createBareJid(String localpart, String domainpart) {
        if (!isValidDomainpart(domainpart)) {
            return Optional.empty();
        }
        
        if (localpart != null && !isValidLocalpart(localpart)) {
            return Optional.empty();
        }
        
        String normalizedLocalpart = localpart != null ? normalizeLocalpart(localpart) : null;
        String normalizedDomainpart = normalizeDomainpart(domainpart);
        
        return Optional.of(new Jid(normalizedLocalpart, normalizedDomainpart, null));
    }
    
    /**
     * Create a full JID with resource
     */
    public Optional<Jid> createFullJid(String localpart, String domainpart, String resourcepart) {
        if (!isValidDomainpart(domainpart) || !isValidResourcepart(resourcepart)) {
            return Optional.empty();
        }
        
        if (localpart != null && !isValidLocalpart(localpart)) {
            return Optional.empty();
        }
        
        String normalizedLocalpart = localpart != null ? normalizeLocalpart(localpart) : null;
        String normalizedDomainpart = normalizeDomainpart(domainpart);
        String normalizedResourcepart = normalizeResourcepart(resourcepart);
        
        return Optional.of(new Jid(normalizedLocalpart, normalizedDomainpart, normalizedResourcepart));
    }
}