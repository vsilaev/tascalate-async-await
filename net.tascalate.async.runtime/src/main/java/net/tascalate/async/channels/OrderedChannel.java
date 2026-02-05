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
package net.tascalate.async.channels;

import java.util.Iterator;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import net.tascalate.async.TypedChannel;

public class OrderedChannel<T> implements TypedChannel<T> {
    
    public static final TypedChannel<Object> EMPTY = new TypedChannel<Object>() {

        @Override
        public CompletionStage<Object> receive() {
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

    private final Iterator<? extends T> delegate;
    
    protected OrderedChannel(Iterator<? extends T> delegate) {
        this.delegate  = delegate;
    }
    
    @Override
    public T receive() {
        if (delegate.hasNext()) {
            T result = delegate.next();
            if (null == result) {
                throw new IllegalStateException("Null element returned from the wrapped iterable");
            }
            return result;
        } else {
            return null;
        }
    }

    @Override
    public void close() {
    }    
    
    @Override
    public String toString() {
        return String.format("%s[delegate=%s]", getClass().getSimpleName(), delegate);
    }
    
    public static <T> TypedChannel<T> create(Stream<? extends T> items) {
        return create(items.iterator());
    }

    public static <T> TypedChannel<T> create(Iterable<? extends T> items) {
        return create(items.iterator());
    }
    
    private static <T> TypedChannel<T> create(Iterator<? extends T> items) {
        return new OrderedChannel<>(items);
    }
} 
