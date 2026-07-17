/**
 * Copyright 2015-2025 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.apix;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import net.tascalate.async.Sequence;
import net.tascalate.async.core.AsyncMethodExecutor;
import net.tascalate.javaflow.Option;
import net.tascalate.javaflow.SuspendableProducer;
import net.tascalate.javaflow.SuspendableStream;
import net.tascalate.javaflow.function.SuspendableFunction;

public final class JavaFlowBidge {
    
    private JavaFlowBidge() {
        
    }
    
    public static <T> Function<Sequence<T>, SuspendableStream<T>> stream() {
        return JavaFlowBidge::stream;
    }
    
    public static <T> SuspendableStream<T> stream(Sequence<? extends T> source) {
        return new SuspendableStream<>(new SuspendableProducer<T>() {
            @Override
            public Option<T> produce() {
                T result = source.next();
                return null != result ? Option.some(result) : Option.none();
            }

            @Override
            public void close() {
                source.close();
            }
        });
    }
    
    public static <T> Function<SuspendableProducer<? extends T>, Sequence<T>> fromStream() {
        final class SequenceByProducer implements Sequence<T> {
            private final SuspendableProducer<? extends T> producer;
            
            SequenceByProducer(SuspendableProducer<? extends T> producer) {
                this.producer = producer;
            }
            
            @Override
            public T next() {
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
        return SequenceByProducer::new;
    }      
    
    public static <T> SuspendableFunction<CompletionStage<T>, T> awaitValue() {
        return new SuspendableFunction<CompletionStage<T>, T>() {
            @Override
            public T apply(CompletionStage<T> future) {
                return AsyncMethodExecutor.await(future);
            }
        };
    }
}
