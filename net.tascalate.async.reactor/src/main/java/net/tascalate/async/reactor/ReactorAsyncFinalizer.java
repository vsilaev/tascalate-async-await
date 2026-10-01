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
package net.tascalate.async.reactor;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import net.tascalate.async.spi.AsyncFinalizer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactorAsyncFinalizer implements AsyncFinalizer<Mono<?>, Flux<?>> {

    /**
     * Mono version
     */
    @Override
    public Mono<?> finalizeSingle(Mono<?> singleAsynResult, 
                                  boolean cancellationIsError,
                                  BiFunction<Throwable, Boolean, CompletionStage<Void>> cleanup) {
        return 
        Mono.usingWhen(
            Mono.just(Boolean.TRUE),
            $ -> singleAsynResult,
            buildAsyncComplete(cleanup),
            buildAsyncError(true, cleanup),
            buildAsyncCancel(true, cleanup)
        );
    }

    @Override
    public Flux<?> finalizeMultiple(Flux<?> multipleAsynResults, 
                                    boolean cancellationIsError,
                                    BiFunction<Throwable, Boolean, CompletionStage<Void>> cleanup) {

        return Flux.usingWhen(
                Mono.just(Boolean.TRUE),
                $ -> multipleAsynResults,
                buildAsyncComplete(cleanup),
                buildAsyncError(cancellationIsError, cleanup),
                buildAsyncCancel(cancellationIsError, cleanup)
            );
    }

    /**
     * Flux version
     */
    static <T> Flux<T> awaitDestructor(
            Flux<T> result, 
            boolean cancellationIsError,
            BiFunction<Throwable, Boolean, CompletionStage<Void>> destructor) {
            
        return Flux.usingWhen(
            Mono.just(Boolean.TRUE),
            $ -> result,
            buildAsyncComplete(destructor),
            buildAsyncError(cancellationIsError, destructor),
            buildAsyncCancel(cancellationIsError, destructor)
        );
    }
    
    private static Function<Boolean, Publisher<?>> buildAsyncComplete(
            BiFunction<Throwable, Boolean, CompletionStage<Void>> destructor) {
        
        return $ -> Mono.defer(() -> Mono.fromCompletionStage(destructor.apply(null, false)));
        
    }

    private static BiFunction<Boolean, Throwable, Publisher<?>> buildAsyncError(boolean cancellationIsError, 
                                                                                BiFunction<Throwable, Boolean, CompletionStage<Void>> destructor) {
        
        return ($, err) -> {
            boolean isCancellation = err instanceof CancellationException;
            if (isCancellation) {
                Throwable cancelError = cancellationIsError ? err : null;
                return Mono.defer(() -> Mono.fromCompletionStage(destructor.apply(cancelError, cancellationIsError)));
            }
            return Mono.defer(() -> Mono.fromCompletionStage(destructor.apply(err, true)));
        };
    }

    private static Function<Boolean, Publisher<?>> buildAsyncCancel(
            boolean cancellationIsError, 
            BiFunction<Throwable, Boolean, CompletionStage<Void>> destructor) {
        
        return $ -> {
            Throwable cancelError = cancellationIsError ? new CancellationException("Cancelled") : null;
            return Mono.defer(() -> Mono.fromCompletionStage(destructor.apply(cancelError, cancellationIsError)));
        };
    }
}

/*
//Inside your buildAsyncComplete/Error/Cancel helpers:
return Mono.fromCompletionStage(() -> destructor.apply(err, true))
       .timeout(Duration.ofSeconds(5)) // Prevents hanging if destructor stalls
       .onErrorResume(TimeoutException.class, e -> {
           // Log that cleanup timed out, but allow the main pipeline to finish
           return Mono.empty(); 
       }); 
 */
