package com.farata.concurrent;

import java.util.concurrent.CompletableFuture;

public class RestrictedCompletableFuture<T> extends CompletableFuture<T> implements CompletionFuture<T> {
    
    @Override
    final public boolean complete(T value) {
        throw new UnsupportedOperationException();
    }

    @Override
    final public boolean completeExceptionally(Throwable ex) {
        throw new UnsupportedOperationException();
    }

    
    protected boolean internalCompleteNormally(T value) {
        return super.complete(value);
    }
    
    protected boolean internalCompleteExceptionally(Throwable ex) {
        return super.completeExceptionally(ex);
    }
}
