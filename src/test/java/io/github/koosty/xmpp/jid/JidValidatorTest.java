package io.github.koosty.xmpp.jid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JidValidatorTest {
    
    private JidValidator jidValidator;
    
    @BeforeEach
    void setUp() {
        jidValidator = new JidValidator();
    }
    
    @Test
    void testValidBareJid() {
        Optional<Jid> jid = jidValidator.parseJid("user@example.com");
        
        assertTrue(jid.isPresent());
        assertEquals("user", jid.get().localpart());
        assertEquals("example.com", jid.get().domainpart());
        assertNull(jid.get().resourcepart());
        assertTrue(jid.get().isBareJid());
    }
    
    @Test
    void testValidFullJid() {
        Optional<Jid> jid = jidValidator.parseJid("user@example.com/resource");
        
        assertTrue(jid.isPresent());
        assertEquals("user", jid.get().localpart());
        assertEquals("example.com", jid.get().domainpart());
        assertEquals("resource", jid.get().resourcepart());
        assertTrue(jid.get().isFullJid());
    }
    
    @Test
    void testServerJid() {
        Optional<Jid> jid = jidValidator.parseJid("example.com");
        
        assertTrue(jid.isPresent());
        assertNull(jid.get().localpart());
        assertEquals("example.com", jid.get().domainpart());
        assertNull(jid.get().resourcepart());
        assertTrue(jid.get().isServerJid());
    }
    
    @Test
    void testInvalidJid() {
        assertFalse(jidValidator.parseJid("").isPresent());
        assertFalse(jidValidator.parseJid("   ").isPresent());
        assertFalse(jidValidator.parseJid(null).isPresent());
        assertFalse(jidValidator.parseJid("invalid..domain").isPresent());
        assertFalse(jidValidator.parseJid("user@").isPresent());
    }
    
    @Test
    void testLocalpartValidation() {
        assertTrue(jidValidator.isValidLocalpart("user"));
        assertTrue(jidValidator.isValidLocalpart("test123"));
        assertTrue(jidValidator.isValidLocalpart("user.name"));
        
        assertFalse(jidValidator.isValidLocalpart(""));
        assertFalse(jidValidator.isValidLocalpart(null));
        assertFalse(jidValidator.isValidLocalpart("user@invalid"));
        assertFalse(jidValidator.isValidLocalpart("user with spaces"));
        assertFalse(jidValidator.isValidLocalpart("user<test>"));
    }
    
    @Test
    void testDomainpartValidation() {
        assertTrue(jidValidator.isValidDomainpart("example.com"));
        assertTrue(jidValidator.isValidDomainpart("sub.example.org"));
        assertTrue(jidValidator.isValidDomainpart("localhost"));
        
        assertFalse(jidValidator.isValidDomainpart(""));
        assertFalse(jidValidator.isValidDomainpart(null));
        assertFalse(jidValidator.isValidDomainpart(".example.com"));
        assertFalse(jidValidator.isValidDomainpart("example.com."));
        assertFalse(jidValidator.isValidDomainpart("example..com"));
    }
    
    @Test
    void testResourcepartValidation() {
        assertTrue(jidValidator.isValidResourcepart("resource"));
        assertTrue(jidValidator.isValidResourcepart("Resource123"));
        assertTrue(jidValidator.isValidResourcepart("mobile device"));
        assertTrue(jidValidator.isValidResourcepart("桌面"));
        
        assertFalse(jidValidator.isValidResourcepart(""));
        assertFalse(jidValidator.isValidResourcepart(null));
        assertFalse(jidValidator.isValidResourcepart("resource\u0000"));
        assertFalse(jidValidator.isValidResourcepart("resource\u001F"));
    }
    
    @Test
    void testJidNormalization() {
        Optional<Jid> jid = jidValidator.parseJid("USER@EXAMPLE.COM/Resource");
        
        assertTrue(jid.isPresent());
        assertEquals("user", jid.get().localpart()); // normalized to lowercase
        assertEquals("example.com", jid.get().domainpart()); // normalized to lowercase
        assertEquals("Resource", jid.get().resourcepart()); // case preserved
    }
    
    @Test
    void testCreateBareJid() {
        Optional<Jid> jid = jidValidator.createBareJid("user", "example.com");
        
        assertTrue(jid.isPresent());
        assertEquals("user", jid.get().localpart());
        assertEquals("example.com", jid.get().domainpart());
        assertNull(jid.get().resourcepart());
    }
    
    @Test
    void testCreateFullJid() {
        Optional<Jid> jid = jidValidator.createFullJid("user", "example.com", "resource");
        
        assertTrue(jid.isPresent());
        assertEquals("user", jid.get().localpart());
        assertEquals("example.com", jid.get().domainpart());
        assertEquals("resource", jid.get().resourcepart());
    }
    
    @Test
    void testJidToString() {
        Jid bareJid = new Jid("user", "example.com", null);
        assertEquals("user@example.com", bareJid.toString());
        
        Jid fullJid = new Jid("user", "example.com", "resource");
        assertEquals("user@example.com/resource", fullJid.toString());
        
        Jid serverJid = new Jid(null, "example.com", null);
        assertEquals("example.com", serverJid.toString());
    }
    
    @Test
    void testJidManipulation() {
        Jid fullJid = new Jid("user", "example.com", "resource");
        
        Jid bareJid = fullJid.toBareJid();
        assertEquals("user", bareJid.localpart());
        assertEquals("example.com", bareJid.domainpart());
        assertNull(bareJid.resourcepart());
        
        Jid newJid = bareJid.withResource("newresource");
        assertEquals("newresource", newJid.resourcepart());
        assertEquals("user@example.com", newJid.toBareJidString());
    }
}