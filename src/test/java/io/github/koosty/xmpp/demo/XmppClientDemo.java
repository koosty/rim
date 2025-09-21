package io.github.koosty.xmpp.demo;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.impl.JidCreate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced XMPP Client Demo using Smack library
 * This demo shows how to connect to the XMPP server using a real XMPP client
 */
public class XmppClientDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(XmppClientDemo.class);
    
    private static final String XMPP_DOMAIN = "localhost";
    private static final int XMPP_PORT = 5222;
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "test123";
    
    public static void main(String[] args) {
        logger.info("Starting Enhanced XMPP Client Demo using Smack library");
        
        try {
            // Configure XMPP connection
            XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                    .setXmppDomain(XMPP_DOMAIN)
                    .setHost("localhost")
                    .setPort(XMPP_PORT)
                    .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled) // No TLS for demo
                    .build();
            
            AbstractXMPPConnection connection = new XMPPTCPConnection(config);
            
            // Add connection listener
            connection.addConnectionListener(new ConnectionListener() {
                @Override
                public void connected(XMPPConnection connection) {
                    logger.info("Connected to XMPP server: {}", connection.getXMPPServiceDomain());
                }
                
                @Override
                public void authenticated(XMPPConnection connection, boolean resumed) {
                    logger.info("Authenticated as: {}", connection.getUser());
                }
                
                @Override
                public void connectionClosed() {
                    logger.info("Connection closed");
                }
                
                @Override
                public void connectionClosedOnError(Exception e) {
                    logger.error("Connection closed on error: {}", e.getMessage());
                }
            });
            
            // Add message listener
            connection.addAsyncStanzaListener(new StanzaListener() {
                @Override
                public void processStanza(Stanza packet) {
                    if (packet instanceof Message) {
                        Message message = (Message) packet;
                        if (message.getBody() != null) {
                            logger.info("Received message from {}: {}", message.getFrom(), message.getBody());
                        }
                    }
                }
            }, stanza -> stanza instanceof Message);
            
            // Connect and login
            logger.info("Connecting to {}:{}...", XMPP_DOMAIN, XMPP_PORT);
            connection.connect();
            
            logger.info("Logging in as {}...", USERNAME);
            connection.login(USERNAME, PASSWORD);
            
            // Send initial presence - using available presence
            Presence presence = connection.getStanzaFactory()
                    .buildPresenceStanza()
                    .ofType(Presence.Type.available)
                    .setStatus("Available - XMPP Client Demo")
                    .build();
            connection.sendStanza(presence);
            logger.info("Sent presence: {}", presence.getStatus());
            
            // Demo: Send a message to alice if she exists
            try {
                Message message = connection.getStanzaFactory()
                        .buildMessageStanza()
                        .to(JidCreate.entityBareFrom("alice@" + XMPP_DOMAIN))
                        .setBody("Hello from XMPP Client Demo!")
                        .ofType(Message.Type.chat)
                        .build();
                connection.sendStanza(message);
                logger.info("Sent demo message to alice@{}", XMPP_DOMAIN);
            } catch (Exception e) {
                logger.info("Could not send demo message (alice might not be online): {}", e.getMessage());
            }
            
            // Keep connection alive for demo
            logger.info("Demo client is running. Press Ctrl+C to exit.");
            logger.info("You can now connect other XMPP clients to interact with this demo client.");
            
            // Keep the demo running for 30 seconds
            Thread.sleep(30000);
            
            // Send offline presence and disconnect
            Presence offlinePresence = connection.getStanzaFactory()
                    .buildPresenceStanza()
                    .ofType(Presence.Type.unavailable)
                    .setStatus("Demo completed - going offline")
                    .build();
            connection.sendStanza(offlinePresence);
            
            connection.disconnect();
            logger.info("XMPP Client Demo completed successfully");
            
        } catch (Exception e) {
            logger.error("Error in XMPP Client Demo: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}