/**
 * Copyright 2015-2025 Valery Silaev (http://vsilaev.com)
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;

import org.apache.commons.javaflow.core.StackOwner;
import org.apache.commons.javaflow.core.StackRecorder;

import net.tascalate.async.AsyncResult;
import net.tascalate.async.Scheduler;
import net.tascalate.async.suspendable;

abstract public class AbstractAsyncMethod extends StackOwner implements Runnable {
    
    private static final AtomicReferenceFieldUpdater<AbstractAsyncMethod, State> STATE_UPDATER = 
            AtomicReferenceFieldUpdater.newUpdater(AbstractAsyncMethod.class, State.class, "state");
    
    private static final AtomicLongFieldUpdater<AbstractAsyncMethod> BLOCKER_VERSION_UPDATER = 
            AtomicLongFieldUpdater.newUpdater(AbstractAsyncMethod.class, "blockerVersion");
    
    enum State {
        INITIAL, RUNNING, COMPLETED
    }
    
    public final CompletableFuture<?> future;
    
    private final Scheduler scheduler;
    
    private volatile State state = State.INITIAL;
    private volatile long blockerVersion = 0;

    private volatile PhaseCancellation<?> phaseCancellation;
    
    private StackRecorder stackRecorder;
    
    protected AbstractAsyncMethod(Scheduler scheduler) {
        this.future = new ResultPromise<>();
        this.scheduler = scheduler != null ? scheduler : Scheduler.sameThreadContextless();
    }

    public final @suspendable void run() {
        if (!STATE_UPDATER.compareAndSet(this, State.INITIAL, State.RUNNING)) {
            throw new IllegalStateException(getClass().getName() + " should be in INITIAL state");
        }
        try {
            internalRun();
        } finally {
            if (!STATE_UPDATER.compareAndSet(this, State.RUNNING, State.COMPLETED)) {
                throw new IllegalStateException(getClass().getName() + " should be in RUNNING state");
            }           	
        }
    }
    
    abstract protected @suspendable void internalRun();

    final boolean isRunning() {
        return state == State.RUNNING;
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
    
    protected final Scheduler scheduler() {
        return scheduler;
    }
    
    final protected String toString(String implementationName, String className, String methodSignature) {
        PhaseCancellation<?> currentPhaseCancellation = phaseCancellation;
        return String.format("%s[origin-class=%s, origin-method=%s, state=%s, scheduler=%s, blocker-version=%s, awaiting-on=%s]", 
            implementationName, className, methodSignature,
            state, scheduler, blockerVersion, currentPhaseCancellation == null ? null : currentPhaseCancellation.awaitingOn()
        );
    }

    final Runnable createResumeHandler(Runnable originalResumer) {
        long currentBlockerVersion = blockerVersion;
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
        return new Runnable() {
            @Override
            public void run() {
                try {
                    scheduler.schedule(contextualResumer);
                } catch (RejectedExecutionException ex) {
                    failure(ex);
                }
            }
        };        
    }
    
    private boolean registerResumeTarget(CompletionStage<?> resumePromise, long expectedBlockerVersion) {
        if (BLOCKER_VERSION_UPDATER.compareAndSet(this, expectedBlockerVersion, expectedBlockerVersion + 1)) {
           PhaseCancellation<?> resumePhaseCancellation = new PhaseCancellation<>(resumePromise, false);
            // Save references for outer promise cancellation
            this.phaseCancellation = resumePhaseCancellation;
            // Re-check for race with main future cancellation
            cancelAwaitIfNecessary(resumePhaseCancellation);

            return true;
        } else {
            return false;
        }
    }
    
    final <V> CompletionStage<V> registerAwaitTarget(CompletionStage<V> originalAwait) {
        BLOCKER_VERSION_UPDATER.incrementAndGet(this);
        PhaseCancellation<V> awaitPhaseCancellation = new PhaseCancellation<>(originalAwait, true);
        // Save references for outer promise cancellation
        this.phaseCancellation = awaitPhaseCancellation;
        // Re-check for race with main future cancellation
        cancelAwaitIfNecessary(awaitPhaseCancellation);
        return awaitPhaseCancellation.createGuardedAwait();
    }

    private void cancelAwaitIfNecessary(PhaseCancellation<?> phaseCancellation) {
        if (future.isCancelled()) {
            cancelAwaitUnconditionally(phaseCancellation);
        }
    }
    
    final void cancelAwaitUnconditionally(PhaseCancellation<?> phaseCancellation) {
        // No longer need reference
        this.phaseCancellation = null;
        if (null != phaseCancellation) {
            phaseCancellation.proceed();
        }
    }

    static final class PhaseCancellation<V> {
        private final CompletionStage<V> originalAwait;
        private final CompletableFuture<V> terminateMethod;
        
        PhaseCancellation(CompletionStage<V> originalAwait, boolean terminateMethod) {
            this.originalAwait = originalAwait;
            this.terminateMethod = terminateMethod ? new CompletableFuture<>() : null;
        }
        
        void proceed() {
            // First terminate method to avoid exceptions in method
            if (null != terminateMethod) {
                terminateMethod.completeExceptionally(CloseSignal.INSTANCE);
            }
            // Then cancel promise we are waiting on
            if (null != originalAwait) {
                CompletionStageHelper.cancelCompletionStage(originalAwait, true);
            }            
        }
        
        CompletionStage<V> createGuardedAwait() {
            if (null == terminateMethod) {
                throw new IllegalStateException("Unnable to create guarded await for the phase without method termination");
            } else {
                return terminateMethod.applyToEither(originalAwait, Function.identity());
            }
        }
        
        CompletionStage<V> awaitingOn() {
            return originalAwait;
        }
    }
    
    @Override
    protected final StackRecorder getStack() {
        return stackRecorder;
    }
    
    @Override
    protected final void setStack(StackRecorder value) {
        stackRecorder = value;
    }
    
    final class ResultPromise<T> extends RestrictedCompletableFuture<T> implements AsyncResult<T> {
        
        ResultPromise() {}
        
        @Override
        public Scheduler scheduler() {
            return scheduler;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean doCancel = mayInterruptIfRunning || !isRunning();
            if (!doCancel) {
                return false;
            }
            cancelAwaitUnconditionally(phaseCancellation);
            return super.cancel(mayInterruptIfRunning);
        }
    }
}
