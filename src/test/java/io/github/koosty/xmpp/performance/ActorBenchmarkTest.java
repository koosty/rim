package io.github.koosty.xmpp.performance;

import io.github.koosty.xmpp.actor.AbstractActor;
import io.github.koosty.xmpp.actor.message.ActorMessage;
import io.github.koosty.xmpp.actor.message.GenericActorMessage;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH benchmarks for Actor system components
 * Measures performance characteristics of the hybrid architecture
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class ActorBenchmarkTest {

    private TestBenchmarkActor actor;
    private GenericActorMessage testMessage;
    private BlockingQueue<ActorMessage> messageQueue;

    @Setup
    public void setup() {
        actor = new TestBenchmarkActor("benchmark-actor");
        testMessage = new GenericActorMessage("benchmark", "test-sender", Map.of("data", "benchmark-payload"));
        messageQueue = new LinkedBlockingQueue<>();
        
        // Pre-populate queue for queue benchmark
        for (int i = 0; i < 10000; i++) {
            messageQueue.offer(new GenericActorMessage("queue-test-" + i, "sender", Map.of("data", "payload-" + i)));
        }
    }

    @Benchmark
    public ActorMessage benchmarkActorMessageProcessing(Blackhole bh) {
        // Benchmark individual message processing
        ActorMessage result = actor.processMessage(testMessage).block();
        bh.consume(result);
        return result;
    }

    @Benchmark
    public void benchmarkMessageCreation(Blackhole bh) {
        // Benchmark message creation overhead
        GenericActorMessage message = new GenericActorMessage("bench", "sender", Map.of("key", "value"));
        bh.consume(message);
    }

    @Benchmark
    public void benchmarkQueueOperations(Blackhole bh) {
        // Benchmark message queue operations
        ActorMessage message = messageQueue.poll();
        if (message != null) {
            bh.consume(message);
            messageQueue.offer(message); // Put it back
        }
    }

    @Benchmark
    public void benchmarkActorStateAccess(Blackhole bh) {
        // Benchmark actor state access patterns
        int count = actor.getProcessedCount();
        boolean healthy = actor.isHealthy();
        bh.consume(count);
        bh.consume(healthy);
    }

    @Benchmark
    public void benchmarkReactiveChain(Blackhole bh) {
        // Benchmark reactive processing chains
        Mono<ActorMessage> result = actor.processMessage(testMessage)
                .map(msg -> new GenericActorMessage("mapped", "system", Map.of("mapped", true)))
                .flatMap(msg -> actor.processMessage(msg));
        
        StepVerifier.create(result)
                .consumeNextWith(bh::consume)
                .verifyComplete();
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public void benchmarkBatchMessageProcessing(Blackhole bh) {
        // Benchmark batch processing of messages
        for (int i = 0; i < 100; i++) {
            GenericActorMessage message = new GenericActorMessage("batch-" + i, "sender", Map.of("batch", i));
            ActorMessage result = actor.processMessage(message).block();
            bh.consume(result);
        }
    }

    @Benchmark
    public void benchmarkConcurrentActorAccess(Blackhole bh) throws InterruptedException {
        // Benchmark concurrent access patterns
        Thread t1 = new Thread(() -> {
            ActorMessage result = actor.processMessage(testMessage).block();
            bh.consume(result);
        });
        
        Thread t2 = new Thread(() -> {
            int count = actor.getProcessedCount();
            bh.consume(count);
        });
        
        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    @Benchmark
    public void benchmarkMessagePayloadAccess(Blackhole bh) {
        // Benchmark payload access patterns
        GenericActorMessage genericMessage = (GenericActorMessage) testMessage;
        Map<String, Object> payload = genericMessage.payload();
        Object data = payload.get("data");
        bh.consume(data);
    }

    @Benchmark
    public void benchmarkActorHealthCheck(Blackhole bh) {
        // Benchmark health checking overhead
        boolean healthy = actor.isHealthy();
        bh.consume(healthy);
    }

    @Benchmark
    @OperationsPerInvocation(1000)
    public void benchmarkHighVolumeProcessing(Blackhole bh) {
        // Benchmark high-volume message processing
        for (int i = 0; i < 1000; i++) {
            GenericActorMessage message = new GenericActorMessage("volume-" + i, "load-test", 
                Map.of("index", i, "timestamp", System.nanoTime()));
            ActorMessage result = actor.processMessage(message).block();
            bh.consume(result);
        }
    }

    /**
     * Simple actor implementation for benchmarking
     */
    static class TestBenchmarkActor extends AbstractActor {
        private final AtomicInteger processedCount = new AtomicInteger(0);
        
        public TestBenchmarkActor(String actorId) {
            super(actorId);
        }
        
        @Override
        protected Mono<ActorMessage> processMessage(ActorMessage message) {
            return Mono.fromCallable(() -> {
                processedCount.incrementAndGet();
                // Simulate minimal processing
                return message;
            });
        }
        
        public int getProcessedCount() {
            return processedCount.get();
        }
        
        @Override
        public boolean isHealthy() {
            return true; // Always healthy for benchmark
        }
    }

    /**
     * Run benchmarks programmatically for testing
     */
    public static void main(String[] args) throws Exception {
        // This allows running benchmarks from IDE or command line
        org.openjdk.jmh.Main.main(args);
    }
}