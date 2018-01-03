package net.tascalate.async.generator;

import java.util.concurrent.CompletionStage;

import net.tascalate.async.api.Generator;
import net.tascalate.async.api.PromisesGenerator;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;

class PromisesGeneratorImpl <T> implements PromisesGenerator<T> {
    
    private final Generator<T> delegate;
    
    PromisesGeneratorImpl(Generator<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Generator<T> raw() {
        return delegate;
    }

    @Override
    public Promise<T> next(Object producerParam) {
        CompletionStage<T> original = delegate.next(producerParam);
        return null == original ? null : Promises.from(original);
    }

    @Override
    public void close() {
        delegate.close();
    }

    
}
