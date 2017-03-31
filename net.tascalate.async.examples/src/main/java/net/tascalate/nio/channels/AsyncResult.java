package net.tascalate.nio.channels;

import java.nio.channels.CompletionHandler;

import net.tascalate.concurrent.CompletablePromise;

class AsyncResult<V> extends CompletablePromise<V> {
    
    final CompletionHandler<V, Object> handler = new CompletionHandler<V, Object>() {
        @Override
        public void completed(final V result, final Object attachment) {
            onSuccess(result);
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            onError(exc);     
        }
    };

    AsyncResult() {
    }
    
}
