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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.tascalate.async.core.AsyncMethodExecutor;
import net.tascalate.async.sequence.FutureCompletionSequence;

import net.tascalate.javaflow.SuspendableIterator;
import net.tascalate.javaflow.SuspendableStream;
import net.tascalate.javaflow.function.SuspendableFunction;

public interface AsyncGenerator<T> extends CustomizableSequence<CompletionStage<T>> { 
    

    public static final class Sink<T> extends AsyncGeneratorSinkBase<T> {
        Sink(long batchSize, Scheduler scheduler) {
            super(batchSize, scheduler);
        }
    }
    
    public static final class Source<T> extends AsyncGeneratorSourceBase<T> {
        Source(Sequence<? extends CompletionStage<? extends T>> sequence, Scheduler scheduler, Consumer<? super T> itemProcessor) {
            super(sequence, scheduler, itemProcessor);
        }
        
        @Override
        Source<T> start() {
            super.start();
            return this;
        }
    }
    
    default SuspendableStream<T> valuesStream() {
        return stream().map$(awaitValue());
    }     
    
    default SuspendableIterator<T> valuesIterator() {
        // Optimized version instead of [to-producer].stream().map$(await).iterator()
        // to minimize call stack with suspendable methods
        SuspendableIterator<CompletionStage<T>> original = iterator();
        return new SuspendableIterator<T>() {
            @Override
            public T next() {
                CompletionStage<T> future = original.next();
                return AsyncMethodExecutor.await( future );
            }

            @Override
            public boolean hasNext() {
                return original.hasNext();
            }

            @Override
            public void close() {
                original.close();
            }

            @Override
            public String toString() {
                return String.format("%s-ValuesIterator[owner=%s]", getClass().getSimpleName(), AsyncGenerator.this);
            }            
        };
    }
    
    abstract Scheduler scheduler();
    
    default ConcurrentGenerator<T> concurrent() {
        return concurrent(this, this.scheduler());
    }
    
    default AsyncGenerator.Source<T> lazyFetch(Consumer<? super T> itemProcessor) {
        return lazyFetch(scheduler(), itemProcessor);
    }
    
    default AsyncGenerator.Source<T> lazyFetch(Scheduler scheduler, Consumer<? super T> itemProcessor) {
        return lazyFetch(this,  scheduler, itemProcessor);
    }

    @SuppressWarnings("unchecked")
    public static <T> AsyncYield<T> start() {
        return (AsyncYield<T>)AsyncYield.INSTANCE;
    }
    
    @SuppressWarnings("unchecked")
    public static <T> AsyncYield<T> start(Class<T> itemType) {
        return (AsyncYield<T>)AsyncYield.INSTANCE;
    }
    
    public static <T> Sequence<CompletionStage<T>> from(T readyValue) {
        return from(Stream.of(readyValue));
    }
    
    @SafeVarargs
    public static <T> Sequence<CompletionStage<T>> from(T... readyValues) {
        return from(Stream.of(readyValues));
    }
    
    public static <T> Sequence<CompletionStage<T>> from(Iterable<? extends T> readyValues) {
        return from(StreamSupport.stream(readyValues.spliterator(), false));
    }
    
    public static <T> Sequence<CompletionStage<T>> from(Stream<? extends T> readyValues) {
        return Sequence.of(readyValues.map(CompletableFuture::completedFuture));
    }
    
    @SafeVarargs
    public static <T, F extends CompletionStage<T>> Sequence<F> readyFirst(F... pendingValues) {
        return readyFirst(Stream.of(pendingValues));
    }

    public static <T, F extends CompletionStage<T>> Sequence<F> readyFirst(Iterable<? extends F> pendingValues) {
        return readyFirst(pendingValues, -1);
    } 
    
    public static <T, F extends CompletionStage<T>> Sequence<F> readyFirst(Iterable<? extends F> pendingValues, int chunkSize) {
        return FutureCompletionSequence.create(pendingValues, chunkSize);
    }
    
    public static <T, F extends CompletionStage<T>> Sequence<F> readyFirst(Stream<? extends F> pendingValues) {
        return readyFirst(pendingValues, -1);
    }
    
    public static <T, F extends CompletionStage<T>> Sequence<F> readyFirst(Stream<? extends F> pendingValues, int chunkSize) {
        return FutureCompletionSequence.create(pendingValues, chunkSize);
    }
    
    @SuppressWarnings("resource")
    public static <T> ConcurrentGenerator<T> concurrent(Sequence<? extends CompletionStage<? extends T>> promises, Scheduler scheduler) {
        return new ConcurrentGenerator<>(promises, scheduler).start();
    }

    public static <T> Source<T> lazyFetch(Sequence<? extends CompletionStage<? extends T>> promises, Scheduler scheduler, Consumer<? super T> itemProcessor) {
        return new Source<>(promises, scheduler, itemProcessor).start();
    }
    
    public static <T> AsyncGenerator<T> lazyEmit(Scheduler scheduler, Consumer<? super Sink<T>> subcriber) {
        return lazyEmit(scheduler, 1L, subcriber);
    }
    
    public static <T> AsyncGenerator<T> lazyEmit(Scheduler scheduler, long batchSize, Consumer<? super Sink<T>> subcriber) {
        Sink<T> emitter = new Sink<>(batchSize, scheduler);
        subcriber.accept(emitter);
        return emitter.start();
    }
    
    public static <T> AsyncGenerator<T> emptyOn(Scheduler scheduler) {
        return new AsyncGenerator<T>() {

            @Override
            public CompletionStage<T> next(Object param) {
                return null;
            }

            @Override
            public CompletionStage<T> next() {
                return null;
            }

            @Override
            public void close() {
            }

            @Override
            public Scheduler scheduler() {
                return scheduler;
            }
            
        };
    }
    
    public static <T> SuspendableFunction<CompletionStage<T>, T> awaitValue() {
        return new SuspendableFunction<CompletionStage<T>, T>() {
            @Override
            public T apply(CompletionStage<T> future) {
                return AsyncMethodExecutor.await(future);
            }
        };
    }
}
