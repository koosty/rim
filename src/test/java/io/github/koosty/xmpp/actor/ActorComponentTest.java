package io.github.koosty.xmpp.actor;

import io.github.koosty.xmpp.actor.message.ActorMessage;
import io.github.koosty.xmpp.actor.message.GenericActorMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for Actor components and message passing
 * Tests the hybrid Reactor-Actor architecture patterns
 */
@DisplayName("Actor Component Tests")
class ActorComponentTest {

    private TestActor actor;
    private ConcurrentLinkedQueue<String> processedMessages;
    private AtomicInteger messageCount;

    @BeforeEach
    void setUp() {
        processedMessages = new ConcurrentLinkedQueue<>();
        messageCount = new AtomicInteger(0);
        actor = new TestActor();
    }

    @Test
    @DisplayName("Actor processes messages sequentially")
    void testSequentialMessageProcessing() {
        GenericActorMessage message1 = new GenericActorMessage("test-message-1", "test-sender", Map.of("data", "test-message-1"));
        GenericActorMessage message2 = new GenericActorMessage("test-message-2", "test-sender", Map.of("data", "test-message-2"));
        GenericActorMessage message3 = new GenericActorMessage("test-message-3", "test-sender", Map.of("data", "test-message-3"));

        Mono<ActorMessage> result = actor.processMessage(message1)
                .then(actor.processMessage(message2))
                .then(actor.processMessage(message3))
                .thenReturn(message3);

        StepVerifier.create(result)
                .expectNext(message3)
                .verifyComplete();

        assertEquals(3, processedMessages.size());
        assertTrue(processedMessages.contains("processed: test-message-1"));
        assertTrue(processedMessages.contains("processed: test-message-2"));
        assertTrue(processedMessages.contains("processed: test-message-3"));
    }

    @Test
    @DisplayName("Actor handles concurrent message processing")
    void testConcurrentMessageHandling() {
        GenericActorMessage message1 = new GenericActorMessage("concurrent-1", "sender1", Map.of("data", "concurrent-1"));
        GenericActorMessage message2 = new GenericActorMessage("concurrent-2", "sender2", Map.of("data", "concurrent-2"));

        Mono<ActorMessage> result1 = actor.processMessage(message1);
        Mono<ActorMessage> result2 = actor.processMessage(message2);

        StepVerifier.create(Mono.zip(result1, result2))
                .assertNext(tuple -> {
                    assertEquals(message1, tuple.getT1());
                    assertEquals(message2, tuple.getT2());
                })
                .verifyComplete();

        assertEquals(2, processedMessages.size());
    }

    @Test
    @DisplayName("Actor maintains message order under load")
    void testMessageOrderingUnderLoad() {
        int messageCount = 100;
        Mono<Void> processingChain = Mono.empty();

        for (int i = 0; i < messageCount; i++) {
            GenericActorMessage message = new GenericActorMessage("order-test-" + i, "load-sender", Map.of("data", "order-test-" + i));
            processingChain = processingChain.then(actor.processMessage(message)).then();
        }

        StepVerifier.create(processingChain)
                .verifyComplete();

        assertEquals(messageCount, processedMessages.size());
        
        // Verify messages were processed in order
        String[] messagesArray = processedMessages.toArray(new String[0]);
        for (int i = 0; i < messageCount; i++) {
            assertEquals("processed: order-test-" + i, messagesArray[i]);
        }
    }

    @Test
    @DisplayName("Actor handles message processing errors gracefully")
    void testErrorHandling() {
        ErrorActor errorActor = new ErrorActor();
        GenericActorMessage validMessage = new GenericActorMessage("valid", "test-sender", Map.of("data", "valid"));
        GenericActorMessage errorMessage = new GenericActorMessage("ERROR", "test-sender", Map.of("data", "ERROR"));

        StepVerifier.create(errorActor.processMessage(validMessage))
                .expectNext(validMessage)
                .verifyComplete();

        StepVerifier.create(errorActor.processMessage(errorMessage))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("Actor responds within expected time limits")
    void testMessageProcessingTimeout() {
        SlowActor slowActor = new SlowActor();
        GenericActorMessage message = new GenericActorMessage("slow-message", "test-sender", Map.of("data", "slow-message"));

        StepVerifier.create(slowActor.processMessage(message))
                .expectNext(message)
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("Actor state remains isolated between message processing")
    void testStateIsolation() {
        StatefulActor statefulActor = new StatefulActor();
        GenericActorMessage message1 = new GenericActorMessage("state-1", "sender1", Map.of("data", "state-1"));
        GenericActorMessage message2 = new GenericActorMessage("state-2", "sender2", Map.of("data", "state-2"));

        StepVerifier.create(statefulActor.processMessage(message1))
                .expectNext(message1)
                .verifyComplete();

        assertEquals(1, statefulActor.getProcessedCount());

        StepVerifier.create(statefulActor.processMessage(message2))
                .expectNext(message2)
                .verifyComplete();

        assertEquals(2, statefulActor.getProcessedCount());
    }

    /**
     * Test actor implementation for testing message processing
     */
    class TestActor extends AbstractActor {
        
        public TestActor() {
            super("test-actor");
        }

        @Override
        protected Mono<ActorMessage> processMessage(ActorMessage message) {
            return Mono.fromRunnable(() -> {
                String messageData = extractMessageData(message);
                processedMessages.offer("processed: " + messageData);
                messageCount.incrementAndGet();
            }).thenReturn(message);
        }
        
        private String extractMessageData(ActorMessage message) {
            if (message instanceof GenericActorMessage genericMessage) {
                Object data = genericMessage.payload().get("data");
                return data != null ? data.toString() : "unknown";
            }
            return "unknown";
        }
    }

    /**
     * Actor that throws errors for specific messages
     */
    class ErrorActor extends AbstractActor {
        
        public ErrorActor() {
            super("error-actor");
        }

        @Override
        protected Mono<ActorMessage> processMessage(ActorMessage message) {
            if (message instanceof GenericActorMessage genericMessage) {
                Object data = genericMessage.payload().get("data");
                if ("ERROR".equals(data)) {
                    return Mono.error(new RuntimeException("Test error"));
                }
            }
            return Mono.just(message);
        }
    }

    /**
     * Actor that takes time to process messages
     */
    class SlowActor extends AbstractActor {
        
        public SlowActor() {
            super("slow-actor");
        }

        @Override
        protected Mono<ActorMessage> processMessage(ActorMessage message) {
            return Mono.delay(Duration.ofMillis(100))
                    .thenReturn(message);
        }
    }

    /**
     * Actor that maintains internal state
     */
    class StatefulActor extends AbstractActor {
        private final AtomicInteger processedCount = new AtomicInteger(0);
        
        public StatefulActor() {
            super("stateful-actor");
        }

        @Override
        protected Mono<ActorMessage> processMessage(ActorMessage message) {
            return Mono.fromRunnable(() -> processedCount.incrementAndGet())
                    .thenReturn(message);
        }

        public int getProcessedCount() {
            return processedCount.get();
        }
    }
}