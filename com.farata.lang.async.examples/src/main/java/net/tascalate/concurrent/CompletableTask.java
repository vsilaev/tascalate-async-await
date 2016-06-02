package net.tascalate.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.javacrumbs.completionstage.CompletionStageFactory;
import net.javacrumbs.completionstage.SimpleCompletionStage;

class CompletableTask<V> extends SimpleCompletionStage<V> implements CompletionFuture<V>, RunnableFuture<V> {
    private final RunnableFuture<V> delegate;

    static <V> CompletableTask<V> create(Executor executor, Callable<V> callable) {
        return new CompletableTask<>(executor, callable);
    }
    
    static <V> CompletableTask<V> create(Executor executor, Runnable runnable, V value) {
        return create(executor, Executors.callable(runnable, value));
    }
    
    protected CompletableTask(final Executor executor, Callable<V> callable) {
        super(executor, new CompletionStageFactory(executor));
        this.delegate = new FutureTaskWithCompletion(callable);
    }
    
    @Override
    public void run() {
        delegate.run();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return delegate.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return delegate.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.get(timeout, unit);
    }

    @Override
    public boolean complete(V result) {
        throw new UnsupportedOperationException();
    }
    
    @Override 
    public boolean completeExceptionally(Throwable ex) {
        throw new UnsupportedOperationException();
    };
    
    @Override
    public void doComplete(V result, Throwable throwable) {
        throw new UnsupportedOperationException();
    }
    
    boolean internalComplete(V result) {
        return super.complete(result);
    }
    
    boolean internalCompleteExceptionally(Throwable ex) {
        return super.completeExceptionally(ex);
    };

    private class FutureTaskWithCompletion extends FutureTask<V> {

        FutureTaskWithCompletion(Callable<V> callable) {
            super(callable);
        }

        @Override
        protected void set(V v) {
            super.set(v);
            internalComplete(v);
        };
        
        @Override
        protected void setException(Throwable t) {
            super.setException(t);
            internalCompleteExceptionally(t);
        };
        
        @Override 
        protected void done() {
            if (isCancelled()) {
                internalCompleteExceptionally(new CancellationException());
            }
        }
    }
}

