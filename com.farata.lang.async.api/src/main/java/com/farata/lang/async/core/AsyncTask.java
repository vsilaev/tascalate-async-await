package com.farata.lang.async.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;

abstract public class AsyncTask<V> implements Runnable {
	final protected CompletionStage<V> future; 
	// Just regular CompletableFuture, continuation handling should be added via CompletableFuture.whenComplete 
	// in SuspendableExecutor.await -> applyCondition
	
	protected AsyncTask(final CompletionStage<V> future) {
		this.future = future;
	}
	
	abstract public @continuable void run();
	
	final protected static <V> CompletionStage<V> $$result$$(final V value, final CompletionStage<V> future) {
		future.toCompletableFuture().complete(value);
		return future;
	}
	
	final protected static <V, E extends Throwable> CompletionStage<V> $$fault$$(final E exception, final CompletionStage<V> future) {
		future.toCompletableFuture().completeExceptionally(exception);
		return future;
	}
	
	final public static <V> CompletionStage<V> createFuture() {
		return new CompletableFuture<V>();
	}

}
