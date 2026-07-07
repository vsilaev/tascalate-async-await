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
package net.tascalate.async.spring;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BiFunction;

import net.tascalate.async.core.CompletionStageHelper;
import net.tascalate.async.core.RestrictedCompletableFuture;

class FinalizerFuture <T> extends RestrictedCompletableFuture<T> {
    private static final AtomicIntegerFieldUpdater<FinalizerFuture<?>> WAS_CANCELLED_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(cast(FinalizerFuture.class), "wasCancelled");
    
    private final CompletionStage<?> cancellationTarget;
    private volatile int wasCancelled = 0;
    
    private FinalizerFuture(CompletionStage<?> cancellationTarget) {
        this.cancellationTarget = cancellationTarget;
    }
    
    boolean delayedCancel(boolean mayInterruptIfRunning) {
        if (wasCancelled > 0) {
            return super.cancel(mayInterruptIfRunning);
        } else {
            return false;
        }
    }
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (WAS_CANCELLED_UPDATER.compareAndSet(this,  0,  1)) {
            CompletionStageHelper.cancelCompletionStage(cancellationTarget, mayInterruptIfRunning);
            return true;
        } else {
            return false;
        }
    }
    
    static <T> CompletionStage<T> awaitDestructor(CompletionStage<T> result, boolean cancellationIsError, 
                                                   BiFunction<Throwable, Boolean, CompletionStage<Void>> destructor) {
        FinalizerFuture<T> alt = new FinalizerFuture<>(result);
        result.handle((r, e) -> Outcome.create(r, e, cancellationIsError))
              .thenCompose(o -> o.composeWith(destructor))
              .whenComplete((r, e) -> {
                  if (alt.delayedCancel(true)) {
                      // Cancelled
                  } else if (null == e) {
                      CompletionStageHelper.completeSuccess(alt, r);
                  } else {
                      CompletionStageHelper.completeFailure(alt, e);
                  }
        });
        return alt;
    }
    
    static abstract class Outcome<T> {
        abstract CompletionStage<T> composeWith(BiFunction<Throwable, Boolean, CompletionStage<Void>> destructor);
        
        static <T> Outcome<T> create(T result, Throwable error, boolean cancellationIsError) {
            if (null == error) {
                return new Success<>(result);
            } else if (!cancellationIsError && error instanceof CancellationException) {
                return new Cancelation<>((CancellationException)error);
            } else {
                return new Failure<>(error);
            }
        }
        
        CompletionStage<T> error(Throwable error) {
            CompletableFuture<T> result = new CompletableFuture<>();
            result.completeExceptionally(error);
            return result;
        }

        static class Cancelation<T> extends Outcome<T> {
            private final CancellationException error;
            
            Cancelation(CancellationException error) {
                this.error = error;
            }
            
            CompletionStage<T> composeWith(BiFunction<Throwable, Boolean, CompletionStage<Void>> destructor) {
                return destructor.apply(error, false).thenApply($ -> null /* Apply is never called */);
            }
        }
        
        static class Success<T> extends Outcome<T> {
            private final T result;
            Success(T result) {
                this.result = result;
            }
            
            CompletionStage<T> composeWith(BiFunction<Throwable, Boolean, CompletionStage<Void>> destructor) {
                return destructor.apply(null, false).thenCompose($ -> CompletableFuture.completedFuture(result));
            }
        }
        
        static class Failure<T> extends Outcome<T> {
            private final Throwable error;
            
            Failure(Throwable error) {
                this.error = error;
            }
            
            CompletionStage<T> composeWith(BiFunction<Throwable, Boolean, CompletionStage<Void>> destructor) {
                return destructor.apply(error, true).thenApply($ -> null /* Apply is never called */);
            }
        }

    }
    
    @SuppressWarnings("unchecked")
    private static <T> Class<T> cast(Class<?> clazz) {
        return (Class<T>)clazz;
    }
}
