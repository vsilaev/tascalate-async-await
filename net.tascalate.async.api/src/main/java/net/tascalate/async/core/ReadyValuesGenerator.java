package net.tascalate.async.core;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.api.Generator;
import net.tascalate.async.api.GeneratorDecorator;
import net.tascalate.async.api.ValuesGenerator;

public class ReadyValuesGenerator<T> implements GeneratorDecorator<T, ReadyValuesGenerator<T>>, ValuesGenerator<T> {
    private final Generator<T> delegate;
    private boolean advance;
    private CompletionStage<T> current;
    
    public ReadyValuesGenerator(Generator<T> delegate) {
        this.delegate = delegate;
        advance = true;
    }
    
    public Generator<T> raw() {
        return delegate.raw();
    }
    
    public @continuable boolean hasNext() {
        advanceIfNecessary();
        return current != null;
    }

    public @continuable T next() {
        advanceIfNecessary();

        if (current == null)
            throw new NoSuchElementException();

        final T result = AsyncMethodExecutor.await(current);
        advance = true;

        return result;
    }

    public void close() {
        current = null;
        advance = false;
        delegate.close();
    }
    
    protected @continuable void advanceIfNecessary() {
        if (advance)
            current = delegate.next();
        advance = false;
    }
    
}
