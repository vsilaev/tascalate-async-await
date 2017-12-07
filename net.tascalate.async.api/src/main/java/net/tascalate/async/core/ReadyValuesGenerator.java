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
package net.tascalate.async.core;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import net.tascalate.async.api.Generator;

public class ReadyValuesGenerator<T> implements Generator<T> {
    
    public final static Generator<?> EMPTY = new Generator<Object>() {

        @Override
        public boolean next(Object producerParam) {
            return false;
        }

        @Override
        public boolean next() {
            return false;
        }

        @Override
        public Object current() {
            throw new NoSuchElementException();
        }

        @Override
        public void close() {

        }
        
    };
    
    final private Iterator<? extends T> readyValues;
    
    private T current = null;
    private boolean hasValue = false;

    public ReadyValuesGenerator(final Stream<? extends T> readyValues) {
        this(readyValues.iterator());
    }
    
    public ReadyValuesGenerator(final Iterable<? extends T> readyValues) {
        this(readyValues.iterator());
    }
    
    protected ReadyValuesGenerator(final Iterator<? extends T> readyValues) {
        this.readyValues = readyValues;
    }

    @Override
    public boolean next(Object producerParam) {
        if (readyValues.hasNext()) {
            current = readyValues.next();
            return hasValue = true;
        } else {
            current = null;
            return hasValue = false;
        }
    }

    @Override
    public T current() {
        if (hasValue) {
            return current;
        } else {
            throw new NoSuchElementException();
        }
    }

    @Override
    public void close() {}
    
};
