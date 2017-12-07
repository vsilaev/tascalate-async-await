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

import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.core.PendingValuesGenerator;
import net.tascalate.async.core.ReadyValuesGenerator;

public interface Generator<T> extends AutoCloseable {
    
    public @continuable boolean next(Object producerParam);
    
    default 
    public @continuable boolean next() {
        return next(null);
    }
    
    public T current();
    
    public void close();
    
    @SuppressWarnings("unchecked")
    public static <T> Generator<T> empty() {
        return (Generator<T>)ReadyValuesGenerator.EMPTY;
    }

    public static <T> Generator<T> of(T readyValue) {
    	return of(Stream.of(readyValue));
    }
    
    @SafeVarargs
    public static <T> Generator<T> of(T... readyValues) {
        return of(Stream.of(readyValues));
    }
    
    public static <T> Generator<T> of(Stream<? extends T> readyValues) {
        return new ReadyValuesGenerator<>(readyValues);
    }
    
    public static <T> Generator<T> of(Iterable<? extends T> readyValues) {
        return new ReadyValuesGenerator<>(readyValues);
    }

    public static <T> Generator<T> of(CompletionStage<T> pendingValue) {
    	return ofUnordered(Stream.of(pendingValue));
    }
    
    @SafeVarargs
    public static <T> Generator<T> ofUnordered(CompletionStage<T>... pendingValues) {
        return ofUnordered(Stream.of(pendingValues));
    }

    public static <T> Generator<T> ofUnordered(Stream<CompletionStage<T>> pendingValues) {
        return PendingValuesGenerator.create(pendingValues);
    }
    
    public static <T> Generator<T> ofUnordered(Iterable<CompletionStage<T>> pendingValues) {
        return PendingValuesGenerator.create(pendingValues);
    }
}
