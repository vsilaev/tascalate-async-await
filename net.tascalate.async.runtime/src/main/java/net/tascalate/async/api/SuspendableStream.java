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

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.javaflow.extras.ContinuableBiFunction;
import org.apache.commons.javaflow.extras.ContinuableBinaryOperator;
import org.apache.commons.javaflow.extras.ContinuableConsumer;
import org.apache.commons.javaflow.extras.ContinuableFunction;
import org.apache.commons.javaflow.extras.ContinuablePredicate;
import org.apache.commons.javaflow.extras.ContinuableSupplier;

public class SuspendableStream<T> implements AutoCloseable {
    
    public static interface Producer<T> extends AutoCloseable {
        @suspendable Value<T> produce();
        void close();
    }
    protected static final SuspendableStream<Object> EMPTY = new SuspendableStream<>(new Producer<Object>() {
        
        @Override
        public Value<Object> produce() {
            return Value.none();
        }
        
        @Override
        public void close() {}
    });
    
    protected final Producer<T> producer;
    
    public SuspendableStream(Producer<T> producer) {
        this.producer = producer;
    }
    
    @Override
    public void close() {
        producer.close();
    }
    
    protected <U> SuspendableStream<U> nextStage(Producer<U> producer) {
        return new SuspendableStream<>(producer);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> SuspendableStream<T> empty() {
        return (SuspendableStream<T>)EMPTY;
    }
    
    public static <T> SuspendableStream<T> of(T value) {
        return SuspendableStream.repeat(value).take(1);
    }
    
    @SafeVarargs
    public static <T> SuspendableStream<T> of(T... values) {
        return of(Stream.of(values));
    }
    
    public static <T> SuspendableStream<T> of(Iterable<? extends T> values) {
        return of(values.iterator());
    }
    
    public static <T> SuspendableStream<T> of(Stream<? extends T> values) {
        return of(values.iterator());
    }
    
    private static <T> SuspendableStream<T> of(Iterator<? extends T> values) {
        return new SuspendableStream<>(new Producer<T>() {
            @Override
            public Value<T> produce() {
                if (values.hasNext()) {
                    return Value.some(values.next());
                } else {
                    return Value.none();
                }
            }
            
            @Override
            public void close() {}
        });
    }
    
    public static <T> SuspendableStream<T> repeat(T value) {
        return new SuspendableStream<>(new Producer<T>() {
            @Override
            public Value<T> produce() {
                return Value.some(value);
            }
            
            @Override
            public void close() {}
        });
    }
    
    public static <T> SuspendableStream<T> generate(Supplier<? extends T> supplier) {
        return new SuspendableStream<>(new Producer<T>() {
            @Override
            public Value<T> produce() {
                return Value.some(supplier.get());
            }
            
            @Override
            public void close() {}
        });
    }
    
    public static <T> SuspendableStream<T> generateAwaitable(ContinuableSupplier<? extends T> supplier) {
        return new SuspendableStream<>(new Producer<T>() {
            @Override
            public Value<T> produce() {
                return Value.some(supplier.get());
            }
            
            @Override
            public void close() {}
        });
    }
    
    public <R> SuspendableStream<R> map(Function<? super T, ? extends R> mapper) {
        return nextStage(new InnerProducer<R>() {
            @Override
            public Value<R> produce() {
                return producer.produce().map(mapper);
            }
        });
    }
    
    public <R> SuspendableStream<R> mapAwaitable(ContinuableFunction<? super T, ? extends R> mapper) {
        return nextStage(new InnerProducer<R>() {
            @Override
            public Value<R> produce() {
                return producer.produce().map(mapper);
            }
        });
    }

    
    public SuspendableStream<T> filter(Predicate<? super T> predicate) {
        return nextStage(new InnerProducer<T>() {
            @Override
            public Value<T> produce() {
                Value<T> v;
                while ((v = producer.produce()).exist()) {
                    v = v.filter(predicate);
                    if (v.exist()) {
                        return v;
                    }
                }
                return Value.none();
            }
        });
    }
    
    public SuspendableStream<T> filterAwaitable(ContinuablePredicate<? super T> predicate) {
        return nextStage(new InnerProducer<T>() {
            @Override
            public Value<T> produce() {
                Value<T> v;
                while ((v = producer.produce()).exist()) {
                    v = v.filter(predicate);
                    if (v.exist()) {
                        return v;
                    }
                }
                return Value.none();
            }
        });
    }
    
    
    public <R> SuspendableStream<R> flatMap(Function<? super T, ? extends SuspendableStream<R>> mapper) {
        return nextStage(new InnerProducer<R>() {
            Value<? extends SuspendableStream<R>> current = Value.none();
            
            @Override
            public Value<R> produce() {
                Value<R> r = current.flatMap(s -> s.producer.produce());
                if (r.exist()) {
                    return r;
                } else {
                    current.accept(toConsumer(SuspendableStream::close));
                    Value<T> v;
                    while ((v = producer.produce()).exist()) {
                        current = v.map(mapper);
                        r = current.flatMap(s -> s.producer.produce());
                        if (r.exist()) {
                            return r;
                        }
                    }
                    return Value.none();
                }
            }

            @Override
            public void close() {
                try {
                    current.accept(toConsumer(SuspendableStream::close));
                } finally {
                    super.close();
                }
            }
        });
    }
    
    public <R> SuspendableStream<R> flatMapAwaitable(ContinuableFunction<? super T, ? extends SuspendableStream<R>> mapper) {
        return nextStage(new InnerProducer<R>() {
            Value<? extends SuspendableStream<R>> current = Value.none();
            
            @Override
            public Value<R> produce() {
                Value<R> r = current.flatMap(s -> s.producer.produce());
                if (r.exist()) {
                    return r;
                } else {
                    current.accept(toConsumer(SuspendableStream::close));
                    Value<T> v;
                    while ((v = producer.produce()).exist()) {
                        current = v.map(mapper);
                        r = current.flatMap(s -> s.producer.produce());
                        if (r.exist()) {
                            return r;
                        }
                    }
                    return Value.none();
                }
            }

            @Override
            public void close() {
                try {
                    current.accept(toConsumer(SuspendableStream::close));
                } finally {
                    super.close();
                }
            }
        });
    }

    public <U, R> SuspendableStream<R> zip(SuspendableStream<U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {
        return zip(other, zipper, null, null);
    }
    
    public <U, R> SuspendableStream<R> zip(SuspendableStream<U> other, 
                                           BiFunction<? super T, ? super U, ? extends R> zipper,
                                           Supplier<? extends T> onLeftMissing, 
                                           Supplier<? extends U> onRightMissing) {
        
        Supplier<Value<T>> aMissing = toValueSupplier(onLeftMissing); 
        Supplier<Value<U>> bMissing = toValueSupplier(onRightMissing);
        return nextStage(new InnerProducer<R>() {
            @Override
            public Value<R> produce() {
                Value<T> a = producer.produce();
                Value<U> b = other.producer.produce();
                if (a.exist() || b.exist()) {
                    // Try to combine if at least one exist
                    return a.orElse(aMissing).combine(b.orElse(bMissing), zipper);                    
                } else {
                    return Value.none();
                }
            }

            @Override
            public void close() {
                try {
                    other.close();
                } finally {
                    super.close();
                }
            }
        });        
    }
    
    public <U, R> SuspendableStream<R> zipAwaitable(SuspendableStream<U> other, ContinuableBiFunction<? super T, ? super U, ? extends R> zipper) {
        return zipAwaitable(other, zipper, null, null);
    }
    
    public <U, R> SuspendableStream<R> zipAwaitable(SuspendableStream<U> other, 
                                                    ContinuableBiFunction<? super T, ? super U, ? extends R> zipper,
                                                    ContinuableSupplier<? extends T> onLeftMissing,
                                                    ContinuableSupplier<? extends U> onRightMissing) {
        ContinuableSupplier<Value<T>> aMissing = toValueSupplier(onLeftMissing); 
        ContinuableSupplier<Value<U>> bMissing = toValueSupplier(onRightMissing);
        return nextStage(new InnerProducer<R>() {
            @Override
            public Value<R> produce() {
                Value<T> a = producer.produce();
                Value<U> b = other.producer.produce();
                if (a.exist() || b.exist()) {
                    // Try to combine if at least one exist
                    return a.orElse(aMissing).combine(b.orElse(bMissing), zipper);                    
                } else {
                    return Value.none();
                }
            }

            @Override
            public void close() {
                try {
                    other.close();
                } finally {
                    super.close();
                }
            }
        });     
    }
    
    public SuspendableStream<T> peek(Consumer<? super T> action) {
        return nextStage(new InnerProducer<T>() {
            @Override
            public Value<T> produce() {
                Value<T> result = producer.produce();
                result.accept(action);
                return result;
            }
        });
    }
    
    public SuspendableStream<T> peekAwaitable(ContinuableConsumer<? super T> action) {
        return nextStage(new InnerProducer<T>() {
            @Override
            public Value<T> produce() {
                Value<T> result = producer.produce();
                result.accept(action);
                return result;
            }
        });
    }
    
    public SuspendableStream<T> ignoreErrors() {
        return nextStage(new InnerProducer<T>() {
            @Override
            public Value<T> produce() {
                while (true) {
                    try {
                        return producer.produce();
                    } catch (Exception ex) {
                        // Ignore regular exceptions
                    }
                }
            }
        });
    }
    
    public SuspendableStream<T> stopOnError() {
        return nextStage(new InnerProducer<T>() {
            @Override
            public Value<T> produce() {
                try {
                    return producer.produce();
                } catch (Throwable ex) {
                    return Value.none();
                }
            }
        });
    }    
    
    public SuspendableStream<T> recover(T value) {
        return recover(ex -> value);
    }    
    
    public SuspendableStream<T> recover(Function<? super Throwable, ? extends T> recover) {
        return nextStage(new InnerProducer<T>() {
            @Override
            public Value<T> produce() {
                try {
                    return producer.produce();
                } catch (Throwable ex) {
                    return Value.some(recover.apply(ex));
                }
            }
        });
    }
    
    public SuspendableStream<T> recoverAwaitable(ContinuableFunction<? super Throwable, ? extends T> recover) {
        return nextStage(new InnerProducer<T>() {
            @Override
            public Value<T> produce() {
                try {
                    return producer.produce();
                } catch (Throwable ex) {
                    return Value.some(recover.apply(ex));
                }
            }
        });
    }
    
    public SuspendableStream<T> drop(long count) {
        return nextStage(new InnerProducer<T>() {
            long idx = 0;
            
            @Override
            public Value<T> produce() {
                for (; idx < count; idx++) {
                    // Forward
                    Value<T> v = producer.produce();
                    if (!v.exist()) {
                        idx = count;
                        return Value.none();
                    }
                }
                return producer.produce();
            }
        });
    }
    
    public SuspendableStream<T> take(long maxSize) {
        return nextStage(new InnerProducer<T>() {
            long idx = 0;
            
            @Override
            public Value<T> produce() {
                if (idx < maxSize) {
                    idx++;
                    Value<T> v = producer.produce();
                    if (!v.exist()) {
                        idx = maxSize;
                        return Value.none();
                    } else {
                        return v;
                    }
                } else {
                    return Value.none();
                }
            }
        });
    }
    
    public @suspendable void forEach(Consumer<? super T> action) {
        Value<T> v;
        while ((v = producer.produce()).exist()) {
            v.accept(action);
        }
    }
    
    public @suspendable void forEachAwaitable(ContinuableConsumer<? super T> action) {
        Value<T> v;
        while ((v = producer.produce()).exist()) {
            v.accept(action);
        }
    }
    
    public @suspendable Optional<T> reduce(BinaryOperator<T> accumulator) {
        Value<T> result = Value.none();
        Value<T> v;
        while ((v = producer.produce()).exist()) {
            if (result.exist()) {
                result = result.combine(v, accumulator);
            } else {
                result = v;                
            }
        }
        return toOptional(result);
    }
    
    public @suspendable Optional<T> reduceAwaitable(ContinuableBinaryOperator<T> accumulator) {
        Value<T> result = Value.none();
        Value<T> v;
        while ((v = producer.produce()).exist()) {
            if (result.exist()) {
                result = result.combine(v, accumulator);
            } else {
                result = v;                
            }
        }
        return toOptional(result);
    }
    
    public @suspendable <U> U fold(U identity, BiFunction<U, ? super T, U> accumulator) {
        Value<U> result = Value.some(identity);
        Value<T> v;
        while ((v = producer.produce()).exist()) {
            result = result.combine(v, accumulator);
        }
        return result.get();
    }
    
    public @suspendable <U> U foldAwaitable(U identity, ContinuableBiFunction<U, ? super T, U> accumulator) {
        Value<U> result = Value.some(identity);
        Value<T> v;
        while ((v = producer.produce()).exist()) {
            result = result.combine(v, accumulator);
        }
        return result.get();
    }
    
    public <R> R apply(Function<? super SuspendableStream<T>, R> converter) {
        return converter.apply(this);
    }
    
    public <R> R as(Function<? super Producer<T>, R> converter) {
        return converter.apply(producer);
    }

    abstract class InnerProducer<U> implements Producer<U> {
        @Override
        public void close() {
            SuspendableStream.this.close();
        }
    }
    
    @SuppressWarnings("unchecked")
    static <T> Supplier<Value<T>> toValueSupplier(Supplier<? extends T> s) {
        return s != null ? () -> Value.some(s.get()) : (Supplier<Value<T>>)NONE_SUPPLIER_R;
    }
    
    @SuppressWarnings("unchecked")
    static <T> ContinuableSupplier<Value<T>> toValueSupplier(ContinuableSupplier<? extends T> s) {
        return s != null ? () -> Value.some(s.get()) : (ContinuableSupplier<Value<T>>)NONE_SUPPLIER_C;
    }
    
    static <T> Optional<T> toOptional(Value<T> maybeValue) {
        return maybeValue.exist() ? Optional.ofNullable( maybeValue.get() ) : Optional.empty();
    }
    
    static <T> Consumer<T> toConsumer(Consumer<T> consumer) {
        return consumer;
    }
    
    private static final Supplier<?> NONE_SUPPLIER_R = () -> Value.none();
    private static final ContinuableSupplier<?> NONE_SUPPLIER_C = () -> Value.none();
}
