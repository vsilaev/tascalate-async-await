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
package net.tascalate.async.generator;

import java.util.Iterator;
import java.util.concurrent.CompletionStage;

import net.tascalate.async.api.Generator;

public class OrderedFuturesGenerator<T> implements Generator<T> {
    
    public static final Generator<?> EMPTY_GENERATOR = new Generator<Object>() {

        @Override
        public CompletionStage<Object> next(Object producerParam) {
            return null;
        }

        @Override
        public CompletionStage<Object> next() {
            return null;
        }

        @Override
        public void close() {

        }
        
        @Override
        public String toString() {
            return "<empty-generator>";
        }
        
    };

    private final Iterator<CompletionStage<T>> delegate;
    private final AutoCloseable closeable;
    
    public OrderedFuturesGenerator(Iterator<CompletionStage<T>> delegate, Object closeable) {
        this.delegate  = delegate;
        this.closeable = asCloseable(closeable);
    }
    
    @Override
    public CompletionStage<T> next(Object producerParam) {
        return delegate.hasNext() ? delegate.next() : null;
    }

    @Override
    public void close() {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }    
    
    @Override
    public String toString() {
        return String.format("<generator{%s}>[delegate=%s]", getClass().getSimpleName(), delegate);
    }
    
    private AutoCloseable asCloseable(Object source) {
        if (source instanceof AutoCloseable) {
    	    return (AutoCloseable)source;
        } else {
            return null;
        }
    }
} 
