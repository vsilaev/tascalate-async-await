/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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

import java.lang.reflect.Method;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import net.tascalate.async.AsyncValue;
import net.tascalate.async.Scheduler;
import net.tascalate.async.suspendable;

abstract public class AbstractAsyncMethod implements Runnable {
    
    enum State {
        INITIAL, RUNNING, COMPLETED
    }
    
    public final CompletableFuture<?> future;
    
    private final Scheduler scheduler;
    private final AtomicReference<State> state = new AtomicReference<>(State.INITIAL);
    private final AtomicLong blockerVersion = new AtomicLong(0);
    
    private volatile CompletionStage<?> originalAwait;
    private volatile CompletableFuture<?> terminateMethod;
    
    protected AbstractAsyncMethod(Scheduler scheduler) {
        this.future = new ResultPromise<>();
        this.scheduler = scheduler != null ? scheduler : Scheduler.sameThreadContextless();
    }

    public final @suspendable void run() {
        if (!state.compareAndSet(State.INITIAL, State.RUNNING)) {
            throw new IllegalStateException(getClass().getName() + " should be in INITIAL state");
        }
        try {
            internalRun();
        } finally {
            if (!state.compareAndSet(State.RUNNING, State.COMPLETED)) {
                throw new IllegalStateException(getClass().getName() + " should be in RUNNING state");
            }           	
        }
    }
    
    abstract protected @suspendable void internalRun();

    final boolean isRunning() {
        return state.get() == State.RUNNING;
    }
    
    protected final boolean interrupted() {
        return future.isCancelled();
    }

    @SuppressWarnings("unchecked")
    protected final <T> boolean success(T value) {
        return ((ResultPromise<T>)future).internalSuccess(value);
    }
    
    protected final <T> boolean failure(Throwable exception) {
        return ((ResultPromise<?>)future).internalFailure(exception);
    }
    
    final void cancelAwaitIfNecessary() {
        cancelAwaitIfNecessary(terminateMethod, originalAwait);
    }
    
    final Scheduler scheduler() {
        return scheduler;
    }
    
    final protected String toString(String implementationName, String className, String methodSignature) {
        return String.format("%s[origin-class=%s, origin-method=%s, state=%s, scheduler=%s, blocker-version=%s, awaiting-on=%s]", 
            implementationName, className, methodSignature,
            state, scheduler, blockerVersion, originalAwait
        );
    }
    
    final Runnable createResumeHandler(Runnable originalResumer) {
        long currentBlockerVersion = blockerVersion.get();
        Runnable contextualResumer = scheduler.contextualize(originalResumer);
        if (scheduler.characteristics().contains(Scheduler.Characteristics.INTERRUPTIBLE)) {
            return createInterruptibleResumeHandler(contextualResumer, currentBlockerVersion);
        } else {
            return createSimplifiedResumeHandler(contextualResumer, currentBlockerVersion);
        }        
    }
    
    private Runnable createInterruptibleResumeHandler(Runnable contextualResumer, long currentBlockerVersion) {
        return new Runnable() {
            @Override
            public void run() {
                CompletionStage<?> resumeFuture;
                try {
                    resumeFuture = scheduler.schedule(contextualResumer);
                } catch (RejectedExecutionException ex) {
                    failure(ex);
                    return;
                }
                registerResumeTarget(resumeFuture, currentBlockerVersion);
            }
        };        
    }
    
    private Runnable createSimplifiedResumeHandler(Runnable contextualResumer, long currentBlockerVersion) {
        Thread suspendThread = Thread.currentThread();
        return new Runnable() {
            @Override
            public void run() {
                if (Thread.currentThread() == suspendThread) {
                    // Is it possible to use originalResumer here, i.e. one without context???
                    contextualResumer.run();
                } else {
                    try {
                        scheduler.schedule(contextualResumer);
                    } catch (RejectedExecutionException ex) {
                        failure(ex);
                    }
                }
            }
        };        
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
    
    final <V> CompletionStage<V> registerAwaitTarget(CompletionStage<V> originalAwait) {
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
                cancelCompletionStage(originalAwait, true);
            }
        }
    }
    
    final class ResultPromise<T> extends CompletableFuture<T> implements AsyncValue<T> {
        
        ResultPromise() {}
        
        protected boolean internalSuccess(T value) {
            return super.complete(value);
        }
        
        protected boolean internalFailure(Throwable exception) {
            return super.completeExceptionally(exception);
        }
        
        @Override
        public boolean complete(T value) {
            throw new UnsupportedOperationException("ResultPromise may not be completed explicitly");
        }
        
        @Override
        public boolean completeExceptionally(Throwable exception) {
            throw new UnsupportedOperationException("ResultPromise may not be completed explicitly");
        }        
        
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
    
    
    private static boolean cancelCompletionStage(CompletionStage<?> promise, boolean mayInterruptIfRunning) {
        if (promise instanceof Future) {
            Future<?> future = (Future<?>) promise;
            return future.cancel(mayInterruptIfRunning);
        } else {
            Method m = completeExceptionallyMethodOf(promise);
            if (null != m) {
                try {
                    return (Boolean) m.invoke(promise, new CancellationException());
                } catch (final ReflectiveOperationException ex) {
                    return false;
                }
            } else {
                return false;
            }
        }
    }
    

    private static Method completeExceptionallyMethodOf(CompletionStage<?> promise) {
        try {
            Class<?> clazz = promise.getClass();
            return clazz.getMethod("completeExceptionally", Throwable.class);
        } catch (ReflectiveOperationException | SecurityException ex) {
            return null;
        }
    }

}
