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
package net.tascalate.async;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import net.tascalate.async.core.AsyncMethodExecutor;

import net.tascalate.javaflow.util.Option;
import net.tascalate.javaflow.util.SuspendableIterator;
import net.tascalate.javaflow.util.SuspendableProducer;
import net.tascalate.javaflow.util.function.SuspendableFunction;

public final class StandardOperations {
    private StandardOperations() {}
    
    public static <T> SuspendableFunction<CompletionStage<T>, T> readyValues() {
        return new SuspendableFunction<CompletionStage<T>, T>() {
            @Override
            public T apply(CompletionStage<T> future) {
                return AsyncMethodExecutor.await(future);
            }
        };
    }
    
    public static class generator {
        public static <T> Function<Generator<T>, SuspendableIterator<CompletionStage<T>>> promises() {
            return g -> g.stream().iterator();
        }
        
        public static <T> Function<Generator<T>, SuspendableIterator<T>> values() {
            return g -> g
                .stream()
                .map$(readyValues())
                .iterator();
        } 
    }
    
    public static class stream {
        
        public static <T> Function<SuspendableProducer<? extends T>, SuspendableIterator<T>> toIterator() {
            return IteratorByProducer::new;
        }
        
        public static <T, F extends CompletionStage<T>> Function<SuspendableProducer<? extends F>, Sequence<T, F>> toSequence() {
            return SequenceByProducer::new;
        }        
    }
    
    private static final class SequenceByProducer<T, F extends CompletionStage<T>> implements Sequence<T, F> {
        private final SuspendableProducer<? extends F> producer;
        
        SequenceByProducer(SuspendableProducer<? extends F> producer) {
            this.producer = producer;
        }
        
        @Override
        public F next() {
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
    
   private static final class IteratorByProducer<T> implements SuspendableIterator<T> {
        private final SuspendableProducer<? extends T> delegate;
        
        private boolean advance  = true;
        private Option<? extends T> current;
        
        IteratorByProducer(SuspendableProducer<? extends T> delegate) {
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
