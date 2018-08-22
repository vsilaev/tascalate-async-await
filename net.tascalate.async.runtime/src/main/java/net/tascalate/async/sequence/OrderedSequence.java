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
package net.tascalate.async.sequence;

import java.util.Iterator;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import net.tascalate.async.Sequence;

public class OrderedSequence<T, R extends CompletionStage<T>> implements Sequence<T, R> {
    
    public static final Sequence<Object, CompletionStage<Object>> EMPTY_SEQUENCE = new Sequence<Object, CompletionStage<Object>>() {

        @Override
        public CompletionStage<Object> next() {
            return null;
        }

        @Override
        public void close() {

        }
        
        @Override
        public String toString() {
            return "<empty-sequence>";
        }
        
    };

    private final Iterator<? extends R> delegate;
    
    protected OrderedSequence(Iterator<? extends R> delegate) {
        this.delegate  = delegate;
    }
    
    @Override
    public R next() {
        return delegate.hasNext() ? delegate.next() : null;
    }

    @Override
    public void close() {
    }    
    
    @Override
    public String toString() {
        return String.format("%s[delegate=%s]", getClass().getSimpleName(), delegate);
    }
    
    public static <T, F extends CompletionStage<T>> Sequence<T, F> create(Stream<? extends F> pendingPromises) {
        return create(pendingPromises.iterator());
    }

    public static <T, F extends CompletionStage<T>> Sequence<T, F> create(Iterable<? extends F> pendingPromises) {
        return create(pendingPromises.iterator());
    }
    
    private static <T, F extends CompletionStage<T>> Sequence<T, F> create(Iterator<? extends F> pendingPromises) {
        return new OrderedSequence<>(pendingPromises);
    }
} 
