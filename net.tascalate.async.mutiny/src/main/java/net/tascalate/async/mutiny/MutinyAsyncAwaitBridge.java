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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiEmitter;
import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.Scheduler;
import net.tascalate.async.Sequence;
import net.tascalate.async.core.CompletionStageHelper;

public final class MutinyAsyncAwaitBridge {
    
    private MutinyAsyncAwaitBridge() {
        
    }
    
    public static <T> Uni<T> uni(Supplier<? extends CompletionStage<T>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return Uni.createFrom().deferred(() -> {
            CompletionStage<T> completionStage =
                Objects.requireNonNull(supplier.get(), "CompletionStage supplier must not return null");

            return uni(completionStage);
        });
    }
    
    public static <T> Uni<T> uni(CompletionStage<T> completionStage) {
        return Uni.createFrom().completionStage(completionStage)
                  .onCancellation().invoke(() -> CompletionStageHelper.cancelCompletionStage(completionStage, true));
    }
    
    public static <T> CompletableFuture<T> promise(Uni<T> uni) {
        return uni.subscribeAsCompletionStage();
    }
    
    public static <T> AsyncGenerator<T> createGenerator(Multi<? extends T> coldMulti, Scheduler asyncAwaitScheduler) {
        return createGenerator(coldMulti, 1L, asyncAwaitScheduler);
    }
    
    public static <T> AsyncGenerator<T> createGenerator(Multi<? extends T> coldMulti, long batchSize, Scheduler asyncAwaitScheduler) {
        return AsyncGenerator.lazyEmit(asyncAwaitScheduler, batchSize, sink -> 
            coldMulti.subscribe().withSubscriber(MutinyCompatibilityBridge.createMultiSubsciber(sink, batchSize))
        );
    }
    
    public static <T> Multi<T> createMulti(AsyncGenerator<? extends T> generator) {
        return createMulti(sink -> generator.lazyFetch(sink::emit), true);
    }
    
    public static <T> Multi<T> createMulti(Supplier<? extends AsyncGenerator<? extends T>> generatorFactory) {
        return createMulti(generatorFactory, false);
    }
    
    public static <T> Multi<T> createMulti(Supplier<? extends AsyncGenerator<? extends T>> generatorFactory, boolean shared) {
        return createMulti(sink -> generatorFactory.get().lazyFetch(sink::emit), shared);
    }
    
    public static <T> Multi<T> createMulti(Sequence<? extends CompletionStage<? extends T>> sequence, Scheduler asyncAwaitScheduler) {
        return createMulti(() -> sequence, asyncAwaitScheduler, true);
    }
    
    public static <T> Multi<T> createMulti(Supplier<? extends Sequence<? extends CompletionStage<? extends T>>> sequenceFactory, Scheduler asyncAwaitScheduler) {
        return createMulti(sequenceFactory, asyncAwaitScheduler, false);
    }
    
    public static <T> Multi<T> createMulti(Supplier<? extends Sequence<? extends CompletionStage<? extends T>>> sequenceFactory, Scheduler asyncAwaitScheduler, boolean shared) {
        return createMulti(sink -> AsyncGenerator.lazyFetch(sequenceFactory.get(), asyncAwaitScheduler, sink::emit), shared);
    }
    
    private static <T> Multi<T> createMulti(Function<MultiEmitter<? super T>, ? extends AsyncGenerator.Source<? extends T>> sourceFactory, boolean shared) {
        if (shared) {
            return createMultiOnce(sourceFactory).broadcast().toAllSubscribers();
        } else {
            return Multi.createFrom().deferred(() ->createMultiOnce(sourceFactory));
        }
    }
    
    private static <T> Multi<T> createMultiOnce(Function<MultiEmitter<? super T>, ? extends AsyncGenerator.Source<? extends T>> sourceFactory) {
        return MultiSource.createMulti(sourceFactory);
    }
}
