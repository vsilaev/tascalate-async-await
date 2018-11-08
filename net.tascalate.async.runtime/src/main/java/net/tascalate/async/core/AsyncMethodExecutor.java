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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.javaflow.api.Continuation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.tascalate.async.InvalidCallContextException;
import net.tascalate.async.Scheduler;
import net.tascalate.async.suspendable;

/**
 * 
 * @author Valery Silaev
 */
public class AsyncMethodExecutor {

    private static final Log log = LogFactory.getLog(AsyncMethodExecutor.class);

    private static final AsyncMethodExecutor INSTANCE = new AsyncMethodExecutor();

    /**
     * Execute the {@link AbstractAsyncMethod}.
     */
    public static void execute(AbstractAsyncMethod asyncMethod) {
        INSTANCE.executeTask(asyncMethod);
    }

    /**
     */
    protected void executeTask(AbstractAsyncMethod asyncMethod) {
        // Create the initial Continuation
        log.debug("Starting suspended Continuation");
        Continuation continuation = Continuation.startSuspendedWith(asyncMethod);
        // Start it
        ContinuationResumer<?, Throwable> originalInvoker = new ContinuationResumer<>(continuation);
        originalInvoker.setup(null, null);
        asyncMethod.createResumeHandler(originalInvoker).run();
    }

    /**
     */
    protected void resume(Continuation initialContinuation, Object context) {
        // Continue Continuation
        log.debug("Continueing continuation");
        Continuation newContinuation = initialContinuation.resume(context);
        // Continuation finished or suspended

        if (newContinuation == null) {
            // Continuation finished
            log.debug("Continuation finished");
            return;
        }

        // Continuation suspended
        log.debug("Continuation suspended");

        // Check if the Continuation was suspended in our way.
        if (!(newContinuation.value() instanceof SuspendParams)) {
            throw new InvalidCallContextException("Continuation was suspended incorrectly, use AsyncCall.await");
        }

        setupContinuation(newContinuation);
    }

    protected <R, E extends Throwable> void setupContinuation(Continuation continuation) {
        @SuppressWarnings("unchecked")
        SuspendParams<R> suspendParams = (SuspendParams<R>)continuation.value();
        
        CompletionStage<R> future   = suspendParams.future;
        AbstractAsyncMethod suspendedMethod = suspendParams.suspendedMethod;
        
        ContinuationResumer<? super R, Throwable> originalResumer = new ContinuationResumer<>(continuation);
        Runnable wrappedResumer = suspendedMethod.createResumeHandler(originalResumer);
        // Setup future and give it a chance to continue the Continuation
        try {
            future.whenComplete((r, e) -> {
                originalResumer.setup(r, e);
                wrappedResumer.run();
            });
        } catch (Throwable error) {
            resume(continuation, FutureResult.failure(error));
        }
    }

    /**
     */
    public @suspendable static <R, E extends Throwable> R await(CompletionStage<R> future) throws E {
        return INSTANCE.awaitTask(future);
    }

    /**
     */
    protected @suspendable <R, E extends Throwable> R awaitTask(CompletionStage<R> future) throws E {
        // Blocking is available - resume() method is being called
    	
        // If promise is already resolved don't suspend
        // at all but rather return directly
        FutureResult<R, E> earlyResult = getResolvedOutcome(future);
        if (earlyResult != null) {
            return earlyResult.done();
        }
        
        AbstractAsyncMethod currentMethod = InternalCallContext.asyncMethod();

        // Register (and wrap) promise we are blocking on
        // to support cancellation from outside
        future = currentMethod.registerAwaitTarget(future);
    	
        // Let's sleep!
        log.debug("Suspending continuation");
        Object outcome = Continuation.suspend(
            // Save Runnable of the suspending continuation + future
            new SuspendParams<>(currentMethod, future)
        );
        log.debug("Continuation continued");

        if (outcome instanceof FutureResult) {
            // Unwrap and return value
            @SuppressWarnings("unchecked")
            FutureResult<R, E> either = (FutureResult<R, E>) outcome;
            return either.done();
        } else {
            // Illegal wake-up
            throw new InvalidCallContextException(
                "Continuation was suspended incorrectly - are your classes instrumented for javaflow?"
            );
        }
    }
    
    public static Scheduler currentScheduler(Scheduler explicitScheduler, Object owner, Class<?> ownerDeclaringClass) {
        return null != explicitScheduler ? 
            explicitScheduler 
            : 
            SchedulerResolvers.currentScheduler(owner, ownerDeclaringClass);
    }
    
    private static <R, E extends Throwable> FutureResult<R, E> getResolvedOutcome(CompletionStage<R> stage) {
        if (stage instanceof Future) {
            @SuppressWarnings("unchecked")
            Future<R> future = (Future<R>)stage;
            if (future.isDone()) {
                try {
                    return FutureResult.success(future.get());
                } catch (CancellationException ex) {
                    @SuppressWarnings("unchecked")
                    E error = (E)ex;
                    return FutureResult.failure(error);
                } catch (ExecutionException ex) {
                    @SuppressWarnings("unchecked")
                    E error = (E)Exceptions.unrollExecutionException(ex);
                    return FutureResult.failure(error);
                } catch (InterruptedException ex) {
                    throw new IllegalStateException("Completed future throws interrupted exception");
                }
            }
        }
        return null;
    }

    static class SuspendParams<R> {
        final AbstractAsyncMethod suspendedMethod;
        final CompletionStage<R> future;
        
        SuspendParams(AbstractAsyncMethod suspendedMethod, CompletionStage<R> future) {
            this.suspendedMethod = suspendedMethod;
            this.future = future;
        }
    }
    
    abstract static class FutureResult<R, E extends Throwable> {

        abstract R done() throws E;
        
        static class Success<R, E extends Throwable> extends FutureResult<R, E> {
            
            private final R result;
            
            Success(R result) {
                this.result = result;
            }
            
            @Override
            final R done() throws E {
                return result;
            }
        }
        
        static class Failure<R, E extends Throwable> extends FutureResult<R, E> {
            
            private final E error;
            
            Failure(E error) {
                this.error = error;
            }
            
            @Override
            final R done() throws E {
                throw error;
            }
            
        }

        static <R, E extends Throwable> FutureResult<R, E> success(R result) {
            return new Success<R, E>(result);
        }

        static <R, E extends Throwable> FutureResult<R, E> failure(E error) {
            return new Failure<R, E>(error);
        }
    }

    
    class ContinuationResumer<R, E extends Throwable> implements Runnable {
        private final Continuation continuation;
        private R result;
        private E error;
        ContinuationResumer(Continuation continuation) {
            this.continuation = continuation;
        }
        
        void setup(R result, E error) {
            this.result = result;
            this.error = error;
        }
        
        @Override
        public void run() {
            if (error == null) {
                resume(continuation, FutureResult.success(result));
            } else {
                Throwable ex = Exceptions.unrollCompletionException(error);
                if (CloseSignal.INSTANCE == ex) {
                    continuation.terminate();
                } else {
                    resume(continuation, FutureResult.failure(ex));
                }
            }            
        }
    }
}
