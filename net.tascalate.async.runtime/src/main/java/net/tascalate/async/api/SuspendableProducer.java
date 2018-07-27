package net.tascalate.async.api;

public interface SuspendableProducer<T> extends AutoCloseable {
    @suspendable T produce(Object param);
    void close();
}
