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

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Function;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;
import net.tascalate.async.AsyncGenerator;

class MultiSource<T> {
    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<MultiSource> PENDING_DEMAND_UPDATER =
            AtomicLongFieldUpdater.newUpdater(MultiSource.class, "pendingDemand");

    private volatile AsyncGenerator.Source<? extends T> source;
    
    // Replaced AtomicLong object with a primitive volatile long
    @SuppressWarnings("unused")
    private volatile long pendingDemand = 0;
    
    private final Function<MultiEmitter<? super T>, ? extends AsyncGenerator.Source<? extends T>> sourceFactory;
    
    private MultiSource(Function<MultiEmitter<? super T>, ? extends AsyncGenerator.Source<? extends T>> sourceFactory) {
        this.sourceFactory = sourceFactory;
    }
    
    static <T> Multi<T> createMulti(Function<MultiEmitter<? super T>, ? extends AsyncGenerator.Source<? extends T>> sourceFactory) {
        MultiSource<T> result = new MultiSource<>(sourceFactory);
        return result.setup()
                     .onRequest().invoke(result::request)
                     .onCancellation().invoke(result::cancel);
    }
    
    private Multi<T> setup() {
        return Multi.createFrom().<T>emitter(sink -> {
            AsyncGenerator.Source<? extends T> source = sourceFactory.apply(sink);
            this.source = source;
            
            processPending(source);
            
            source.completion().whenComplete((r, e) -> {
                if (null == e) {
                    sink.complete();
                } else {
                    sink.fail(e);
                }
            });                
        }, BackPressureStrategy.ERROR);
    }
    
    void request(long count) {
        AsyncGenerator.Source<? extends T> source = this.source;
        if (!requestItems(source, count)) {
            // Source not ready yet — accumulate for replay using the Updater
            PENDING_DEMAND_UPDATER.accumulateAndGet(this, count, (accumulated, incoming) -> {
                if (accumulated < 0) {
                    return accumulated;
                }
                
                if (accumulated == Long.MAX_VALUE || incoming == Long.MAX_VALUE) {
                    return Long.MAX_VALUE;
                }
                
                long sum = accumulated + incoming;
                return sum < 0 ? Long.MAX_VALUE : sum;
            });
            
            processPending(this.source);
        }             
    }
    
    void cancel() {
        AsyncGenerator.Source<? extends T> source = this.source;
        if (null != source) {
            source.cancel();
        } else {
            // Set the sentinel value directly on the field
            PENDING_DEMAND_UPDATER.set(this, -1);
        }
    }
    
    private void processPending(AsyncGenerator.Source<? extends T> source) {
        if (null == source) {
            return;
        }
        
        // Atomically drain the field
        long pending = PENDING_DEMAND_UPDATER.getAndSet(this, 0);
        if (pending > 0) {
            requestItems(source, pending);
        } else if (pending < 0) {
            source.cancel();
        }            
    }
    
    private static <T> boolean requestItems(AsyncGenerator.Source<? extends T> source, long count) {
        if (null == source) {
            return false;
        }
        if (Long.MAX_VALUE == count) {
            source.requestAll();
        } else {
            source.requestNext(count);
        }
        return true;
    }
}
