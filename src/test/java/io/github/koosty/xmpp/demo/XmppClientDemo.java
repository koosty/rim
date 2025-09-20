package io.github.koosty.xmpp.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.netty.tcp.TcpClient;

import java.nio.charset.StandardCharsets;

/**
 * Simple demo client to test XMPP server connectivity
 */
public class XmppClientDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(XmppClientDemo.class);
    
    public static void main(String[] args) {
        logger.info("Starting XMPP Client Demo");
        
        TcpClient.create()
            .host("localhost")
            .port(5222)
            .handle((inbound, outbound) -> {
                // Send XMPP stream initiation
                String streamHeader = "<?xml version='1.0'?>" +
                    "<stream:stream xmlns='jabber:client' " +
                    "xmlns:stream='http://etherx.jabber.org/streams' " +
                    "to='localhost' version='1.0'>";
                
                logger.info("Sending stream header: {}", streamHeader);
                
                return outbound.sendString(reactor.core.publisher.Mono.just(streamHeader))
                    .then()
                    .thenMany(inbound.receive().asString(StandardCharsets.UTF_8))
                    .doOnNext(response -> logger.info("Received: {}", response))
                    .take(5) // Take first 5 responses
                    .then();
            })
            .connect()
            .doOnNext(connection -> logger.info("Connected to XMPP server"))
            .doOnError(error -> logger.error("Connection failed: {}", error.getMessage()))
            .block();
        
        logger.info("XMPP Client Demo completed");
    }
}