/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.tascalate.async.core.AsyncMethodExecutor;
import net.tascalate.async.sequence.CompletionSequence;

import net.tascalate.javaflow.SuspendableIterator;
import net.tascalate.javaflow.SuspendableStream;

public interface AsyncGenerator<T> extends InteractiveSequence<CompletionStage<T>> { 
    
    default SuspendableStream<T> valuesStream() {
        return stream().map$(CallContext.awaitValue());
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

    public static <T> Sequence<CompletionStage<T>> from(T readyValue) {
        return from(Stream.of(readyValue));
    }
    
    @SafeVarargs
    public static <T> Sequence<CompletionStage<T>> from(T... readyValues) {
        return from(Stream.of(readyValues));
    }
    
    public static <T> Sequence<CompletionStage<T>> from(Iterable<T> readyValues) {
        return from(StreamSupport.stream(readyValues.spliterator(), false));
    }
    
    public static <T> Sequence<CompletionStage<T>> from(Stream<T> readyValues) {
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
        return CompletionSequence.create(pendingValues, chunkSize);
    }
    
    public static <T, F extends CompletionStage<T>> Sequence<F> readyFirst(Stream<? extends F> pendingValues) {
        return readyFirst(pendingValues, -1);
    }
    
    public static <T, F extends CompletionStage<T>> Sequence<F> readyFirst(Stream<? extends F> pendingValues, int chunkSize) {
        return CompletionSequence.create(pendingValues, chunkSize);
    }

}
