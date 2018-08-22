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
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.tascalate.async.sequence.CompletionSequence;
import net.tascalate.async.sequence.OrderedSequence;
import net.tascalate.javaflow.util.Option;
import net.tascalate.javaflow.util.SuspendableProducer;
import net.tascalate.javaflow.util.SuspendableStream;

public interface Sequence<T, F extends CompletionStage<T>> extends AutoCloseable {
    @suspendable F next();
    
    void close();
    
    default <D> D as(Function<? super Sequence<T, F>, ? extends D> decoratorFactory) {
        return decoratorFactory.apply(this);
    }

    default
    SuspendableStream<F> stream() {
        return new SuspendableStream<>(new SuspendableProducer<F>() {
            @Override
            public Option<F> produce() {
                F result = Sequence.this.next();
                return null != result ? Option.some(result) : Option.none();
            }

            @Override
            public void close() {
                Sequence.this.close();
            }
        });
    }
    
    
    @SuppressWarnings("unchecked")
    public static <T, F extends CompletionStage<T>> Sequence<T, F> empty() {
        return (Sequence<T, F>)OrderedSequence.EMPTY_SEQUENCE;
    }

    public static <T> Sequence<T, CompletionStage<T>> of(T readyValue) {
        return of(Stream.of(readyValue));
    }
    
    @SafeVarargs
    public static <T> Sequence<T, CompletionStage<T>> of(T... readyValues) {
        return of(Stream.of(readyValues));
    }
    
    public static <T> Sequence<T, CompletionStage<T>> of(Iterable<T> readyValues) {
        return of(StreamSupport.stream(readyValues.spliterator(), false));
    }
    
    public static <T> Sequence<T, CompletionStage<T>> of(Stream<T> readyValues) {
        return ordered(readyValues.map(CompletableFuture::completedFuture));
    }
    
    public static <T, F extends CompletionStage<T>> Sequence<T, F> of(F pendingValue) {
        return ordered(Stream.of(pendingValue));
    }
    
    @SafeVarargs
    public static <T, F extends CompletionStage<T>> Sequence<T, F> ordered(F... pendingValues) {
        return ordered(Stream.of(pendingValues));
    }

    public static <T, F extends CompletionStage<T>> Sequence<T, F> ordered(Iterable<? extends F> pendingValues) {
        return OrderedSequence.create(pendingValues);
    }
    
    public static <T, F extends CompletionStage<T>> Sequence<T, F> ordered(Stream<? extends F> pendingValues) {
        return OrderedSequence.create(pendingValues);
    }

    @SafeVarargs
    public static <T, F extends CompletionStage<T>> Sequence<T, F> readyFirst(F... pendingValues) {
        return readyFirst(Stream.of(pendingValues));
    }

    public static <T, F extends CompletionStage<T>> Sequence<T, F> readyFirst(Iterable<? extends F> pendingValues) {
        return readyFirst(pendingValues, -1);
    } 
    
    public static <T, F extends CompletionStage<T>> Sequence<T, F> readyFirst(Iterable<? extends F> pendingValues, int chunkSize) {
        return CompletionSequence.create(pendingValues, chunkSize);
    }
    
    public static <T, F extends CompletionStage<T>> Sequence<T, F> readyFirst(Stream<? extends F> pendingValues) {
        return readyFirst(pendingValues, -1);
    }
    
    public static <T, F extends CompletionStage<T>> Sequence<T, F> readyFirst(Stream<? extends F> pendingValues, int chunkSize) {
        return CompletionSequence.create(pendingValues, chunkSize);
    }
}
