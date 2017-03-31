package net.tascalate.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.javacrumbs.completionstage.CompletableCompletionStage;

public class CompletablePromise<T> extends DelegatingCompletionStage<T, CompletableFuture<T>> implements Promise<T> {
    
    public CompletablePromise() {
        this(new CompletableFuture<>());
    }
    
    public CompletablePromise(CompletableFuture<T> delegate) {
        super(delegate);
    }

    protected boolean onSuccess(T value) {
        return completionStage.complete(value);
    }
    
    protected boolean onError(Throwable ex) {
        return completionStage.completeExceptionally(ex);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return completionStage.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return completionStage.isCancelled();
    }

    @Override
    public boolean isDone() {
        return completionStage.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return completionStage.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return completionStage.get(timeout, unit);
    }
    
    static boolean cancelPromise(final CompletionStage<?> promise, final boolean mayInterruptIfRunning) {
        if (promise instanceof Future) {
            final Future<?> future = (Future<?>)promise;
            return future.cancel(mayInterruptIfRunning);
        } else if (promise instanceof CompletableCompletionStage) {
            final CompletableCompletionStage<?> stage = (CompletableCompletionStage<?>)promise;
            return stage.completeExceptionally(new CancellationException());
        } else {
            return false;
        }
    }
    
    static Throwable getRealCause(final Throwable error) {
        final Throwable cause = error instanceof CompletionException ? error.getCause() : null;
        return null == cause ? error : cause;
    }
}
