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
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import net.tascalate.async.core.AsyncMethodExecutor;
import net.tascalate.async.function.SuspendableFunction;

final class ValuesGeneratorImpl<T> implements ValuesGenerator<T> {
    private final Generator<T> delegate;
    private boolean advance;
    private CompletionStage<T> current;
    
    public ValuesGeneratorImpl(Generator<T> delegate) {
        this.delegate = delegate;
        advance = true;
    }
    
    @Override
    public Generator<T> raw() {
        return delegate.raw();
    }
    
    @Override
    public boolean hasNext() {
        advanceIfNecessary();
        return current != null;
    }

    @Override
    public T next() {
        advanceIfNecessary();

        if (current == null)
            throw new NoSuchElementException();

        final T result = AsyncMethodExecutor.await(current);
        advance = true;

        return result;
    }

    @Override
    public void close() {
        current = null;
        advance = false;
        delegate.close();
    }
    
    @Override
    public SuspendableStream<T> stream() {
        return delegate.stream().mapWithSuspendable(
           new SuspendableFunction<CompletionStage<T>, T>() {
               @Override
               public @suspendable T apply(CompletionStage<T> future) {
                   return AsyncMethodExecutor.await(future);
               }
           }
        );
    }
    
    protected @suspendable void advanceIfNecessary() {
        if (advance)
            current = delegate.next();
        advance = false;
    }

    @Override
    public String toString() {
        return String.format("<generator-decorator{%s}>[delegate=%s, current=%s]", ValuesGenerator.class.getSimpleName(), delegate, current);
    }
    
    private static final Function<Generator<Object>, ValuesGenerator<Object>> CONVERTER = ValuesGeneratorImpl::new;
    
    static <T> Function<Generator<T>, ValuesGenerator<T>> toValuesGenerator() {
        @SuppressWarnings("unchecked")
        Function<Generator<T>, ValuesGenerator<T>> result = (Function<Generator<T>, ValuesGenerator<T>>)(Object)CONVERTER;
        return result;
    }
}
