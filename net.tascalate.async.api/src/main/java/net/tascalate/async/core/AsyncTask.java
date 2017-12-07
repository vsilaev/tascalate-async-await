package net.tascalate.async.core;

import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.concurrent.Promise;

abstract public class AsyncTask<V> extends AsyncMethodBody {
    public final Promise<V> future;

    protected AsyncTask(ContextualExecutor contextualExecutor) {
        super(contextualExecutor);
        this.future = new ResultPromise<>();
    }
    
    @Override
    public final @continuable void run() {
        try {
            doRun();
            // ensure that promise is resolved
            $$result$$(null, this);
        } catch (Throwable ex) {
            ResultPromise<V> future = (ResultPromise<V>)this.future;
            future.internalCompleWithException(ex);
        }
    }
    
    abstract protected @continuable void doRun() throws Throwable;

    protected static <V> CompletionStage<V> $$result$$(final V value, final AsyncTask<V> self) {
        ResultPromise<V> future = (ResultPromise<V>)self.future;
        future.internalCompleWithResult(value);
        return future;
    }
    
    protected @continuable static <T, V> T $$await$$(final CompletionStage<T> future, final AsyncTask<V> self) {
        return AsyncExecutor.await(future);
    }
}
