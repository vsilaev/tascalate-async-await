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
package net.tascalate.async.spring.webflux;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.AsyncGeneratorTraversal;
import net.tascalate.async.AsyncResult;
import net.tascalate.async.Scheduler;
import net.tascalate.async.Sequence;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

public final class AsyncAwaitFlux {
    
    private AsyncAwaitFlux() {
        
    }
    
    public static <T> Flux<T> create(Supplier<? extends AsyncGenerator<? extends T>> generatorFactory) {
        return Flux.create(sink -> {
            AsyncGenerator<? extends T> generator = generatorFactory.get(); 
            setupTraversalSink(generator.startTraversal(sink::next), sink, generator.scheduler());
        }, FluxSink.OverflowStrategy.ERROR);
    }
    
    public static <T> Flux<T> create(Supplier<? extends Sequence<? extends CompletionStage<? extends T>>> sequenceFactory, Scheduler asyncAwaitScheduler) {
        return Flux.create(sink -> {
            Sequence<? extends CompletionStage<? extends T>> sequence = sequenceFactory.get();
            setupTraversalSink(AsyncGenerator.startTraversal(sequence, asyncAwaitScheduler, sink::next), sink, asyncAwaitScheduler);
        }, FluxSink.OverflowStrategy.ERROR);
    }
    
    private static <T> void setupTraversalSink(AsyncGeneratorTraversal<? extends T> traversal, FluxSink<T> sink, Scheduler asyncAwaitScheduler) {
        AsyncResult<?> traversalFuture = traversal.result();
        traversalFuture.whenComplete((r, e) -> {
            if (null == e) {
                asyncAwaitScheduler.schedule(sink::complete);
            } else {
                sink.error(e);
            }
        });
        
        sink.onCancel(() -> traversalFuture.cancel(true));
        sink.onRequest(count -> asyncAwaitScheduler.schedule(() -> {
            boolean requestAll = Long.MAX_VALUE == count;
            if (requestAll) {
                traversal.requestAll();
            } else {
                traversal.requestNext(count);    
            }
        }));

    }
}
