/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.

 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.tascalate.async.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;

abstract public class AsyncTask<V> extends AsyncMethodBody {
    public final Promise<V> future;

    final AtomicBoolean running = new AtomicBoolean(false);
    
    private volatile CompletionStage<?> originalAwait;
    private final CompletableFuture<?> terminateMethod = new CompletableFuture<>();
    
    protected AsyncTask(ContextualExecutor contextualExecutor) {
        super(contextualExecutor);
        this.future = new ResultPromise<V>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                boolean doCancel = mayInterruptIfRunning || !running.get();
                if (!doCancel) {
                    return false;
                }
                if (super.cancel(mayInterruptIfRunning)) {
                    cancelAwaitIfNecessary();
                    return true;
                } else {
                    return false;
                }
            }
        };
    }
    
    @Override
    public final @continuable void run() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("AsyncTask is already running");
        }
        try {
            doRun();
            // ensure that promise is resolved
            $$result$$(null, this);
        } catch (Throwable ex) {
            ResultPromise<V> future = (ResultPromise<V>)this.future;
            future.internalCompleWithException(ex);
        } finally {
            if (!running.compareAndSet(true, false)) {
                throw new IllegalStateException("AsyncTask is not running");
            }
        }
    }
    
    abstract protected @continuable void doRun() throws Throwable;

    protected static <V> CompletionStage<V> $$result$$(final V value, final AsyncTask<V> self) {
        ResultPromise<V> future = (ResultPromise<V>)self.future;
        future.internalCompleWithResult(value);
        return future;
    }
    
    protected @continuable static <T, V> T $$await$$(final CompletionStage<T> originalAwait, final AsyncTask<V> self) {
        CompletionStage<T> guardedAwait = self
            .terminatorOf(originalAwait)
            .applyToEither(originalAwait, Function.identity());
        
        // Save reference for outer promise cancellation
        self.originalAwait = originalAwait;
        // Re-check for race with main future cancellation
        self.cancelAwaitIfNecessary(originalAwait);

        return AsyncMethodExecutor.await(guardedAwait);
    }
    
    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> terminatorOf(CompletionStage<T> guarded) {
        return (CompletableFuture<T>)terminateMethod;
    }
    
    void cancelAwaitIfNecessary() {
    	cancelAwaitIfNecessary(originalAwait);
    }
    
    private void cancelAwaitIfNecessary(final CompletionStage<?> target) {
        if (future.isCancelled()) {
            // First terminate method to avoid exceptions in method
            terminateMethod.completeExceptionally(CloseSignal.INSTANCE);
            // No longer need reference
            this.originalAwait = null;
            // Then cancel promise we are waiting on
            if (null != target) {
                Promises.from(target).cancel(true);
            }
        }
    }

}
