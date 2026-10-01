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

import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.SequenceIterator;
import net.tascalate.async.AsyncGenerator.Values;

class AsyncValues<T> implements AsyncGenerator.Values<T> {

    private final AsyncGenerator<T> owner;
    
    AsyncValues(AsyncGenerator<T> owner) {
        this.owner = owner;
    }
    
    @Override
    public SequenceIterator<T> iterator() {
        return iterator(true, null);
    }
    
    @Override
    public SequenceIterator<T> iterator(boolean exclusive) {
        return iterator(exclusive, null);
    }

    SequenceIterator<T> iterator(boolean exclusive, AbstractAsyncMethod caller) {
        SequenceIterator<CompletionStage<T>> original = owner.iterator(exclusive);
        return new SequenceIterator.Closeable<T>() {
            
            AbstractAsyncMethod exactCaller = exclusive ? caller : null;
            
            @Override
            public T next() {
                CompletionStage<T> future = original.next();
                if (exactCaller == null && exclusive) {
                    exactCaller = InternalCallContext.asyncMethod();
                }
                return AsyncMethodExecutor.await( future, exactCaller );
            }

            @Override
            public boolean hasNext() {
                return original.hasNext();
            }

            @Override
            public void close() {
                owner.close();
            }

            @Override
            public String toString() {
                return String.format("%s-ValuesIterator[owner=%s]", getClass().getSimpleName(), owner.getClass());
            }            
        };
    }
    
    @Override
    public void close() {
        owner.close();
    }
    
    @Override
    public <D> D as(BiFunction<? super Values<T>, ? super AsyncGenerator<T>, ? extends D> decoratorFactory) {
        return decoratorFactory.apply(this, owner);
    }
}
