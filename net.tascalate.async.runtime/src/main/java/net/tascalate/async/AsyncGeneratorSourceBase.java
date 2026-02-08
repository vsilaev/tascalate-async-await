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

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import net.tascalate.async.core.AsyncMethodExecutor;
import net.tascalate.async.core.AsyncTaskMethod;

abstract class AsyncGeneratorSourceBase<T> {
    private final Sequence<? extends CompletionStage<? extends T>> sequence;
    private final Consumer<? super T> itemProcessor;

    private final AwaitableQueue<Counter> requests = new AwaitableQueue<>();
    
    private AsyncResult<Long> completion;
    
    AsyncGeneratorSourceBase(Sequence<? extends CompletionStage<? extends T>> sequence, Consumer<? super T> itemProcessor) {
        this.sequence = sequence;
        this.itemProcessor = itemProcessor;
    }
    
    AsyncGeneratorSourceBase<T> start(Scheduler scheduler) {
        completion = doStart(scheduler);
        return this;
    }
    
    private AsyncResult<Long> doStart(Scheduler scheduler) {
        Scheduler resolvedScheduler = AsyncMethodExecutor.currentScheduler(scheduler, this, MethodHandles.lookup());
        AsyncTaskMethod<Long> method = new AsyncTaskMethod<Long>(resolvedScheduler) {
            @Override
            protected @suspendable void doRun() {
                long total = 0;
                try (Sequence<?> closeable = sequence) {
                    while (true) {
                        requests.await();
                        
                        Counter counter;
                        while ((counter = requests.poll()) != null) {
                            while (counter.next()) {
                                CompletionStage<? extends T> futureItem = sequence.next();
                                if (null != futureItem) {
                                    if (total < Long.MAX_VALUE) {
                                        total++;
                                    }
                                    
                                    T item = AsyncMethodExecutor.await(futureItem);
                                    itemProcessor.accept(item);
                                } else {
                                    success(total);
                                    return;
                                }
                            }
                        }
                    }
                }                
            }
            
        };
        AsyncMethodExecutor.execute(method);
        @SuppressWarnings("unchecked")
        AsyncResult<Long> result = (AsyncResult<Long>)method.future;
        return result;
    }
    
    public CompletionStage<Long> completion() {
        return completion;
    }
    
    public boolean cancel() {
        return completion.cancel(true);
    }
    
    public Scheduler scheduler() {
        return completion.scheduler();
    }
    
    public boolean requestAll() {
        if (completion.isDone()) {
            return false;
        }
        requests.offer(INFINITE_COUNTER);
        return true;
    }
    
    public boolean requestNext(long count) {
        if (completion.isDone()) {
            return false;
        }
        requests.offer(new FiniteCounter(count));
        return true;
    }
    
    abstract static class Counter {
        abstract boolean next();
    }
    
    private static final Counter INFINITE_COUNTER = new Counter() {
        @Override
        boolean next() {
            return true;
        }
    };
    
    static final class FiniteCounter extends Counter {
        private final long count;
        private long idx = 0;
        
        FiniteCounter(long count) {
            this.count = count;
        }
        
        @Override
        boolean next() {
            return idx++ < count;
        }

    }
}
