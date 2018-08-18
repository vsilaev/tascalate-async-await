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
package net.tascalate.async.api;

import java.util.NoSuchElementException;

final class SuspendableIteratorImpl<T> implements SuspendableIterator<T> {
    private final SuspendableStream.Producer<T> delegate;
    
    private boolean advance  = true;
    private Value<T> current;
    
    public SuspendableIteratorImpl(SuspendableStream.Producer<T> delegate) {
        this.delegate = delegate;
        advance = true;
    }
    
    @Override
    public boolean hasNext() {
        advanceIfNecessary();
        return !current.isNone();
    }

    @Override
    public T next() {
        advanceIfNecessary();

        if (current.isNone()) {
            throw new NoSuchElementException();
        } else {
            advance = true;
            return current.get();
        }
    }

    @Override
    public void close() {
        current = null;
        advance = false;
        delegate.close();
    }
    
    /*
    @Override
    public SuspendableStream<T> stream() {
        return new SuspendableStream<>(delegate);
    }
    */
    
    protected @suspendable void advanceIfNecessary() {
        if (advance) {
            current = delegate.produce();
        }
        advance = false;
    }

    @Override
    public String toString() {
        return String.format("<generator-decorator{%s}>[delegate=%s, current=%s]", SuspendableIterator.class.getSimpleName(), delegate, current);
    }
}
