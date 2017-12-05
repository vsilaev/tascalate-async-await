package net.tascalate.async.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import net.tascalate.concurrent.CompletablePromise;
import net.tascalate.concurrent.Promise;

class ResultPromise<T> extends CompletablePromise<T> {
    ResultPromise() {}
    
    ResultPromise(CompletableFuture<T> delegate) {
        super(delegate);
    }

    @Override
    protected <U> Promise<U> wrap(CompletionStage<U> original) {
        return new ResultPromise<>((CompletableFuture<U>)original);
    }
    
    void internalCompleWithResult(T result) {
        onSuccess(result);
    }
    
    void internalCompleWithException(Throwable exception) {
        onFailure(exception);
    }

}
