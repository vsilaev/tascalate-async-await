package com.farata.lang.async.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CompletionStages {

	@SafeVarargs
	public static <T> CompletionStage<List<T>> all(final CompletionStage<? extends T>... promises) {
		return atLeast(promises.length, 0, true, promises);
	}
	
	@SafeVarargs
	public static <T> CompletionStage<T> any(final CompletionStage<? extends T>... promises) {
		return unwrap(atLeast(1, promises.length - 1, true, promises), false);
	}
	
	@SafeVarargs
	public static <T> CompletionStage<T> anyStrict(final CompletionStage<? extends T>... promises) {
		return unwrap(atLeast(1, 0, true, promises), true);
	}

	@SafeVarargs
	public static <T> CompletionStage<List<T>> atLeast(final int minResultsCount, final CompletionStage<? extends T>... promises) {
		return atLeast(minResultsCount, promises.length - minResultsCount, true, promises);
	}
	
	@SafeVarargs
	public static <T> CompletionStage<List<T>> atLeastStrict(final int minResultsCount, final CompletionStage<? extends T>... promises) {
		return atLeast(minResultsCount, 0, true, promises);
	}
	
	@SafeVarargs
	public static <T> CompletionStage<List<T>> atLeast(final int minResultsCount, final int maxErrorsCount_, final boolean cancelRemaining, final CompletionStage<? extends T>... promises) {
		if (minResultsCount > promises.length) {
			throw new IllegalArgumentException("The number of futures supplied is less than a number of futures to await");
		} else if (minResultsCount == 0) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		} else if (promises.length == 1) {
			final CompletableFuture<List<T>> result = new CompletableFuture<>();
			promises[0].whenComplete((r, e) -> {
				if (null == e) {
					final List<T> value = Collections.singletonList(r);
					result.complete(value);
				} else {
					result.completeExceptionally(
						new MultitargetException(Collections.singletonList(e))
					);
				}
			});
			return result;
		} else {
			final int maxErrorsCount = maxErrorsCount_ < 0 ? 
				promises.length - minResultsCount :
				Math.max(0, Math.min(maxErrorsCount_, promises.length - minResultsCount))
				;
			
			final List<T> results = new ArrayList<>(Collections.nCopies(promises.length, null));
			final List<Throwable> errors = new ArrayList<>(Collections.nCopies(promises.length, null));
			
			final AtomicInteger 
				resultsCount = new AtomicInteger(0), 
				errorsCount = new AtomicInteger(0);
			
			final AtomicBoolean done = new AtomicBoolean(false);
			
			final CompletableFuture<List<T>> result = new CompletableFuture<List<T>>() {

				@Override
				public boolean cancel(boolean mayInterruptIfRunning) {
					if (done.compareAndSet(false, true)) {
						// Disregard isDone for individual promise
						for (int idx = promises.length - 1; idx >= 0; idx--) {
							cancelPromise(promises[idx], mayInterruptIfRunning);
						}
						return super.cancel(mayInterruptIfRunning);
					} else {
						return false;
					}
				}
				
			};

			int i = 0;
			for (final CompletionStage<? extends T> promise : promises) {
				final int idx = i++;
				promise.whenComplete((r, e) -> {
					if (null == e) {
						// ON NEXT RESULT
						final int c = resultsCount.incrementAndGet();
						if (c <= minResultsCount) {
							results.set(idx, r);
							if (c == minResultsCount && done.compareAndSet(false, true)) {
								if (cancelRemaining) {
									cancelPromises(promises, results, errors);
								}
								result.complete(results);
							}
						}
					} else {
						// ON NEXT ERROR
						final int c = errorsCount.getAndIncrement();
						// We are reporting maxErrorsCount + 1 exceptions
						// So if we specify that no exceptions should happen
						// we will report at least one
						if (c <= maxErrorsCount) {
							errors.set(idx, getRealCause(e));
							if (c == maxErrorsCount && done.compareAndSet(false, true)) {
								if (cancelRemaining) {
									cancelPromises(promises, results, errors);
								}
								result.completeExceptionally(
									new MultitargetException(errors)
								);
							}
						}
					}

				});
			}
			return result;
		}
	}
	
	private static void cancelPromises(final CompletionStage<?>[] promises, List<?> results, List<?> errors) {
		for (int idx = promises.length - 1; idx >= 0; idx--) {
			if (results.get(idx) == null && errors.get(idx) == null) {
				cancelPromise(promises[idx], true);
			}
		}
	}
	
	private static boolean cancelPromise(final CompletionStage<?> promise, final boolean mayInterruptIfRunning) {
		if (promise instanceof Future) {
			final Future<?> future = (Future<?>)promise;
			return future.cancel(mayInterruptIfRunning);
		} else {
			return false;
		}
	}
	
	private static <T> CompletionStage<T> unwrap(final CompletionStage<List<T>> original, final boolean unwrapException) {
		final CompletableFuture<T> result = new CompletableFuture<T>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				if (super.cancel(mayInterruptIfRunning)) {
					cancelPromise(original, mayInterruptIfRunning);
					return true;
				} else {
					return false;
				}
			}
		};
		
		original.whenComplete((r, e) -> {
			if (null != e) {
				result.completeExceptionally(
					unwrapException && (e instanceof MultitargetException) ? 
							firstNotNullElement(MultitargetException.class.cast(e).getExceptions()) : e
				);
			} else {
				result.complete(firstNotNullElement(r));
			}
		});
			
		return result;
	}
	
	private static Throwable getRealCause(final Throwable error) {
		final Throwable cause = error instanceof CompletionException ? error.getCause() : null;
		return null == cause ? error : cause;
	}
	
	private static <T> T firstNotNullElement(final Collection<T> collection) {
		return collection.stream().filter(e -> null != e).findAny().get();
	}
}
