package net.tascalate.async.api;

import org.apache.commons.javaflow.api.continuable;

public interface ValuesGenerator<T> extends AutoCloseable {
    @continuable T next();
    @continuable boolean hasNext();
    void close();
}
