package net.tascalate.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.javacrumbs.completionstage.CompletableCompletionStage;
import net.javacrumbs.completionstage.spi.CompletableCompletionStageFactory;

class CompletableTask<V> extends DelegatingCompletionStage<V, CompletableCompletionStage<V>> implements Promise<V>, RunnableFuture<V> {
    private final RunnableFuture<V> internalTask;

    CompletableTask(final CompletableCompletionStageFactory factory, Callable<V> callable) {
        super(factory.createCompletionStage());
        this.internalTask = new InternalTask(callable);
    }
    
    @Override
    public void run() {
        internalTask.run();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return internalTask.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return internalTask.isCancelled();
    }

    @Override
    public boolean isDone() {
        return internalTask.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return internalTask.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return internalTask.get(timeout, unit);
    }
    
    boolean onSuccess(V result) {
        return completionStage.complete(result);
    }
    
    boolean onError(Throwable ex) {
        return completionStage.completeExceptionally(ex);
    };

    private class InternalTask extends FutureTask<V> {

        InternalTask(Callable<V> callable) {
            super(callable);
        }

        @Override
        protected void set(V v) {
            super.set(v);
            onSuccess(v);
        };
        
        @Override
        protected void setException(Throwable t) {
            super.setException(t);
            onError(t);
        };
        
        @Override 
        protected void done() {
            if (isCancelled()) {
                onError(new CancellationException());
            }
        }
    }
}

