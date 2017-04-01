package net.tascalate.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

abstract class BlockingCompletionStage<T> extends CompletionStageAdapter<T> implements Promise<T> {
	
	private final CallbackRegistry<T> callbackRegistry = new CallbackRegistry<>();
	protected final RunnableFuture<T> task;
	protected final Callable<T> action;

	protected BlockingCompletionStage(Executor defaultExecutor, Callable<T> action) {
		super(defaultExecutor);
		this.action = action;
		this.task = new StageTransition(action);
	}
	
	
	abstract Runnable setupTransition(Callable<T> code);
	
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (task.cancel(mayInterruptIfRunning)) {
        	onError( new CancellationException());
        	return true;
        } else {
        	return false;
        }
    }

    @Override
    public boolean isCancelled() {
        return task.isCancelled();
    }

    @Override
    public boolean isDone() {
        return task.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return task.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return task.get(timeout, unit);
    }
    
    boolean onSuccess(T result) {
    	return callbackRegistry.success(result);    	
    }
    
    boolean onError(Throwable ex) {
    	return callbackRegistry.failure(ex);    	
    }

    class StageTransition extends FutureTask<T> {
    	StageTransition(Callable<T> callable) {
            super(callable);
        }

        @Override
        protected void set(T v) {
            super.set(v);
            onSuccess(v);
        };
        
        @Override
        protected void setException(Throwable t) {
            super.setException(t);
            onError(t);
        };
        
        @Override 
        protected void done() {
            if (isCancelled()) {
                //;
            }
        }
    }
    
    @Override
    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
    	BlockingCompletionStage<U> nextStage = internalCreateCompletionStage(executor);
        addCallbacks(nextStage, fn, executor);
        return nextStage;
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return thenApplyAsync(consumerAsFunction(action), executor);
    }
    
    @Override
    public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
        return thenApplyAsync(runnableAsFunction(action), executor);
    }
    
    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                      BiFunction<? super T, ? super U, ? extends V> fn,
                                                      Executor executor) {
    	
        return thenCompose(result1 -> other.thenApplyAsync(result2 -> fn.apply(result1, result2), executor));
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
        return thenCombineAsync(
            other,
            // transform BiConsumer to BiFunction
            (t, u) -> {
                action.accept(t, u);
                return null;
            },
            executor
        );
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return thenCombineAsync(
            other,
            // transform Runnable to BiFunction
            (t, r) -> {
                action.run();
                return null;
            },
            executor
        );
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                                     Function<? super T, U> fn,
                                                     Executor executor) {
    	
        return doApplyToEitherAsync(this, other, fn, executor);
    }

    /**
     * This method exists just to reconcile generics when called from {@link #runAfterEitherAsync}
     * which has unexpected type of parameter "other". The alternative is to ignore compiler warning.
     */
    private <R, U> CompletionStage<U> doApplyToEitherAsync(CompletionStage<? extends R> first,
                                                           CompletionStage<? extends R> second,
                                                           Function<? super R, U> fn,
                                                           Executor executor) {
    	
    	BlockingCompletionStage<R> nextStage = internalCreateCompletionStage(executor);


        // Next stage is not exposed to the client, so we can
        // short-circuit its initiation - just fire callbacks
        // without task execution (unlike as in thenComposeAsync)
        // In ceratin sense, nextStage here is bogus: neither
        // of Future-defined methods are functional.    	
        BiConsumer<R, Throwable> action = (result, failure) -> {
            if (failure == null) {
            	nextStage.onSuccess(result);
            } else {
                nextStage.onError(wrapException(failure));
            }
        };
        // only the first result is accepted by completion stage,
        // the other one is ignored
        first.whenComplete(action);
        second.whenComplete(action);

        
        return nextStage.thenApplyAsync(fn, executor);
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other,
    		                                       Consumer<? super T> action, 
    		                                       Executor executor) {
    	
        return applyToEitherAsync(other, consumerAsFunction(action), executor);
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return doApplyToEitherAsync(this, other, runnableAsFunction(action), executor);
    }    
    
    @Override
    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, 
    		                                       Executor executor) {
    	
    	BlockingCompletionStage<U> nextStage = internalCreateCompletionStage(executor);
    	BlockingCompletionStage<Void> tempStage = internalCreateCompletionStage(executor);
    	
    	// Success path, just return value
    	// Failure path, just re-throw exception
    	BiConsumer<? super U, ? super Throwable> moveToNextStage = (r, e) -> {
    		if (null == e)
    			runDirectly(nextStage, Function.identity(), r, executor);
    		else
    			runDirectly(nextStage, BlockingCompletionStage::forwardException, e, executor);
    	};
    	
    	// Important -- tempStage is the target here
        addCallbacks(
        	tempStage,
            consumerAsFunction(r -> fn.apply(r).whenComplete(moveToNextStage)),
            e -> { moveToNextStage.accept(null, e); return null; },
            executor
        );
        
        return nextStage;
    }

    
    @Override
    public CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn) {
    	BlockingCompletionStage<T> nextStage = internalCreateCompletionStage(getDefaultExecutor());
        addCallbacks(nextStage, Function.identity(), fn, SAME_THREAD_EXECUTOR);
        return nextStage;
    }
    
    @Override
    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
    	BlockingCompletionStage<T> nextStage = internalCreateCompletionStage(getDefaultExecutor());
        addCallbacks(nextStage, 
            result -> {
            	action.accept(result, null);
                return result;
            },
            failure -> {
                try {
                    action.accept(null, failure);
                    return forwardException(failure);
                } catch (Throwable e) {
                    return forwardException(e);
                }
            }, executor
        );
        return nextStage;
    }    
    
    @Override
    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
    	BlockingCompletionStage<U> nextStage = internalCreateCompletionStage(executor);
        addCallbacks(
        	nextStage, 
        	result -> fn.apply(result, null),
        	// exceptions are treated as success
            error -> fn.apply(null, error),
            executor
        );
        return nextStage;
    }    

    
    @Override
    public CompletableFuture<T> toCompletableFuture() {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        Function<Callable<T>, Runnable> setup = c -> () -> {
			try {
				c.call();
			} catch (final Throwable ex) {
				completableFuture.completeExceptionally(ex);
			}
		}; 
        addCallbacks(
        	setup, 
        	consumerAsFunction(completableFuture::complete),
    		consumerAsFunction(completableFuture::completeExceptionally),
    		SAME_THREAD_EXECUTOR
        );
        return completableFuture;
    }
    
    
    protected <U> BlockingCompletionStage<U> internalCreateCompletionStage(Executor executor) {
    	return createCompletionStage(executor == SAME_THREAD_EXECUTOR ? getDefaultExecutor() : executor);
    }
    
    abstract protected <U> BlockingCompletionStage<U> createCompletionStage(Executor executor);
    
    private <V, R> Function<V, R> consumerAsFunction(Consumer<? super V> action) {
        return result -> {
            action.accept(result);
            return null;
        };
    }
    
    private static <R> Function<R, Void> runnableAsFunction(Runnable action) {
        return result -> {
            action.run();
            return null;
        };
    }
    
    private static <U> U forwardException(Throwable e) {
    	throw wrapException(e);
    }
    
    private static CompletionException wrapException(Throwable e) {
        if (e instanceof CompletionException) {
            return (CompletionException)e;
        } else {
            return new CompletionException(e);
        }
    }

    private <U> void addCallbacks(BlockingCompletionStage<U> targetStage, 
    		                      Function<? super T, ? extends U> successCallback, 
    		                      Executor executor) {
    	addCallbacks(targetStage, successCallback, BlockingCompletionStage::forwardException, executor);
    }
    
    private <U> void addCallbacks(BlockingCompletionStage<U> targetStage, 
    		                      Function<? super T, ? extends U> successCallback, 
    		                      Function<Throwable, ? extends U> failureCallback, 
    		                      Executor executor) {
        addCallbacks(targetStage::setupTransition, successCallback, failureCallback, executor);
    }
    
    private <U> void addCallbacks(Function<? super Callable<U>, ? extends Runnable> targetSetup, 
    		                      Function<? super T, ? extends U> successCallback, 
    		                      Function<Throwable, ? extends U> failureCallback, 
    		                      Executor executor) {
        callbackRegistry.addCallbacks(targetSetup, successCallback, failureCallback, executor);
    }
    
    private static <S, U> void runDirectly(BlockingCompletionStage<U> targetStage, 
                                           Function<? super S, ? extends U> callback, 
                                           S value, 
                                           Executor executor) {
    	
    	CallbackRegistry.callCallback(targetStage::setupTransition, callback, value, executor);
    }
}
