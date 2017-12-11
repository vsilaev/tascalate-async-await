package net.tascalate.async.api;

import java.util.function.Function;

public interface GeneratorDecorator<T, S extends GeneratorDecorator<T, S>> extends AutoCloseable {
    
    @SuppressWarnings("unchecked")
    default <D extends GeneratorDecorator<T, D>> D as(Function<S, D> decoratorFactory) {
        return decoratorFactory.apply((S)this);
    }

    Generator<T> raw();
    
    @Override
    void close();
}
