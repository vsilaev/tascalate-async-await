package net.tascalate.async.api;


import org.apache.commons.javaflow.api.continuable;
import net.tascalate.concurrent.Promise;

public interface PromisesGenerator<T> extends GeneratorDecorator<T, PromisesGenerator<T>>, AutoCloseable {
    
    @continuable Promise<T> next(Object producerParam);
    
    default
    @continuable Promise<T> next() {
        return next(Generator.NO_PARAM);
    }
    
    void close();
}
