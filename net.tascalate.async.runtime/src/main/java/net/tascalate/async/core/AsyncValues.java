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
        return iterator(true, null);
    }
    
    @Override
    public SequenceIterator<T> iterator(boolean exclusive) {
        return iterator(exclusive, null);
    }

    protected SequenceIterator<T> iterator(boolean exclusive, AbstractAsyncMethod caller) {
        SequenceIterator<CompletionStage<T>> original = owner.iterator(exclusive);
        return new SequenceIterator.Closeable<T>() {
            
            AbstractAsyncMethod exactCaller = exclusive ? caller : null;
            
            @Override
            public T next() {
                CompletionStage<T> future = original.next();
                if (exactCaller == null && exclusive) {
                    exactCaller = InternalCallContext.asyncMethod();
                }
                return AsyncMethodExecutor.await( future, exactCaller );
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
