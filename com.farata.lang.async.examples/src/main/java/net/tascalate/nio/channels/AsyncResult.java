package net.tascalate.nio.channels;

import java.nio.channels.CompletionHandler;

import net.tascalate.concurrent.RestrictedCompletableFuture;

class AsyncResult<V, A> extends RestrictedCompletableFuture<V> {
    
    private final CompletionHandler<V, A> clientCallback;
    
    final CompletionHandler<V, A> handler = new CompletionHandler<V, A>() {
        @Override
        public void completed(final V result, final A attachment) {
            if (null != clientCallback) {
                clientCallback.completed(result, attachment);
            }
            internalCompleteNormally(result);
        }

        @Override
        public void failed(Throwable exc, A attachment) {
            if (null != clientCallback) {
                clientCallback.failed(exc, attachment);
            }
            internalCompleteExceptionally(exc);     
        }
    };

    AsyncResult(CompletionHandler<V, A> clientCallback ) {
        this.clientCallback = clientCallback;
    }
    
}
