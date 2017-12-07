package net.tascalate.async.core;

import java.io.Serializable;
import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;
import org.apache.commons.javaflow.core.StackRecorder;
import org.apache.commons.javaflow.api.Continuation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.async.api.NoActiveAsyncCallException;

/**
 * 
 * @author Valery Silaev
 */
public class AsyncExecutor implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(AsyncExecutor.class);

    private static final AsyncExecutor INSTANCE = new AsyncExecutor();

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
        resume(continuation, null);
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
        Runnable contextualResumer = ctxExecutor.captureContext(originalResumer);
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

        // Let's sleep!
        log.debug("Suspending continuation");
        Object outcome = Continuation.suspend(
            // Save contextualExecutor of the suspending continuation 
            new SuspendParams<>(future, currentExecution().contextualExecutor())
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
                if (CloseSignal.INSTANCE == error) {
                    continuation.terminate();
                } else {
                    resume(continuation, Either.error(error));
                }
            }            
        }
    }
}
