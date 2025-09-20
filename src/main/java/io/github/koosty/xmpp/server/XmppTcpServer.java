package io.github.koosty.xmpp.server;

import io.github.koosty.xmpp.actor.ActorSystem;
import io.github.koosty.xmpp.actor.ConnectionActor;
import io.github.koosty.xmpp.actor.message.IncomingXmlMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;

import java.time.Duration;
import java.util.UUID;

/**
 * XMPP TCP server using Reactor Netty for handling client connections on port 5222.
 * Implements RFC6120 TCP binding with reactive I/O and Actor-based connection handling.
 */
@Component
public class XmppTcpServer {
    
    private static final Logger logger = LoggerFactory.getLogger(XmppTcpServer.class);
    
    private final ActorSystem actorSystem;
    private final int port;
    private DisposableServer server;
    
    public XmppTcpServer(ActorSystem actorSystem, 
                        @Value("${xmpp.server.port:5222}") int port) {
        this.actorSystem = actorSystem;
        this.port = port;
    }
    
    /**
     * Start the XMPP TCP server
     */
    public Mono<Void> start() {
        logger.info("Starting XMPP TCP Server on port {}", port);
        
        return TcpServer.create()
            .port(port)
            .doOnConnection(connection -> {
                logger.debug("New connection established: {}", connection.channel().id());
            })
            .handle(this::handleConnection)
            .bind()
            .doOnNext(server -> {
                this.server = server;
                logger.info("XMPP TCP Server started on {}:{}", server.host(), server.port());
            })
            .then();
    }
    
    /**
     * Stop the XMPP TCP server
     */
    public Mono<Void> stop() {
        if (server != null && !server.isDisposed()) {
            logger.info("Stopping XMPP TCP Server");
            
            // Shutdown actor system first
            actorSystem.shutdown();
            
            // Dispose server and return completion
            server.disposeNow();
            logger.info("XMPP TCP Server stopped");
            return Mono.empty();
        }
        
        return Mono.empty();
    }
    
    /**
     * Check if server is running
     */
    public boolean isRunning() {
        return server != null && !server.isDisposed();
    }
    
    private Mono<Void> handleConnection(reactor.netty.NettyInbound inbound, 
                                       reactor.netty.NettyOutbound outbound) {
        
        // Generate unique connection ID
        String connectionId = generateConnectionId();
        logger.info("Handling new XMPP connection: {}", connectionId);
        
        // Create connection actor
        ConnectionActor connectionActor = actorSystem.createConnectionActor(connectionId, outbound);
        
        // Handle incoming data
        return inbound.receive()
            .asString()
            .doOnNext(xmlData -> {
                logger.debug("Received XML data on connection {}: {}", connectionId, xmlData.trim());
                
                // Send XML data to connection actor
                IncomingXmlMessage message = IncomingXmlMessage.of(connectionId, xmlData);
                connectionActor.tell(message);
            })
            .doOnError(error -> {
                logger.error("Error on connection {}: {}", connectionId, error.getMessage());
                actorSystem.removeConnectionActor(connectionId);
            })
            .doOnComplete(() -> {
                logger.info("Connection {} closed", connectionId);
                actorSystem.removeConnectionActor(connectionId);
            })
            .doOnCancel(() -> {
                logger.info("Connection {} cancelled", connectionId);
                actorSystem.removeConnectionActor(connectionId);
            })
            .then()
            .timeout(Duration.ofMinutes(30)) // RFC6120 recommends connection timeout
            .onErrorResume(error -> {
                logger.error("Connection {} timed out or failed: {}", connectionId, error.getMessage());
                actorSystem.removeConnectionActor(connectionId);
                return Mono.empty();
            });
    }
    
    private String generateConnectionId() {
        return "conn-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Get server statistics
     */
    public ServerStats getStats() {
        return new ServerStats(
            isRunning(),
            port,
            actorSystem.getActiveConnectionCount(),
            actorSystem.getHealthyConnectionCount()
        );
    }
    
    /**
     * Server statistics record
     */
    public record ServerStats(
        boolean running,
        int port,
        int activeConnections,
        long healthyConnections
    ) {}
}