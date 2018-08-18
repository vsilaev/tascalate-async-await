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

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.apache.commons.javaflow.extras.ContinuableFunction;

import net.tascalate.async.core.AsyncMethodExecutor;

public final class Converters {
    private Converters() {}
    
    public static <T> ContinuableFunction<CompletionStage<T>, T> readyValues() {
        return new ContinuableFunction<CompletionStage<T>, T>() {
            @Override
            public T apply(CompletionStage<T> future) {
                return AsyncMethodExecutor.await(future);
            }
        };
    }
    
    public static <T> Function<SuspendableStream.Producer<T>, SuspendableIterator<T>> suspendableIterator() {
        return IteratorByProducer::new;
    }
    
    public static <T> Function<SuspendableStream.Producer<? extends CompletionStage<T>>, Generator<T>> generator() {
        return GeneratorByProducer::new;
    }
    
    private static final class GeneratorByProducer<T> implements Generator<T> {
        private final SuspendableStream.Producer<? extends CompletionStage<T>> producer;
        
        GeneratorByProducer(SuspendableStream.Producer<? extends CompletionStage<T>> producer) {
            this.producer = producer;
        }
        
        @Override
        public CompletionStage<T> next(Object producerParam) {
            if (producerParam != null) {
                throw new UnsupportedOperationException("Converted generators do not support parameters");
            }
            return producer.produce()
                       .orElse(Value.useNull())
                       .get();
        }

        @Override
        public void close() {
            producer.close();
        }
        
    };
    
   private static final class IteratorByProducer<T> implements SuspendableIterator<T> {
        private final SuspendableStream.Producer<T> delegate;
        
        private boolean advance  = true;
        private Value<T> current;
        
        public IteratorByProducer(SuspendableStream.Producer<T> delegate) {
            this.delegate = delegate;
            current = Value.none();
            advance = true;
        }
        
        @Override
        public boolean hasNext() {
            advanceIfNecessary();
            return current.exist();
        }

        @Override
        public T next() {
            advanceIfNecessary();
            T result = current.get();
            advance = true;
            return result;
        }

        @Override
        public void close() {
            current = Value.none();
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

}
