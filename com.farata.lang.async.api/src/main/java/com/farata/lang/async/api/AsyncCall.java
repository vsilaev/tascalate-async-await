package com.farata.lang.async.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.javaflow.api.continuable;

import com.farata.lang.async.core.AsyncExecutor;
import com.farata.lang.async.core.NoActiveAsyncCallException;


/**
 * @author Valery Silaev
 * 
 */
@continuable
public class AsyncCall {

	@SafeVarargs
	@continuable
	public static <E extends Throwable> List<Object> await(CompletionStage<?>... conditions) throws NoActiveAsyncCallException, E {
		return await(atLeast(conditions.length, conditions));
	}
	
	@SafeVarargs
	@continuable
	public static <E extends Throwable> List<Object> await(final int minCount, final CompletionStage<?>... conditions) throws NoActiveAsyncCallException, E {
		return await(atLeast(minCount, conditions));
	}
	
	/**
	 * Wait for the {@link CompletionStage} within {@link async} method.
	 * 
	 * The {@link async} method will be suspended until {@link CompletionStage} returns or throws the result. 
	 */
	@continuable
	public static <T, E extends Throwable> T await(final CompletionStage<T> condition) throws NoActiveAsyncCallException, E {
		return AsyncExecutor.await(condition);
	}
	
	@continuable
	public static <T, E extends Throwable> T await(final CompletionStage<T> condition, final Class<E> expectedException) throws NoActiveAsyncCallException, E {
		return await(condition);
	}

	final public static <T> CompletableFuture<T> asyncResult(final T value) {
		throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
	}
	
	@SafeVarargs
	private static CompletionStage<List<Object>> atLeast(final int n, final CompletionStage<?>... conditions) {
		if (n > conditions.length) {
			throw new IllegalArgumentException("The number of futures supplied is less than a number of futures to await");
		} else if (n == 0) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		} else if (conditions.length == 1) {
			final CompletableFuture<List<Object>> result = new CompletableFuture<>();
			conditions[0].whenComplete((r, e) -> {
				if (null == e) {
					result.complete(Collections.singletonList(r));
				} else {
					result.completeExceptionally(e);
				}
			});
			return result;
		} else {
			final List<Object> values = new ArrayList<>(Collections.nCopies(conditions.length, null));
			final List<Throwable> errors = new ArrayList<>(Collections.nCopies(conditions.length, null));
			
			int[] valuesCount = {0}, errorsCount = {0};
			
			final CountDownLatch latch = new CountDownLatch(n);
			int i = 0;
			
			for (final CompletionStage<?> future : conditions) {
				final int idx = i++;
				future.whenComplete((r, e) -> {
					if (null == e) {
						errors.set(idx, e);
						errorsCount[0]++;
					} else {
						values.set(idx, r);
						valuesCount[0]++;
					}
					latch.countDown();
				});
			}
			return CompletableFuture.supplyAsync(() -> {
				try {
					latch.await();
				} catch (final InterruptedException ex) {
					throw new CancellationException(ex.getMessage());
				}
				if (valuesCount[0] < n) {
					throw new CombinedCompletionException(errors);
				}
				return values;
			});
		}
	}
}
