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
package net.tascalate.async.sequence;

import java.util.NoSuchElementException;

import net.tascalate.async.CustomizableSequence;
import net.tascalate.async.Sequence;
import net.tascalate.async.SequenceIterator;
import net.tascalate.async.SequenceKind;
import net.tascalate.async.suspendable;
import net.tascalate.async.core.AbstractAsyncMethod;
import net.tascalate.async.core.InternalCallContext;

public abstract class PendingValuesSequence<T> implements Sequence<T> {
    
    @Override
    public SequenceKind kind() {
        return this instanceof CustomizableSequence 
               ? SequenceKind.PENDING_VALUES_CUSTOMIZABLE
               : SequenceKind.PENDING_VALUES_REGULAR;
    }

    @Override
    public SequenceIterator<T> iterator() {
        return iterator(true, null);
    }
    
    @Override
    public SequenceIterator<T> iterator(boolean exclusive) {
        return iterator(exclusive, null);
    }
    
    private SequenceIterator<T> iterator(boolean exclusive, AbstractAsyncMethod caller) {
        if (exclusive) {
            
            return new SequenceIterator.Closeable<T>() {
                private AbstractAsyncMethod exactCaller = caller;
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
                    PendingValuesSequence.this.close();
                }
                
                protected @suspendable void advanceIfNecessary() {
                    if (advance) {
                        if (null == exactCaller) {
                            exactCaller = InternalCallContext.asyncMethod();
                        }
                        current = PendingValuesSequence.this.takeNext(exactCaller);
                    }
                    advance = false;
                }

                @Override
                public String toString() {
                    return String.format("ExclusiveSequenceIterator[owner=%s, current=%s]", PendingValuesSequence.this, current);
                }            
            };
        } else {
            return Sequence.super.iterator();
        }
    }
    
    abstract 
    protected @suspendable T takeNext(AbstractAsyncMethod caller);
    
    protected @suspendable T takeNext(Object param, AbstractAsyncMethod caller) {
        throw new UnsupportedOperationException();
    }
}
