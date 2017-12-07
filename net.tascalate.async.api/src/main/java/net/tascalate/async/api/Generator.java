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

    public static <T> Generator<T> of(T readyValue) {
    	return of(Stream.of(readyValue));
    }
    
    @SafeVarargs
    public static <T> Generator<T> of(T... readyValues) {
        return of(Stream.of(readyValues));
    }
    
    public static <T> Generator<T> of(Stream<? extends T> readyValues) {
        return new ReadyValuesGenerator<>(readyValues);
    }
    
    public static <T> Generator<T> of(Iterable<? extends T> readyValues) {
        return new ReadyValuesGenerator<>(readyValues);
    }

    public static <T> Generator<T> of(CompletionStage<T> pendingValue) {
    	return ofUnordered(Stream.of(pendingValue));
    }
    
    @SafeVarargs
    public static <T> Generator<T> ofUnordered(CompletionStage<T>... pendingValues) {
        return ofUnordered(Stream.of(pendingValues));
    }

    public static <T> Generator<T> ofUnordered(Stream<CompletionStage<T>> pendingValues) {
        return PendingValuesGenerator.create(pendingValues);
    }
    
    public static <T> Generator<T> ofUnordered(Iterable<CompletionStage<T>> pendingValues) {
        return PendingValuesGenerator.create(pendingValues);
    }
}
