package net.tascalate.async.api;

import org.apache.commons.javaflow.api.continuable;

public interface Generator<T> extends AutoCloseable {
    public @continuable boolean next(Object producerParam);
    public @continuable boolean next();
    public T current();
    public void close();
}
