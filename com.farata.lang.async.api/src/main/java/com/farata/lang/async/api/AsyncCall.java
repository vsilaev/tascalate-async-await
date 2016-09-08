package com.farata.lang.async.api;

import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;

import com.farata.lang.async.core.AsyncExecutor;
import com.farata.lang.async.core.NoActiveAsyncCallException;

/**
 * @author Valery Silaev
 * 
 */
public class AsyncCall {

    /**
     * Wait for the {@link CompletionStage} within {@link async} method.
     * 
     * The {@link async} method will be suspended until {@link CompletionStage}
     * returns or throws the result.
     */
    public @continuable static <T, E extends Throwable> T await(final CompletionStage<T> condition,
            final Class<E> expectedException) throws NoActiveAsyncCallException, E {

        return AsyncExecutor.<T, E> await(condition);
    }

    public @continuable static <T> T await(final CompletionStage<T> condition) throws NoActiveAsyncCallException {

        return AsyncExecutor.await(condition);
    }

    public static <T> CompletionStage<T> asyncResult(final T value) {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

    public static <T> Object yield(final T readyValue) {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

    public static <T> Object yield(final CompletionStage<T> pendingValue) {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

    public static <T> void yield(final Generator<T> values) {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }
    
    public static <T> Generator<T> yield() {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

}
