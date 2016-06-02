package net.tascalate.concurrent;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class CompletionStages {

	@SafeVarargs
	public static <T> CompletionFuture<List<T>> all(final CompletionStage<? extends T>... promises) {
		return atLeast(promises.length, 0, true, promises);
	}
	
	@SafeVarargs
	public static <T> CompletionFuture<T> any(final CompletionStage<? extends T>... promises) {
		return unwrap(atLeast(1, promises.length - 1, true, promises), false);
	}
	
	@SafeVarargs
	public static <T> CompletionFuture<T> anyStrict(final CompletionStage<? extends T>... promises) {
		return unwrap(atLeast(1, 0, true, promises), true);
	}

	@SafeVarargs
	public static <T> CompletionFuture<List<T>> atLeast(final int minResultsCount, final CompletionStage<? extends T>... promises) {
		return atLeast(minResultsCount, promises.length - minResultsCount, true, promises);
	}
	
	@SafeVarargs
	public static <T> CompletionFuture<List<T>> atLeastStrict(final int minResultsCount, final CompletionStage<? extends T>... promises) {
		return atLeast(minResultsCount, 0, true, promises);
	}
	
	@SafeVarargs
	public static <T> CompletionFuture<List<T>> atLeast(final int minResultsCount, final int maxErrorsCount, final boolean cancelRemaining, final CompletionStage<? extends T>... promises) {
		if (minResultsCount > promises.length) {
			throw new IllegalArgumentException("The number of futures supplied is less than a number of futures to await");
		} else if (minResultsCount == 0) {
			return new RestrictedCompletableFuture<List<T>>(){{
			    internalCompleteNormally(Collections.emptyList());  
			}};
		} else if (promises.length == 1) {
	        return new CompletionFutureConverter<>(
	                promises[0], 
	                Collections::singletonList,
	                function(MultitargetException::of)
	                .compose(CompletableFutureWrapper::getRealCause)
	        );
		} else {
			return new CombinedCompletableFuture<>(minResultsCount, maxErrorsCount, cancelRemaining, promises);
		}
	}
	
	private static <T> CompletionFuture<T> unwrap(final CompletionStage<List<T>> original, final boolean unwrapException) {
	    if (unwrapException) {
	        return new CompletionFutureConverter<>(
	                original, 
	                CompletionStages::firstNotNullElement,
	                e -> e instanceof MultitargetException ? 
	                     firstNotNullElement(((MultitargetException)e).getExceptions()) : e
	        );
	    } else {
	        return new CompletionFutureConverter<>(original, CompletionStages::firstNotNullElement);
	    }
	}

    private static <T> T firstNotNullElement(final Collection<T> collection) {
        return collection.stream().filter(e -> null != e).findAny().get();
    }
    
    private static <T, R> Function<T, R> function(Function<T, R> fn) { return fn; }
    
    static class CompletionFutureConverter<T, U> extends CompletableFutureWrapper<T, U> {

        CompletionFutureConverter(CompletionStage<? extends U> delegate, Function<? super U, ? extends T> onResult) {
            this(delegate, onResult, Function.identity());
        }
        
        CompletionFutureConverter(CompletionStage<? extends U> delegate, Function<? super U, ? extends T> onResult, Function<? super Throwable, ? extends Throwable> onError) {
            super(delegate);
            delegate.whenComplete((r, e) -> {
                if (null != e) {
                    internalCompleteExceptionally(onError.apply(e));
                } else {
                    internalCompleteNormally(onResult.apply(r));
                }
            });
        }
    }
}
