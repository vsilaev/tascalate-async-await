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
    final private BlockingQueue<Either<T, Throwable>> produced = new LinkedBlockingQueue<>();
    final private AtomicInteger remaining = new AtomicInteger(0);
    
    private CompletableFuture<Void> consumerLock = new CompletableFuture<>();
    private Generator<T> current = Generator.empty();
    
    final private BiConsumer<T, Throwable> handler = (result, error) -> {
        try {
            produced.put(null != error ? Either.error(error) : Either.result(result));
        } catch (InterruptedException e) {
            throw new RuntimeException(e); // Shouldn't happen for queue with unlimited size
        }

        remaining.decrementAndGet();
        synchronized (switchConsumerLock) {
            consumerLock.complete(null);
        }
    };
    
    private PendingValuesGenerator() {  }

    @Override
    public boolean next(Object producerParam) {
        // If we may return more without switching state...
        if (current.next()) {
            return true;
        }

        int unprocessed = remaining.get();
        if (unprocessed < 0) {
            // Forcibly closed
            return false;
        } else {
            final Collection<Either<T, Throwable>> readyValues = new ArrayList<>();
            produced.drainTo(readyValues);

            if (!readyValues.isEmpty()) {
                // If we are consuming slower than producing 
                // then use available results right away
                current = Generator.of(readyValues.stream().map(Either::doneUnchecked));
                return next(producerParam);
            } else {
                // Otherwise await for any result...            
                if (unprocessed > 0) {
                    synchronized (switchConsumerLock) {
                        AsyncMethodExecutor.await(consumerLock);
                        consumerLock = new CompletableFuture<>();
                    }
                    // ... and try again
                    return next(producerParam);
                } else {
                    //...or stop if no more results...
                    current = Generator.empty();
                    return false;
                }
            }
        }
        
    }

    @Override
    public T current() {
        return current.current();
    }

    @Override
    public void close() {
        remaining.set(-1);
        current.close();
        current = Generator.empty();
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
