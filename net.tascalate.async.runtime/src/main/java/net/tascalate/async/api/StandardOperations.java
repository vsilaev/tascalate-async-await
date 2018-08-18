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
import org.apache.commons.javaflow.extras.ContinuableIterator;
import org.apache.commons.javaflow.extras.ContinuableProducer;
import org.apache.commons.javaflow.extras.Option;

import net.tascalate.async.core.AsyncMethodExecutor;

public final class StandardOperations {
    private StandardOperations() {}
    
    public static <T> ContinuableFunction<CompletionStage<T>, T> readyValues() {
        return new ContinuableFunction<CompletionStage<T>, T>() {
            @Override
            public T apply(CompletionStage<T> future) {
                return AsyncMethodExecutor.await(future);
            }
        };
    }
    
    public static class generator {
        public static <T> Function<Generator<T>, ContinuableIterator<CompletionStage<T>>> promises() {
            return g -> g
                .stream()
                .map(f -> (CompletionStage<T>)f)
                .as(stream.toIterator());
        }
        
        public static <T> Function<Generator<T>, ContinuableIterator<T>> values() {
            return g -> g
                .stream()
                .map$(readyValues())
                .as(stream.toIterator());
        } 
    }
    
    public static class stream {
        
        public static <T> Function<ContinuableProducer<T>, ContinuableIterator<T>> toIterator() {
            return IteratorByProducer::new;
        }
        
        public static <T> Function<ContinuableProducer<? extends CompletionStage<T>>, Generator<T>> toGenerator() {
            return GeneratorByProducer::new;
        }        
    }
    
    private static final class GeneratorByProducer<T> implements Generator<T> {
        private final ContinuableProducer<? extends CompletionStage<T>> producer;
        
        GeneratorByProducer(ContinuableProducer<? extends CompletionStage<T>> producer) {
            this.producer = producer;
        }
        
        @Override
        public CompletionStage<T> next(Object producerParam) {
            if (producerParam != null) {
                throw new UnsupportedOperationException("Converted generators do not support parameters");
            }
            return producer.produce().orElseNull().get();
        }

        @Override
        public void close() {
            producer.close();
        }
        
        @Override
        public String toString() {
            return String.format("%s[producer=%s]", getClass().getSimpleName(), producer);
        }
        
    };
    
   private static final class IteratorByProducer<T> implements ContinuableIterator<T> {
        private final ContinuableProducer<T> delegate;
        
        private boolean advance  = true;
        private Option<T> current;
        
        public IteratorByProducer(ContinuableProducer<T> delegate) {
            this.delegate = delegate;
            current = Option.none();
            advance = true;
        }
        
        @Override
        public boolean hasNext() {
            advanceIfNecessary();
            return current.exists();
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
            current = Option.none();
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
            return String.format("%s[delegate=%s, current=%s]", getClass().getSimpleName(), delegate, current);
        }
    }

}
