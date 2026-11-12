package net.tascalate.async.mutiny;

import java.util.concurrent.Flow.Subscription;

//${mutiny.version}
import net.tascalate.async.AsyncGenerator.Sink;

class MutinyModernMultiSubscriber<T> extends PortableMultiSubscriber<T> {

    MutinyModernMultiSubscriber(Sink<T> sink, long batchSize) {
        super(sink, batchSize);
    }

    @Override
    public void onSubscribe(Subscription s) {
        onSubscribe((Object)s);
    }

}
