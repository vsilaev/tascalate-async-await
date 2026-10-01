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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Subscription;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.Scheduler;
import net.tascalate.async.Sequence;
import net.tascalate.async.core.CompletionStageHelper;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

public final class ReactorAsyncAwaitBridge {
    
    private ReactorAsyncAwaitBridge() {
        
    }
    
    public static <T> Mono<T> mono(CompletionStage<T> completionStage) {
        return Mono.fromCompletionStage(completionStage)
                   .doOnCancel(() -> CompletionStageHelper.cancelCompletionStage(completionStage, true));
    }
    
    public static <T> CompletableFuture<T> promise(Mono<T> mono) {
        return mono.toFuture();
    }
    
    public static <T> AsyncGenerator<T> createGenerator(Flux<? extends T> coldFlux, Scheduler asyncAwaitScheduler) {
        return createGenerator(coldFlux, 1L, asyncAwaitScheduler);
    }
    
    public static <T> AsyncGenerator<T> createGenerator(Flux<? extends T> coldFlux, long batchSize, Scheduler asyncAwaitScheduler) {
        return AsyncGenerator.lazyEmit(asyncAwaitScheduler, batchSize, sink -> {
            coldFlux.subscribe(new BaseSubscriber<T>() {
                @Override
                protected void hookOnSubscribe(Subscription subscription) {
                    sink.subscribe(subscription::request, subscription::cancel);
                }

                @Override
                protected void hookOnNext(T value) {
                    sink.emitNextItem(value);
                }
                
                @Override
                protected void hookOnError(Throwable throwable) {
                    sink.emitError(throwable);
                }

                @Override
                protected void hookOnComplete() {
                    sink.emitCompletion();
                }

                @Override
                protected void hookOnCancel() {
                    // Canceling subscription is just a completion of the generator
                    hookOnComplete();
                }
                
            });
            
        });
    }
    
    public static <T> Flux<T> createFlux(AsyncGenerator<? extends T> generator) {
        return createFlux(sink -> generator.lazyFetch(sink::next), false);
    }
    
    public static <T> Flux<T> createFlux(Supplier<? extends AsyncGenerator<? extends T>> generatorFactory) {
        return createFlux(generatorFactory, true);
    }
    
    public static <T> Flux<T> createFlux(Supplier<? extends AsyncGenerator<? extends T>> generatorFactory, boolean shared) {
        return createFlux(sink -> generatorFactory.get().lazyFetch(sink::next), shared);
    }
    
    public static <T> Flux<T> createFlux(Sequence<? extends CompletionStage<? extends T>> sequence, Scheduler asyncAwaitScheduler) {
        return createFlux(() -> sequence, asyncAwaitScheduler, false);
    }
    
    public static <T> Flux<T> createFlux(Supplier<? extends Sequence<? extends CompletionStage<? extends T>>> sequenceFactory, Scheduler asyncAwaitScheduler) {
        return createFlux(sequenceFactory, asyncAwaitScheduler, true);
    }
    
    public static <T> Flux<T> createFlux(Supplier<? extends Sequence<? extends CompletionStage<? extends T>>> sequenceFactory, Scheduler asyncAwaitScheduler, boolean shared) {
        return createFlux(sink -> AsyncGenerator.lazyFetch(sequenceFactory.get(), asyncAwaitScheduler, sink::next), shared);
    }
    
    private static <T> Flux<T> createFlux(Function<? super FluxSink<T>, ? extends AsyncGenerator.Source<? extends T>> sourceFactory, boolean shared) {
        Flux<T> upstream = Flux.<T>create(sink -> {
            AsyncGenerator.Source<? extends T> source = sourceFactory.apply(sink);
            
            sink.onCancel(() -> source.cancel());
            
            sink.onRequest(count -> {
                boolean requestAll = Long.MAX_VALUE == count;
                if (requestAll) {
                    source.requestAll();
                } else {
                    source.requestNext(count);    
                }
            });

            source.completion().whenComplete((r, e) -> {
                if (null == e) {
                    sink.complete();
                } else {
                    sink.error(e);
                }
            });
        }, FluxSink.OverflowStrategy.ERROR);
        return shared ? upstream.share() : upstream;        
    }
}
