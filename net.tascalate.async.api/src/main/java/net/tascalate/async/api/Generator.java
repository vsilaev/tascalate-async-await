package net.tascalate.async.api;

import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.core.PendingValuesGenerator;
import net.tascalate.async.core.ReadyValuesGenerator;

public interface Generator<T> extends AutoCloseable {
    
    public @continuable boolean next(Object producerParam);
    
    default 
    public @continuable boolean next() {
        return next(null);
    }
    
    public T current();
    
    public void close();
    
    @SuppressWarnings("unchecked")
    public static <T> Generator<T> empty() {
        return (Generator<T>)ReadyValuesGenerator.EMPTY;
    }

    @SafeVarargs
    public static <T> Generator<T> produce(T... readyValues) {
        return produce(Stream.of(readyValues));
    }
    
    public static <T> Generator<T> produce(Stream<? extends T> readyValues) {
        return new ReadyValuesGenerator<>(readyValues);
    }
    
    public static <T> Generator<T> produce(Iterable<? extends T> readyValues) {
        return new ReadyValuesGenerator<>(readyValues);
    }

    @SafeVarargs
    public static <T> Generator<T> await(CompletionStage<T>... pendingValues) {
        return await(Stream.of(pendingValues));
    }

    public static <T> Generator<T> await(Stream<CompletionStage<T>> pendingValues) {
        return PendingValuesGenerator.create(pendingValues);
    }
    
    public static <T> Generator<T> await(Iterable<CompletionStage<T>> pendingValues) {
        return PendingValuesGenerator.create(pendingValues);
    }
}
