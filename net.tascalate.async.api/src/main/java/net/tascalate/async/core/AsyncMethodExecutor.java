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

import java.io.Serializable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.javaflow.api.Continuation;
import org.apache.commons.javaflow.api.continuable;
import org.apache.commons.javaflow.core.StackRecorder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.async.api.NoActiveAsyncCallException;

/**
 * 
 * @author Valery Silaev
 */
public class AsyncMethodExecutor implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(AsyncMethodExecutor.class);

    private static final AsyncMethodExecutor INSTANCE = new AsyncMethodExecutor();

    /**
     * Execute the {@link AsyncMethodBody}.
     */
    public static void execute(AsyncMethodBody runnable) {
        INSTANCE.executeTask(runnable);
    }

    /**
     * Execute the {@link Continuation} and start {@link Continuator} when the
     * {@link Continuation} suspends.
     */
    protected void executeTask(AsyncMethodBody runnable) {
        // Create the initial Continuation
        log.debug("Starting suspended Continuation");
        Continuation continuation = Continuation.startSuspendedWith(runnable);
        // Start it
        ContextualExecutor ctxExecutor = runnable.contextualExecutor();
        if (ctxExecutor.useAsInvoker()) {
            ctxExecutor.execute(ctxExecutor.contextualize( 
                () -> resume(continuation, null) 
            ));
        } else {
            resume(continuation, null);
        }
    }

    /**
     * Continue the {@link Continuation} and start {@link Continuator} when the
     * {@link Continuation} suspends.
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
        if (newContinuation.value() == null) {
            throw new NoActiveAsyncCallException("Continuation was suspended incorrectly");
        }

        setupContinuation(newContinuation);
    }

    protected <R, E extends Throwable> void setupContinuation(Continuation continuation) {
        @SuppressWarnings("unchecked")
        SuspendParams<R> suspendParams = (SuspendParams<R>)continuation.value();
        CompletionStage<R> future      = suspendParams.future;
        ContextualExecutor ctxExecutor = suspendParams.contextualExecutor; 

        ContinuationResumer<? super R, Throwable> originalResumer = new ContinuationResumer<>(continuation);
        Runnable contextualResumer = ctxExecutor.contextualize(originalResumer);
        Thread suspendThread = Thread.currentThread();
        // Setup future and give it a chance to continue the Continuation
        try {
            future.whenComplete((r, e) -> {
                originalResumer.setup(r, e);
                if (Thread.currentThread() == suspendThread) {
                    // Is it possible to use originalResumer here, i.e. one without context???
                    contextualResumer.run();
                } else {
                    ctxExecutor.execute(contextualResumer);
                }
            });
        } catch (Throwable error) {
            resume(continuation, Either.error(error));
        }
    }

    /**
     */
    public @continuable static <R, E extends Throwable> R await(CompletionStage<R> future) throws E {
        return INSTANCE.awaitTask(future);
    }

    /**
     */
    protected @continuable <R, E extends Throwable> R awaitTask(CompletionStage<R> future) throws E {
        // Blocking is available - resume() method is being called
    	
    	AsyncMethodBody currentMethod = currentExecution();

    	// Register (and wrap) promise we are blocking on
    	// to support cancellation from outside
    	future = currentMethod.registerAwaitTarget(future);
    	
    	// If promise is already resolved don't suspend
    	// at all but rather return directly
    	Either<R, E> earlyResult = getResolvedOutcome(future);
    	if (earlyResult != null) {
    		return earlyResult.done();
    	}
    	
        // Let's sleep!
        log.debug("Suspending continuation");
        Object outcome = Continuation.suspend(
            // Save contextualExecutor of the suspending continuation 
            new SuspendParams<>(future, currentMethod.contextualExecutor())
        );
        log.debug("Continuation continued");

        if (outcome instanceof Either) {
            // Unwrap and return value
            @SuppressWarnings("unchecked")
            Either<R, E> either = (Either<R, E>) outcome;
            return either.done();
        } else {
            // Illegal wake-up
            throw new NoActiveAsyncCallException(
                "Continuation was suspended incorrectly - are your classes instrumented for javaflow?"
            );
        }
    }
    
	private static <R, E extends Throwable> Either<R, E> getResolvedOutcome(CompletionStage<R> stage) {
    	if (stage instanceof Future) {
    		@SuppressWarnings("unchecked")
			Future<R> future = (Future<R>)stage;
    		if (future.isDone()) {
    			try {
    				return Either.result(future.get());
    			} catch (CancellationException ex) {
    				@SuppressWarnings("unchecked")
    				E error = (E)ex;
    				return Either.error(error);
    			} catch (ExecutionException ex) {
    				@SuppressWarnings("unchecked")
    				E error = (E)unrollExecutionException(ex);
    				return Either.error(error);
    			} catch (InterruptedException ex) {
					throw new IllegalStateException("Completed future throws interrupted exception");
				}
    		}
    	}
		return null;
    }
    
    private static AsyncMethodBody currentExecution() {
        StackRecorder stackRecorder = StackRecorder.get();
        if (null == stackRecorder) {
            throw new NoActiveAsyncCallException(
                "Continuation was continued incorrectly - are your classes instrumented for javaflow?"
            );
        }
        Runnable result = stackRecorder.getRunnable();
        if (result instanceof AsyncMethodBody) {
            return (AsyncMethodBody)result;
            
        } else {
            throw new NoActiveAsyncCallException(
                "Current runnable is not " + AsyncMethodBody.class.getName() + " - are your classes instrumented for javaflow?"
            );
        }
    }
    
    static Throwable unrollExecutionException(Throwable ex) {
        Throwable nested = ex;
        while (nested instanceof ExecutionException) {
            nested = nested.getCause();
        }
        return null == nested ? ex : nested;
    }
    
    static Throwable unrollCompletionException(Throwable ex) {
        Throwable nested = ex;
        while (nested instanceof CompletionException) {
            nested = nested.getCause();
        }
        return null == nested ? ex : nested;
    }
    
    static class SuspendParams<R> {
        final CompletionStage<R> future;
        final ContextualExecutor contextualExecutor;
        
        SuspendParams(CompletionStage<R> future, ContextualExecutor contextualExecutor) {
            this.future = future;
            this.contextualExecutor = contextualExecutor;
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
                resume(continuation, Either.result(result));
            } else {
                Throwable ex = unrollCompletionException(error);
                if (CloseSignal.INSTANCE == ex) {
                    continuation.terminate();
                } else {
                    resume(continuation, Either.error(ex));
                }
            }            
        }
    }
}
