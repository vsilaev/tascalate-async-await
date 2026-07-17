/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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

import java.util.NoSuchElementException;

import net.tascalate.async.CustomizableSequence;
import net.tascalate.async.Sequence;
import net.tascalate.async.SequenceIterator;
import net.tascalate.async.suspendable;

public abstract class SuspendableSequence<T> implements Sequence<T> {

    public @suspendable SequenceIterator<T> iterator() {
        return iterator(true, null);
    }
    
    public SequenceIterator<T> iterator(boolean exclusive) {
        return iterator(exclusive, null);
    }
    
    private SequenceIterator<T> iterator(boolean exclusive, AbstractAsyncMethod caller) {
        if (exclusive) {
            AbstractAsyncMethod exactCaller = null != caller ? caller : InternalCallContext.asyncMethod();
            return new SequenceIterator.Closeable<T>() {
                private boolean advance  = true;
                private T current = null;
                
                @Override
                public boolean hasNext() {
                    advanceIfNecessary();
                    return current != null;
                }

                @Override
                public T next() {
                    advanceIfNecessary();
                    if (null == current) {
                        throw new NoSuchElementException();
                    }
                    advance = true;
                    return current;
                }

                public void close() {
                    current = null;
                    advance = false;
                    SuspendableSequence.this.close();
                }
                
                protected @suspendable void advanceIfNecessary() {
                    if (advance) {
                        current = SuspendableSequence.this.next$(exactCaller);
                    }
                    advance = false;
                }

                @Override
                public String toString() {
                    return String.format("ExclusiveSequenceIterator[owner=%s, current=%s]", SuspendableSequence.this, current);
                }            
            };
        } else {
            return Sequence.super.iterator();
        }
    }
    
    abstract 
    protected @suspendable T next$(AbstractAsyncMethod caller);
    
    protected @suspendable T next$(Object param, AbstractAsyncMethod caller) {
        throw new UnsupportedOperationException();
    }
    
    public static @suspendable <T> T next$(Sequence<? extends T> source, AbstractAsyncMethod caller) {
        if (source instanceof SuspendableSequence) {
            @SuppressWarnings("unchecked")
            SuspendableSequence<T> typedSource = (SuspendableSequence<T>)source;
            return typedSource.next$(caller);
        } else {
            return source.next();
        }
    }
    
    public static @suspendable <T> T next$(CustomizableSequence<? extends T> source, Object param, AbstractAsyncMethod caller) {
        if (source instanceof SuspendableSequence) {
            @SuppressWarnings("unchecked")
            SuspendableSequence<T> typedSource = (SuspendableSequence<T>)source;
            return typedSource.next$(param, caller);
        } else {
            return source.next(param);
        }
    }

}
