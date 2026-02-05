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
package net.tascalate.async;

import java.util.NoSuchElementException;
import java.util.function.Function;

import java.util.stream.Stream;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.channels.OrderedChannel;
import net.tascalate.javaflow.Option;
import net.tascalate.javaflow.SuspendableIterator;
import net.tascalate.javaflow.SuspendableProducer;
import net.tascalate.javaflow.SuspendableStream;

public interface TypedChannel<T> extends AutoCloseable {
    @suspendable T receive();
    
    void close();
    
    default T item() {
        return null;
    }
    
    default <D> D as(Function<? super TypedChannel<T>, ? extends D> decoratorFactory) {
        return decoratorFactory.apply(this);
    }

    default SuspendableStream<T> stream() {
        return new SuspendableStream<>(new SuspendableProducer<T>() {
            @Override
            public Option<T> produce() {
                T result = TypedChannel.this.receive();
                return null != result ? Option.some(result) : Option.none();
            }

            @Override
            public void close() {
                TypedChannel.this.close();
            }
        });
    }
    
    default SuspendableIterator<T> iterator() {
        // Optimized version instead of [to-producer].stream().iterator()
        // to minimize call stack with suspendable methods
        return new SuspendableIterator<T>() {
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

            @Override
            public void close() {
                current = null;
                advance = false;
                TypedChannel.this.close();
            }
            
            protected @continuable void advanceIfNecessary() {
                if (advance) {
                    current = TypedChannel.this.receive();
                }
                advance = false;
            }

            @Override
            public String toString() {
                return String.format("%s-PromisesIterator[owner=%s, current=%s]", getClass().getSimpleName(), TypedChannel.this, current);
            }            
        };
    }
    
    @SuppressWarnings("unchecked")
    public static <T> TypedChannel<T> empty() {
        return (TypedChannel<T>)OrderedChannel.EMPTY;
    }
    
    public static <T> TypedChannel<T> of(T value) {
        return of(Stream.of(value));
    }
    
    @SafeVarargs
    public static <T> TypedChannel<T> of(T... values) {
        return of(Stream.of(values));
    }

    public static <T> TypedChannel<T> of(Iterable<? extends T> values) {
        return OrderedChannel.create(values);
    }
    
    public static <T> TypedChannel<T> of(Stream<? extends T> values) {
        return OrderedChannel.create(values);
    }

    public static <T> Function<SuspendableProducer<? extends T>, TypedChannel<T>> fromStream() {
        final class ByProducer implements TypedChannel<T> {
            private final SuspendableProducer<? extends T> producer;
            
            ByProducer(SuspendableProducer<? extends T> producer) {
                this.producer = producer;
            }
            
            @Override
            public T receive() {
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
        return ByProducer::new;
    }        
}
