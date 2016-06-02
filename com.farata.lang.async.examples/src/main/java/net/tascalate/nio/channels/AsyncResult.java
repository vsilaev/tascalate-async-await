package net.tascalate.nio.channels;

import java.nio.channels.CompletionHandler;

import net.tascalate.concurrent.RestrictedCompletableFuture;

class AsyncResult<V> extends RestrictedCompletableFuture<V> {
    
    final CompletionHandler<V, Object> handler = new CompletionHandler<V, Object>() {
        @Override
        public void completed(final V result, final Object attachment) {
            internalCompleteNormally(result);
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            internalCompleteExceptionally(exc);     
        }
    };

    AsyncResult() {
    }
    
}
