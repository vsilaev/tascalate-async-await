package net.tascalate.async.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

abstract public class AsyncTask<V> extends AsyncMethodBody {
    final public CompletionStage<V> future;
    // Just regular CompletableFuture, continuation handling should be added via
    // CompletableFuture.whenComplete in AsyncExecutor.await -> setupContinuation

    protected AsyncTask() {
        this.future = createFuture();
    }

    final protected static <V> CompletionStage<V> $$result$$(final V value, final AsyncTask<V> self) {
        final CompletionStage<V> future = self.future;
        future.toCompletableFuture().complete(value);
        return future;
    }

    final protected CompletionStage<V> $$fault$$(final Throwable exception) {
        future.toCompletableFuture().completeExceptionally(exception);
        return future;
    }

    final private static <V> CompletionStage<V> createFuture() {
        return new CompletableFuture<V>();
    }

}
