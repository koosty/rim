# Hybrid Reactor-Actor Architecture Design

## Overview

This document describes the hybrid architecture pattern combining Reactor's reactive I/O with Actor-like message processing for the XMPP server implementation.

## Architecture Components

### 1. Reactive I/O Layer (Reactor Netty)
```java
// High-performance TCP server handling thousands of connections
@Component
public class XmppTcpServer {
    private final ActorSystem actorSystem;
    
    public Mono<Void> start() {
        return TcpServer.create()
            .port(5222)
            .handle((inbound, outbound) -> handleConnection(inbound, outbound))
            .bind()
            .then();
    }
    
    private Mono<Void> handleConnection(NettyInbound inbound, NettyOutbound outbound) {
        String connectionId = UUID.randomUUID().toString();
        
        // Create connection actor for this specific connection
        ConnectionActor actor = actorSystem.createConnectionActor(connectionId);
        
        // Bridge reactive streams to actor messages
        return inbound.receive()
            .asString()
            .doOnNext(xmlData -> actor.tell(new IncomingXmlMessage(xmlData)))
            .then();
    }
}
```

### 2. Actor-Like Components

#### Connection Actor
```java
@Component
public class ConnectionActor {
    private final String connectionId;
    private final BlockingQueue<ActorMessage> mailbox = new LinkedBlockingQueue<>();
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private volatile Thread processingThread;
    
    public void tell(ActorMessage message) {
        mailbox.offer(message);
    }
    
    public void start() {
        processingThread = Thread.startVirtualThread(this::processMessages);
    }
    
    private void processMessages() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ActorMessage message = mailbox.take();
                handleMessage(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void handleMessage(ActorMessage message) {
        switch (message.getType()) {
            case INCOMING_XML -> processIncomingXml((IncomingXmlMessage) message);
            case TLS_NEGOTIATION -> handleTlsNegotiation((TlsNegotiationMessage) message);
            case SASL_AUTH -> handleSaslAuth((SaslAuthMessage) message);
            case RESOURCE_BINDING -> handleResourceBinding((ResourceBindingMessage) message);
            case OUTGOING_STANZA -> sendStanza((OutgoingStanzaMessage) message);
        }
    }
}
```

#### Session Actor
```java
@Component  
public class SessionActor {
    private final JID userJid;
    private final Set<String> activeConnections = ConcurrentHashMap.newKeySet();
    private final AtomicReference<PresenceState> presence = new AtomicReference<>(PresenceState.UNAVAILABLE);
    private final BlockingQueue<ActorMessage> mailbox = new LinkedBlockingQueue<>();
    
    public void tell(ActorMessage message) {
        mailbox.offer(message);
    }
    
    private void processMessages() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ActorMessage message = mailbox.take();
                handleMessage(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void handleMessage(ActorMessage message) {
        switch (message.getType()) {
            case CONNECTION_BOUND -> addConnection((ConnectionBoundMessage) message);
            case CONNECTION_CLOSED -> removeConnection((ConnectionClosedMessage) message);
            case PRESENCE_UPDATE -> updatePresence((PresenceUpdateMessage) message);
            case INCOMING_MESSAGE -> routeMessage((IncomingMessageStanza) message);
        }
    }
}
```

### 3. Actor System Management

#### Actor System
```java
@Component
public class ActorSystem {
    private final Map<String, ConnectionActor> connectionActors = new ConcurrentHashMap<>();
    private final Map<JID, SessionActor> sessionActors = new ConcurrentHashMap<>();
    private final MessageRoutingActor messageRouter;
    private final PresenceActor presenceManager;
    private final ActorSupervision supervision;
    
    public ConnectionActor createConnectionActor(String connectionId) {
        ConnectionActor actor = new ConnectionActor(connectionId, this);
        connectionActors.put(connectionId, actor);
        actor.start();
        
        supervision.supervise(actor);
        return actor;
    }
    
    public SessionActor getOrCreateSessionActor(JID userJid) {
        return sessionActors.computeIfAbsent(userJid, jid -> {
            SessionActor actor = new SessionActor(jid, this);
            actor.start();
            supervision.supervise(actor);
            return actor;
        });
    }
    
    public void routeMessage(String fromConnectionId, XmppStanza stanza) {
        messageRouter.tell(new RouteStanzaMessage(fromConnectionId, stanza));
    }
}
```

