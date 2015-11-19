package com.farata.lang.async.core;

import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;

@continuable
abstract public class AsyncTask<V> implements Runnable {
	final protected CompletionStage<V> future; 
	// Just regular CompletableFuture, continuation handling should be added via CompletableFuture.whenComplete 
	// in SuspendableExecutor.await -> applyCondition
	
	protected AsyncTask(final CompletionStage<V> future) {
		this.future = future;
	}
	
	@continuable
	abstract public void run();
	
	final protected static <V> void $result(final V value, final CompletionStage<V> future) {
		future.toCompletableFuture().complete(value);
	}
	
	final protected static <E extends Throwable> void $fault(final E exception, final CompletionStage<?> future) {
		future.toCompletableFuture().completeExceptionally(exception);
	}

}
