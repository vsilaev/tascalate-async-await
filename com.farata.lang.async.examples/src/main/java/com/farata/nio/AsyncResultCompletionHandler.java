package com.farata.nio;

import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;

public class AsyncResultCompletionHandler<V, A> implements CompletionHandler<V, A> {
	
	volatile public static int ARTIFICIAL_DELAY_BEFORE_COMPLETION = 0;
	
	final private CompletableFuture<V> asyncResult;
	final private CompletionHandler<V, ? super A> clientCallback;
	
	public AsyncResultCompletionHandler(final CompletableFuture<V> asyncResult, final CompletionHandler<V, ? super A> clientCallback) {
		this.asyncResult = asyncResult;
		this.clientCallback = clientCallback;
	}

	@Override
	public void completed(final V result, final A attachment) {
		delayResult();
		if (null != clientCallback) {
			clientCallback.completed(result, attachment);
		}
		asyncResult.complete(result);
	}

	@Override
	public void failed(Throwable exc, A attachment) {
		delayResult();
		if (null != clientCallback) {
			clientCallback.failed(exc, attachment);
		}
		asyncResult.completeExceptionally(exc);		
	}
	
	private static void delayResult() {
		if (ARTIFICIAL_DELAY_BEFORE_COMPLETION > 0) {
			
			try {
				Thread.sleep(ARTIFICIAL_DELAY_BEFORE_COMPLETION);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
}