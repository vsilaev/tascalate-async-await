package net.tascalate.async.core;

import java.io.Serializable;
import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;
import org.apache.commons.javaflow.api.Continuation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 
 * @author Valery Silaev
 */
public class AsyncExecutor implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(AsyncExecutor.class);

    private static final AsyncExecutor INSTANCE = new AsyncExecutor();

    /**
     * Execute the {@link Runnable}.
     */
    public static void execute(final Runnable runnable) {
        INSTANCE.executeTask(runnable);
    }

    /**
     * Execute the {@link Continuation} and start {@link Continuator} when the
     * {@link Continuation} suspends.
     */
    protected void executeTask(final Runnable runnable) {
        // Create the initial Continuation
        log.debug("Starting suspended Continuation");
        final Continuation continuation = Continuation.startSuspendedWith(runnable);
        // Start it
        resume(continuation, null);
    }

    /**
     * Continue the {@link Continuation} and start {@link Continuator} when the
     * {@link Continuation} suspends.
     */
    protected void resume(final Continuation initialContinuation, final Object context) {
        // Continue Continuation
        log.debug("Continueing continuation");
        final Continuation newContinuation = initialContinuation.resume(context);
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

    protected <R, E extends Throwable> void setupContinuation(final Continuation continuation) {
        @SuppressWarnings("unchecked")
        final CompletionStage<R> future = (CompletionStage<R>) continuation.value();

        // Setup future and give it a chance to continue the Continuation
        try {
            future.whenComplete((result, error) -> {
                if (error == null) {
                    resume(continuation, Either.result(result));
                } else {
                    if (CloseSignal.INSTANCE == error) {
                        continuation.terminate();
                    } else {
                        resume(continuation, Either.error(error));
                    }
                }
            });
        } catch (final Throwable error) {
            resume(continuation, Either.error(error));
        }
    }

    /**
     */
    public @continuable static <R, E extends Throwable> R await(final CompletionStage<R> future) throws E {
        return INSTANCE.awaitTask(future);
    }

    /**
     */
    protected @continuable <R, E extends Throwable> R awaitTask(final CompletionStage<R> future) throws E {
        // Blocking is available - resume() method is being called

        // Let's sleep!
        log.debug("Suspending continuation");
        final Object outcome = Continuation.suspend(future);
        log.debug("Continuation continued");

        if (outcome instanceof Either) {
            // Unwrap and return value
            @SuppressWarnings("unchecked")
            final Either<R, E> either = (Either<R, E>) outcome;
            return either.done();
        } else {
            // Illegal wake-up
            throw new NoActiveAsyncCallException(
                "Continuation was continued incorrectly - are your classes instrumented for javaflow?"
            );
        }
    }
}
