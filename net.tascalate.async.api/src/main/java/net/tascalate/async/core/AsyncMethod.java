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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.concurrent.CompletableTask;
import net.tascalate.concurrent.Promises;

abstract public class AsyncMethod implements Runnable {
    protected final CompletableFuture<?> future;
    
    private final ContextualExecutor contextualExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong blockerVersion = new AtomicLong(0);
    
    private volatile CompletionStage<?> originalAwait;
    private volatile CompletableFuture<?> terminateMethod;
    
    protected AsyncMethod(ContextualExecutor contextualExecutor) {
        this.future = new ResultPromise<>();
        this.contextualExecutor = contextualExecutor != null ? 
        contextualExecutor : ContextualExecutor.sameThreadContextless();
    }

    public final @continuable void run() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException(getClass().getName() + " is already running");
        }
        try {
            internalRun();
        } finally {
            if (!running.compareAndSet(true, false)) {
                throw new IllegalStateException(getClass().getName() + " is not running");
            }           	
        }
    }
    
    abstract protected @continuable void internalRun();

    boolean isRunning() {
        return running.get();
    }

    void cancelAwaitIfNecessary() {
        cancelAwaitIfNecessary(terminateMethod, originalAwait);
    }
    
    Runnable createResumeHandler(Runnable originalResumer) {
        long currentBlockerVersion = blockerVersion.get();
        Runnable contextualResumer = contextualExecutor.contextualize(originalResumer);
        if (contextualExecutor.interruptible()) {
            return () -> {
                CompletionStage<?> resumeFuture = CompletableTask.runAsync(
                    contextualResumer, contextualExecutor
                );
                registerResumeTarget(resumeFuture, currentBlockerVersion);
            };
        } else {
            Thread suspendThread = Thread.currentThread();
            return () -> {
                if (Thread.currentThread() == suspendThread) {
                    // Is it possible to use originalResumer here, i.e. one without context???
                    contextualResumer.run();
                } else {
                    contextualExecutor.execute(contextualResumer);
                }
            };
        }        
    }
    
    private boolean registerResumeTarget(CompletionStage<?> resumePromise, long expectedBlockerVersion) {
        if (blockerVersion.compareAndSet(expectedBlockerVersion, expectedBlockerVersion + 1)) {
            // Save references for outer promise cancellation
            this.terminateMethod = null;
            this.originalAwait   = resumePromise;
            // Re-check for race with main future cancellation
            cancelAwaitIfNecessary(null, resumePromise);

            return true;
        } else {
            return false;
        }
    }
    
    <V> CompletionStage<V> registerAwaitTarget(CompletionStage<V> originalAwait) {
        blockerVersion.incrementAndGet();
    	CompletableFuture<V> terminateMethod = new CompletableFuture<>();
        CompletionStage<V> guardedAwait = terminateMethod.applyToEither(originalAwait, Function.identity());
        // Save references for outer promise cancellation
        this.terminateMethod = terminateMethod;
        this.originalAwait   = originalAwait;
        // Re-check for race with main future cancellation
        cancelAwaitIfNecessary(terminateMethod, originalAwait);
        return guardedAwait;
    }

    private void cancelAwaitIfNecessary(CompletableFuture<?> terminateMethod, CompletionStage<?> originalAwait) {
        if (future.isCancelled()) {
        	this.terminateMethod = null;
            // First terminate method to avoid exceptions in method
            terminateMethod.completeExceptionally(CloseSignal.INSTANCE);
            // No longer need reference
            this.originalAwait = null;
            // Then cancel promise we are waiting on
            if (null != originalAwait) {
                Promises.from(originalAwait).cancel(true);
            }
        }
    }
    
    class ResultPromise<T> extends CompletableFuture<T> {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean doCancel = mayInterruptIfRunning || !isRunning();
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
    }
}
