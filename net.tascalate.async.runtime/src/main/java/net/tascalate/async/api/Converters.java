package net.tascalate.async.api;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.apache.commons.javaflow.extras.ContinuableFunction;

import net.tascalate.async.core.AsyncMethodExecutor;

public final class Converters {
    private Converters() {}
    
    public static <T> ContinuableFunction<CompletionStage<T>, T> readyValues() {
        return new ContinuableFunction<CompletionStage<T>, T>() {
            @Override
            public T apply(CompletionStage<T> future) {
                return AsyncMethodExecutor.await(future);
            }
        };
    }
    
    public static <T> Function<SuspendableStream.Producer<T>, SuspendableIterator<T>> iterator() {
        return SuspendableIteratorImpl::new;
    }
    
    public static <T> Function<SuspendableStream.Producer<? extends CompletionStage<T>>, Generator<T>> generator() {
        class SuspendableStreamProducerAdapter implements Generator<T> {
            private final SuspendableStream.Producer<? extends CompletionStage<T>> producer;
            
            SuspendableStreamProducerAdapter(SuspendableStream.Producer<? extends CompletionStage<T>> producer) {
                this.producer = producer;
            }
            
            @Override
            public CompletionStage<T> next(Object producerParam) {
                if (producerParam != null) {
                    throw new UnsupportedOperationException("Converted generators do not support parameters");
                }
                return producer.produce();
            }

            @Override
            public void close() {
                producer.close();
            }
            
        };
        return SuspendableStreamProducerAdapter::new;
    }

}
