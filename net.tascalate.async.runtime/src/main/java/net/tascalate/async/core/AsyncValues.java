package net.tascalate.async.core;

import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.SequenceIterator;
import net.tascalate.async.AsyncGenerator.Values;

public class AsyncValues<T> implements AsyncGenerator.Values<T> {

    private final AsyncGenerator<T> owner;
    
    public AsyncValues(AsyncGenerator<T> owner) {
        this.owner = owner;
    }

    @Override
    public SequenceIterator<T> iterator() {
        // Optimized version instead of [to-producer].stream().map$(await).iterator()
        // to minimize call stack with suspendable methods
        SequenceIterator<CompletionStage<T>> original = owner.iterator();
        return new SequenceIterator.Closeable<T>() {
            @Override
            public T next() {
                CompletionStage<T> future = original.next();
                return AsyncMethodExecutor.await( future );
            }

            @Override
            public boolean hasNext() {
                return original.hasNext();
            }

            @Override
            public void close() {
                owner.close();
            }

            @Override
            public String toString() {
                return String.format("%s-ValuesIterator[owner=%s]", getClass().getSimpleName(), owner.getClass());
            }            
        };
    }
    
    @Override
    public void close() {
        owner.close();
    }
    
    @Override
    public <D> D as(BiFunction<? super Values<T>, ? super AsyncGenerator<T>, ? extends D> decoratorFactory) {
        return decoratorFactory.apply(this, owner);
    }
}
