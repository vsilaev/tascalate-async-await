package net.tascalate.async.mutiny;

import org.reactivestreams.Subscription;

import net.tascalate.async.AsyncGenerator.Sink;

class MutinyLegacyMultiSubscriber<T> extends PortableMultiSubscriber<T> {

    MutinyLegacyMultiSubscriber(Sink<T> sink, long batchSize) {
        super(sink, batchSize);
    }

    @Override
    public void onSubscribe(Subscription s) {
        onSubscribe((Object)s);
    }
    
}
