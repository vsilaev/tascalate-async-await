package net.tascalate.concurrent;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class Promises {
    
    public static <T> Promise<T> readyValue(T value) {
        final CompletablePromise<T> result = new CompletablePromise<>();
        result.onSuccess(value);
        return result;
    }    
    
    public static <T> Promise<T> from(CompletionStage<T> stage) {
        return from(stage, Function.identity(), Function.identity());
    }
    
    public static <T, R> Promise<R> from(CompletionStage<T> stage, Function<? super T, ? extends R> resultConverter, Function<? super Throwable, ? extends Throwable> errorConverter) {
        final CompletablePromise<R> result = new CompletablePromise<R>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (super.cancel(mayInterruptIfRunning)) {
                    cancelPromise(stage, mayInterruptIfRunning);
                    return true;
                } else {
                    return false;
                }
            }
        };
        stage.whenComplete(handler(
            acceptConverted(result::onSuccess, resultConverter), 
            acceptConverted(result::onError, errorConverter)
        ));
        return result;
    }
    
	@SafeVarargs
	public static <T> Promise<List<T>> all(final CompletionStage<? extends T>... promises) {
		return atLeast(promises.length, 0, true, promises);
	}
	
	@SafeVarargs
	public static <T> Promise<T> any(final CompletionStage<? extends T>... promises) {
		return unwrap(atLeast(1, promises.length - 1, true, promises), false);
	}
	
	@SafeVarargs
	public static <T> Promise<T> anyStrict(final CompletionStage<? extends T>... promises) {
		return unwrap(atLeast(1, 0, true, promises), true);
	}

	@SafeVarargs
	public static <T> Promise<List<T>> atLeast(final int minResultsCount, final CompletionStage<? extends T>... promises) {
		return atLeast(minResultsCount, promises.length - minResultsCount, true, promises);
	}
	
	@SafeVarargs
	public static <T> Promise<List<T>> atLeastStrict(final int minResultsCount, final CompletionStage<? extends T>... promises) {
		return atLeast(minResultsCount, 0, true, promises);
	}
	
	@SafeVarargs
	public static <T> Promise<List<T>> atLeast(final int minResultsCount, final int maxErrorsCount, final boolean cancelRemaining, final CompletionStage<? extends T>... promises) {
		if (minResultsCount > promises.length) {
			throw new IllegalArgumentException("The number of futures supplied is less than a number of futures to await");
		} else if (minResultsCount == 0) {
			return readyValue(Collections.emptyList());  
		} else if (promises.length == 1) {
	        return from(promises[0], Collections::singletonList, Function.<Throwable>identity()
	                                                             .andThen(CompletablePromise::getRealCause)
	                                                             .andThen(MultitargetException::of));

		} else {
			return new AggregatingPromise<>(minResultsCount, maxErrorsCount, cancelRemaining, promises);
		}
	}
	
	private static <T> Promise<T> unwrap(final CompletionStage<List<T>> original, final boolean unwrapException) {
	    if (unwrapException) {
	        return from(
	            original, 
	            Promises::firstNotNullElement,
	            e -> e instanceof MultitargetException ? 
	                 firstNotNullElement(((MultitargetException)e).getExceptions()) : e
	        );
	    } else {
	        return from(original.thenApply(Promises::firstNotNullElement));
	    }
	}

    private static <T> T firstNotNullElement(final Collection<T> collection) {
        return collection.stream().filter(e -> null != e).findAny().get();
    }
    
    private static <T> BiConsumer<T, ? super Throwable> handler(Consumer<? super T> onResult, Consumer<? super Throwable> onError) {
        return (r, e) -> {
            if (null != e) {
                onError.accept(e);
            } else {
                try {
                    onResult.accept(r);
                } catch (final Exception ex) {
                    onError.accept(ex);
                }
            }
        };
    }

    
    private static <T, U> Consumer<? super T> acceptConverted(Consumer<? super U> target, Function<? super T, ? extends U> converter) {
        return t -> target.accept(converter.apply(t));
    }
    
}