#### Actor Supervision
```java
@Component
public class ActorSupervision {
    private final Map<Actor, SupervisionStrategy> supervisionStrategies = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    public void supervise(Actor actor) {
        SupervisionStrategy strategy = determineStrategy(actor);
        supervisionStrategies.put(actor, strategy);
        
        // Monitor actor health
        scheduler.scheduleAtFixedRate(() -> checkActorHealth(actor), 30, 30, TimeUnit.SECONDS);
    }
    
    private void checkActorHealth(Actor actor) {
        if (!actor.isHealthy()) {
            SupervisionStrategy strategy = supervisionStrategies.get(actor);
            strategy.handleFailure(actor);
        }
    }
    
    private SupervisionStrategy determineStrategy(Actor actor) {
        if (actor instanceof ConnectionActor) {
            return SupervisionStrategy.RESTART; // Restart failed connections
        } else if (actor instanceof SessionActor) {
            return SupervisionStrategy.ESCALATE; // Escalate session failures
        } else {
            return SupervisionStrategy.RESUME; // Resume other actors
        }
    }
}
```

### 4. Message Types

#### Actor Messages
```java
public sealed interface ActorMessage permits 
    IncomingXmlMessage, 
    OutgoingStanzaMessage, 
    TlsNegotiationMessage,
    SaslAuthMessage,
    ResourceBindingMessage,
    PresenceUpdateMessage,
    ConnectionBoundMessage,
    ConnectionClosedMessage,
    RouteStanzaMessage {
    
    MessageType getType();
    String getSender();
    Instant getTimestamp();
}

public record IncomingXmlMessage(
    String connectionId, 
    String xmlData, 
    Instant timestamp
) implements ActorMessage {
    @Override
    public MessageType getType() { return MessageType.INCOMING_XML; }
    @Override
    public String getSender() { return connectionId; }
    @Override
    public Instant getTimestamp() { return timestamp; }
}

public record OutgoingStanzaMessage(
    String connectionId,
    XmppStanza stanza,
    Instant timestamp
) implements ActorMessage {
    @Override
    public MessageType getType() { return MessageType.OUTGOING_STANZA; }
    @Override
    public String getSender() { return connectionId; }
    @Override
    public Instant getTimestamp() { return timestamp; }
}
```

## Benefits of Hybrid Architecture

### 1. Performance Benefits
- **I/O Performance**: Reactor Netty handles thousands of concurrent connections efficiently
- **Message Processing**: Actor-like sequential processing eliminates race conditions
- **Memory Efficiency**: Shared reactive infrastructure with isolated actor state

### 2. Fault Tolerance
- **Connection Isolation**: Failed connections don't affect others
- **Supervision**: Actor supervision handles failures gracefully
- **State Protection**: Each actor owns its state, preventing corruption

### 3. Maintainability
- **Clear Boundaries**: Each actor has well-defined responsibilities
- **Sequential Logic**: Actor message processing is easier to reason about
- **Testability**: Actors can be tested in isolation

### 4. Scalability
- **Horizontal Scaling**: Actors can be distributed across JVMs later
- **Backpressure**: Built-in mailbox queuing handles load spikes
- **Resource Management**: Actors can be created and destroyed as needed

## Implementation Guidelines

### Actor Best Practices
1. **Single Responsibility**: Each actor handles one concern (connection, session, routing)
2. **Immutable Messages**: All actor messages should be immutable records
3. **No Shared State**: Actors should not share mutable state
4. **Sequential Processing**: Process messages one at a time per actor
5. **Supervision**: Always implement proper supervision strategies

### Integration Patterns
1. **Reactive Bridge**: Bridge reactive streams to actor messages
2. **Actor Coordination**: Use dedicated actors for coordinating other actors  
3. **Message Routing**: Central routing actor for inter-actor communication
4. **State Management**: Use actors for complex state that needs isolation

### Performance Considerations
1. **Virtual Threads**: Use Project Loom virtual threads for actor processing
2. **Mailbox Sizing**: Configure appropriate mailbox sizes for different actor types
3. **Message Batching**: Consider batching small messages for better throughput
4. **Actor Lifecycle**: Properly manage actor creation and destruction

## Migration Path

### Phase 1: Core Infrastructure
1. Implement basic ActorSystem and ConnectionActor
2. Bridge Reactor Netty to ConnectionActor messages
3. Add basic supervision and lifecycle management

### Phase 2: XMPP Features
1. Convert handlers to actors (TLS, SASL, Resource Binding)
2. Implement SessionActor for user session management
3. Add message routing between actors

### Phase 3: Advanced Features
1. Implement PresenceActor for presence management
2. Add fault tolerance and error recovery
3. Performance optimization and monitoring

This hybrid approach provides the best of both worlds: Reactor's excellent I/O performance combined with Actor-like isolation and fault tolerance for complex XMPP state management.