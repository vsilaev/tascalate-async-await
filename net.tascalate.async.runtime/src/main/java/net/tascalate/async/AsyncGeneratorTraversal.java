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
package net.tascalate.async;

import static net.tascalate.async.CallContext.async;
import static net.tascalate.async.CallContext.await;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public final class AsyncGeneratorTraversal<T> {
    private final Sequence<? extends CompletionStage<? extends T>> sequence;
    private final Consumer<? super T> itemProcessor;

    private final BlockingQueue<CompletableFuture<Long>> requests = new LinkedBlockingQueue<>();
    
    AsyncResult<Long> result;
    
    AsyncGeneratorTraversal(Sequence<? extends CompletionStage<? extends T>> sequence, Consumer<? super T> itemProcessor) {
        this.sequence = sequence;
        this.itemProcessor = itemProcessor;
        // Populate with first pending request
        requests.offer(new CompletableFuture<>());
    }
    
    @async AsyncResult<Long> start(@SchedulerProvider Scheduler scheduler) {
        long total = 0;
        try (Sequence<?> closeable = sequence) {
            while (true) {
                CompletionStage<Long> request = requests.peek();
                
                if (null == request) {
                    throw new IllegalStateException("FluxSink emmitter encountered null request");
                }
                
                long count = await(request);
                long idx = 0;
                while (idx++ < count) {
                    total++;
                    CompletionStage<? extends T> futureItem = sequence.next();
                    if (null != futureItem) {
                        T item = await(futureItem);
                        itemProcessor.accept(item);
                    } else {
                        return async(total);
                    }
                }
            }
        }
    }
    
    public AsyncResult<Long> result() {
        return result;
    }
    
    public boolean requestNext(long count) {
        if (result.isDone()) {
            return false;
        }
        // Add next wait point before confirming current one
        requests.offer(new CompletableFuture<>());
        requests.poll().complete(count);
        return true;
    }
}
