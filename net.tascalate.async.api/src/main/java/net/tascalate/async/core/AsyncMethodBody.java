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
import net.tascalate.concurrent.Promises;

abstract public class AsyncMethodBody implements Runnable {
    private final ContextualExecutor contextualExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private volatile CompletionStage<?> originalAwait;
    private volatile CompletableFuture<?> terminateMethod;
    
    protected AsyncMethodBody(ContextualExecutor contextualExecutor) {
        this.contextualExecutor = contextualExecutor != null ? 
        contextualExecutor : ContextualExecutor.sameThreadContextless();
    }

    boolean isRunning() {
    	return running.get();
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
    
    ContextualExecutor contextualExecutor() {
        return contextualExecutor;
    }
    
    <T> CompletionStage<T> registerAwaitTarget(CompletionStage<T> originalAwait) {
    	CompletableFuture<T> terminateMethod = new CompletableFuture<>();
        CompletionStage<T> guardedAwait = terminateMethod.applyToEither(originalAwait, Function.identity());
        // Save references for outer promise cancellation
        this.terminateMethod = terminateMethod;
        this.originalAwait   = originalAwait;
        // Re-check for race with main future cancellation
        cancelAwaitIfNecessary(terminateMethod, originalAwait);
        return guardedAwait;
    }
    
    protected void cancelAwaitIfNecessary() {
        cancelAwaitIfNecessary(terminateMethod, originalAwait);
    }
    
    protected void cancelAwaitIfNecessary(CompletableFuture<?> terminateMethod, CompletionStage<?> originalAwait) {
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
