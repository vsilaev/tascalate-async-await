package net.tascalate.async.core;

import java.util.concurrent.CompletionStage;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.javaflow.SuspendableIterator;
import net.tascalate.javaflow.SuspendableStream;

public class AsyncValues<T> implements AsyncGenerator.Values<T> {

    private final AsyncGenerator<T> owner;
    
    public AsyncValues(AsyncGenerator<T> owner) {
        this.owner = owner;
    }
    
    @Override
    public SuspendableStream<T> stream() {
        return owner.stream().map$(AsyncGenerator.awaitValue());
    }

    @Override
    public SuspendableIterator<T> iterator() {
        // Optimized version instead of [to-producer].stream().map$(await).iterator()
        // to minimize call stack with suspendable methods
        SuspendableIterator<CompletionStage<T>> original = owner.iterator();
        return new SuspendableIterator<T>() {
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
                original.close();
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
}
