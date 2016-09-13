package net.tascalate.async.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import net.tascalate.async.api.Generator;

public class PendingValuesGenerator<T> implements Generator<T> {
    
    final private Object switchConsumerLock = new byte[]{};
    final private BlockingQueue<T> produced = new LinkedBlockingQueue<>();
    final private AtomicInteger remaining = new AtomicInteger(0);
    
    private CompletableFuture<Void> consumerLock = new CompletableFuture<>();
    private Generator<T> current = Generator.empty();
    
    final private BiConsumer<T, Throwable> handler = (result, error) -> {
        if (null != error) {
            remaining.decrementAndGet();
            synchronized (switchConsumerLock) {
                consumerLock.completeExceptionally(error); ///???
            }
        } else {
            try {
                remaining.decrementAndGet();
                produced.put(result);
                synchronized (switchConsumerLock) {
                    consumerLock.complete(null);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e); // Shouldn't happen for queue with unlimited size
            }
        }
    };

    
    private PendingValuesGenerator() {  }

    @Override
    public boolean next(Object producerParam) {
        // If we may return more without switching state...
        if (current.next()) {
            return true;
        }

        final Collection<T> readyValues = new ArrayList<>();
        produced.drainTo(readyValues);
        
        if (!readyValues.isEmpty()) {
            // If we are consuming slower than producing 
            // then use available results right away
            current = Generator.produce(readyValues);
            return current.next();
        } else {
            int remainingPromises = remaining.get();
            if (remainingPromises <= 0) {
                current = Generator.empty();
                return false;
            } else {
                // Otherwise await for any result...
                synchronized (switchConsumerLock) {
                    AsyncExecutor.await(consumerLock);
                    consumerLock = new CompletableFuture<>();
                }
                // ... and try again
                return next(producerParam);
            }
        }
    }

    @Override
    public T current() {
        return current.current();
    }

    @Override
    public void close() {
        try {
            current.close();
        } finally {
            current = Generator.empty();
            remaining.set(0);
            synchronized (switchConsumerLock) {
                consumerLock.completeExceptionally(CloseSignal.INSTANCE);                
            }
        }
    }

    public static <T> Generator<T> create(Stream<CompletionStage<T>> pendingValues) {
        return create(pendingValues.iterator());
    }

    public static <T> Generator<T> create(Iterable<CompletionStage<T>> pendingValues) {
        return create(pendingValues.iterator());
    }
    
    private static <T> Generator<T> create(Iterator<CompletionStage<T>> pendingValues) {
        PendingValuesGenerator<T> result = new PendingValuesGenerator<>();
        while(pendingValues.hasNext()) {
            // +1 before setting completion handler -- 
            // while stage may be completed already
            // we should increment step-by-step 
            // instead of setting the value at once
            result.remaining.incrementAndGet(); 
            pendingValues.next().whenComplete(result.handler);
        };
        
        return result;
    }
}
