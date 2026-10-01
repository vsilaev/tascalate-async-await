/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.mutiny;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import net.tascalate.async.spi.AsyncFinalizer;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

public class MutinyAsyncFinalizer implements AsyncFinalizer<Uni<?>, Multi<?>>{

    private static final Object CLEANUP_DONE = new Object();

    /**
     * Uni version.
     *
     * The cleanup is executed when the original Uni terminates.
     * The downstream receives the original item or failure only after
     * the cleanup CompletionStage has completed.
     *
     * If the cleanup itself fails:
     * - original success + cleanup failure -- downstream receives cleanup failure
     * - original failure + cleanup failure -- downstream receives original failure
     *   with cleanup failure added as suppressed
     */
    @Override
    public Uni<?> finalizeSingle(Uni<?> result,
                                boolean cancellationIsError,
                                BiFunction<Throwable, Boolean, CompletionStage<Void>> cleanup) {

        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(cleanup, "cleanup must not be null");

        return result.onTermination().call(
            (value, failure, cancelled) -> cleanup(failure, cancelled, cancellationIsError, cleanup)
        );
    }

    /**
     * Multi version.
     *
     * Items emitted by the source Multi are passed downstream immediately.
     * The terminal signal, completion or failure, is delayed until the
     * cleanup CompletionStage has completed.
     */
    @Override
    public Multi<?> finalizeMultiple(Multi<?> result,
                                     boolean cancellationIsError,
                                     BiFunction<Throwable, Boolean, CompletionStage<Void>> cleanup) {

        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(cleanup, "cleanup must not be null");

        return result.onTermination().call(
            (failure, cancelled) -> cleanup(failure, cancelled, cancellationIsError, cleanup)
        );
    }

    private static Uni<Object> cleanup(Throwable failure,
                                       Boolean cancelled,
                                       boolean cancellationIsError,
                                       BiFunction<Throwable, Boolean, CompletionStage<Void>> cleanup) {

        final boolean isCancelled = cancelled != null && cancelled;
        final Throwable effectiveFailure = failure == null ? null : unwrap(failure);

        final Throwable errorToPass;
        final boolean passError;

        if (effectiveFailure != null) {
            if (effectiveFailure instanceof CancellationException) {
                errorToPass = cancellationIsError ? effectiveFailure : null;
                passError = cancellationIsError;
            } else {
                errorToPass = effectiveFailure;
                passError = true;
            }
        } else if (isCancelled) {
            errorToPass = cancellationIsError ? new CancellationException("Cancelled") : null;
            passError = cancellationIsError;
        } else {
            errorToPass = null;
            passError = false;
        }

        return 
        Uni.createFrom()
            .deferred(() -> {
                CompletionStage<Void> cs;
                try {
                    cs = cleanup.apply(errorToPass, passError);
                } catch (Throwable t) {
                    return Uni.createFrom().failure(t);
                }

                if (cs == null) {
                    return Uni.createFrom().failure(
                        new NullPointerException("Destructor returned null CompletionStage")
                    );
                }

                /*
                 * The cleanup item itself is irrelevant.
                 * onTermination().call only cares whether the returned Uni
                 * completes successfully or fails.
                 *
                 * We map to a non-null marker object to avoid any
                 * Uni<Void>/null-item sensitivity across versions.
                 */
                return Uni.createFrom()
                          .completionStage(cs)
                          .onItem()
                          .transform(ignored -> CLEANUP_DONE);
            })
            .onFailure().recoverWithUni(cleanupFailure -> {
                Throwable finalFailure;

                if (effectiveFailure == null || isCancelled) {
                    finalFailure = unwrap(cleanupFailure);
                } else {
                    finalFailure = combine(effectiveFailure, unwrap(cleanupFailure));
                }

                return Uni.createFrom().failure(finalFailure);
            });
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;

        while (current instanceof CompletionException
                && current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }

        return current;
    }

    private static Throwable combine(Throwable original, Throwable cleanupFailure) {
        if (original == null) {
            return cleanupFailure;
        }

        if (cleanupFailure == null) {
            return original;
        }

        if (original == cleanupFailure) {
            return original;
        }

        try {
            original.addSuppressed(cleanupFailure);
        } catch (Exception ignored) {
            // Very defensive. Should normally not happen.
        }

        return original;
    }
}