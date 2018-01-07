/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.tascalate.async.generator.GeneratorDecorators;
import net.tascalate.async.generator.ReadyFirstFuturesGenerator;
import net.tascalate.async.generator.OrderedFuturesGenerator;

public interface Generator<T> extends GeneratorDecorator<T, Generator<T>>, AutoCloseable {
    
    @suspendable CompletionStage<T> next(Object producerParam);
    
    default
    @suspendable CompletionStage<T> next() {
        return next(null);
    }
    
    void close();
    
    default
    Generator<T> raw() {
        return this;
    }
    
    default
    ValuesGenerator<T> values() {
        return as(GeneratorDecorators::values);
    }
    
    default
    PromisesGenerator<T> promises() {
        return as(GeneratorDecorators::promises);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> Generator<T> empty() {
        return (Generator<T>)PackagePrivate.EMPTY_GENERATOR;
    }

    public static <T> Generator<T> of(T readyValue) {
    	return ordered(Stream.of(readyValue));
    }
    
    @SafeVarargs
    public static <T> Generator<T> of(T... readyValues) {
        return ordered(Stream.of(readyValues));
    }
    
    public static <T> Generator<T> of(CompletionStage<T> pendingValue) {
    	return of(Stream.of(pendingValue));
    }
    
    @SafeVarargs
    public static <T> Generator<T> of(CompletionStage<T>... pendingValues) {
        return of(Stream.of(pendingValues));
    }

    public static <T> Generator<T> of(Iterable<CompletionStage<T>> pendingValues) {
        return new OrderedFuturesGenerator<T>(pendingValues.iterator(), pendingValues);
    }
    
    public static <T> Generator<T> of(Stream<CompletionStage<T>> pendingValues) {
        return new OrderedFuturesGenerator<T>(pendingValues.iterator(), pendingValues);
    }
    
    public static <T> Generator<T> ordered(Iterable<T> readyValues) {
        return of(StreamSupport.stream(readyValues.spliterator(), false).map(CompletableFuture::completedFuture));
    }
    
    public static <T> Generator<T> ordered(Stream<T> readyValues) {
        return of(readyValues.map(CompletableFuture::completedFuture));
    }

    @SafeVarargs
    public static <T> Generator<T> readyFirst(CompletionStage<T>... pendingValues) {
        return readyFirst(Stream.of(pendingValues));
    }

    public static <T> Generator<T> readyFirst(Iterable<CompletionStage<T>> pendingValues) {
        return ReadyFirstFuturesGenerator.create(pendingValues);
    }
    
    public static <T> Generator<T> readyFirst(Stream<CompletionStage<T>> pendingValues) {
        return ReadyFirstFuturesGenerator.create(pendingValues);
    }
}
