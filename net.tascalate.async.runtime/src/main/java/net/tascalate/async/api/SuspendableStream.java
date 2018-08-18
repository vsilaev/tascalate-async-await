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
import java.util.NoSuchElementException;
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
        @suspendable T produce() throws ProducerExhaustedException;
        void close();
        
        public static final ProducerExhaustedException STOP = ProducerExhaustedException.INSTANCE;
    }
    
    public static class ProducerExhaustedException extends NoSuchElementException {
        private static final long serialVersionUID = 1L;

        private ProducerExhaustedException() {}
        
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }

        @Override
        public synchronized Throwable initCause(Throwable ignore) {
            return this;
        }

        @Override
        public void setStackTrace(StackTraceElement[] ignore) {
        }
        
        static final ProducerExhaustedException INSTANCE = new ProducerExhaustedException();
    }
    
    protected static final SuspendableStream<Object> EMPTY = new SuspendableStream<>(new Producer<Object>() {
        
        @Override
        public Object produce() throws ProducerExhaustedException {
            throw STOP;
        }
        
        @Override
        public void close() {}
    });
    
    protected final Producer<T> producer;
    
    public SuspendableStream(Producer<T> producer) {
        this.producer = producer;
    }
    
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
            public T produce() {
                if (values.hasNext()) {
                    return values.next();
                } else {
                    throw STOP;
                }
            }
            
            @Override
            public void close() {}
        });
    }
    
    public static <T> SuspendableStream<T> repeat(T value) {
        return new SuspendableStream<>(new Producer<T>() {
            @Override
            public T produce() {
                return value;
            }
            
            @Override
            public void close() {}
        });
    }
    
    public static <T> SuspendableStream<T> generate(Supplier<? extends T> supplier) {
        return new SuspendableStream<>(new Producer<T>() {
            @Override
            public T produce() {
                return supplier.get();
            }
            
            @Override
            public void close() {}
        });
    }
    
    public static <T> SuspendableStream<T> generateAwaitable(ContinuableSupplier<? extends T> supplier) {
        return new SuspendableStream<>(new Producer<T>() {
            @Override
            public T produce() {
                return supplier.get();
            }
            
            @Override
            public void close() {}
        });
    }
    
    public <R> SuspendableStream<R> map(Function<? super T, ? extends R> mapper) {
        return nextStage(new InnerProducer<R>() {
            @Override
            public R produce() {
                return mapper.apply(producer.produce());
            }
        });
    }
    
    public <R> SuspendableStream<R> mapAwaitable(ContinuableFunction<? super T, ? extends R> mapper) {
        return nextStage(new InnerProducer<R>() {
            @Override
            public R produce() {
                return mapper.apply(producer.produce());
            }
        });
    }

    
    public SuspendableStream<T> filter(Predicate<? super T> predicate) {
        return nextStage(new InnerProducer<T>() {
            @Override
            public T produce() {
                while (true) {
                    T original = producer.produce();
                    if (predicate.test(original)) {
                        return original;
                    }
                }
            }
        });
    }
    
    public SuspendableStream<T> filterAwaitable(ContinuablePredicate<? super T> predicate) {
        return nextStage(new InnerProducer<T>() {
            @Override
            public T produce() {
                while (true) {
                    T original = producer.produce();
                    if (predicate.test(original)) {
                        return original;
                    }
                }
            }
        });
    }
    
    
    public <R> SuspendableStream<R> flatMap(Function<? super T, ? extends SuspendableStream<? extends R>> mapper) {
        return nextStage(new InnerProducer<R>() {
            SuspendableStream<? extends R> current = null;
            
            @Override
            public R produce() {
                if (null != current) {
                    try {
                        return current.producer.produce();
                    } catch (ProducerExhaustedException ex) {
                        current.close();
                        current = null;
                    }
                }

                while (null == current) {
                    // If ProducerExhaustedException is thrown then delegate is over
                    current = mapper.apply(producer.produce());
                    try {
                        return current.producer.produce();
                    } catch (ProducerExhaustedException ex) {
                        current.close();
                        current = null;
                    }
                }
                throw STOP;
            }

            @Override
            public void close() {
                try {
                    if (null != current) {
                        current.close();
                    }
                } finally {
                    super.close();
                }
            }
        });
    }
    
    public <R> SuspendableStream<R> flatMapAwaitable(ContinuableFunction<? super T, ? extends SuspendableStream<? extends R>> mapper) {
        return nextStage(new InnerProducer<R>() {
            SuspendableStream<? extends R> current = null;
            
            @Override
            public R produce() {
                if (null != current) {
                    try {
                        return current.producer.produce();
                    } catch (ProducerExhaustedException ex) {
                        current.close();
                        current = null;
                    }
                }

                while (null == current) {
                    // If ProducerExhaustedException is thrown then delegate is over
                    current = mapper.apply(producer.produce());
                    try {
                        return current.producer.produce();
                    } catch (ProducerExhaustedException ex) {
                        current.close();
                        current = null;
                    }
                }
                throw STOP;
            }

            @Override   
            public void close() {
                try {
                    if (null != current) {
                        current.close();
                    }
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
                                           Supplier<? extends T> provideMissingLeft, 
                                           Supplier<? extends U> provideMissingRight) {
        return nextStage(new InnerProducer<R>() {
            @Override
            public R produce() {
                T a;
                boolean aMissing = false;
                try {
                    a = producer.produce();
                } catch (ProducerExhaustedException ex) {
                    a = null;
                    aMissing = true;
                }
                
                U b;
                boolean bMissing = false;
                try {
                    b = other.producer.produce();
                } catch (ProducerExhaustedException ex) {
                    b = null;
                    bMissing = true;
                }

                if (aMissing && bMissing) {
                    // Both streams are over
                    throw STOP;
                } else {
                    if (aMissing) {
                        if (null != provideMissingLeft) {
                            a = provideMissingLeft.get();
                        } else {
                            throw STOP;
                        }
                    }
                    if (bMissing) {
                        if (null != provideMissingRight) {
                            b = provideMissingRight.get();
                        } else {
                            throw STOP;
                        }
                    }
                    
                    return zipper.apply(a, b);
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
                                                    ContinuableSupplier<? extends T> provideMissingLeft,
                                                    ContinuableSupplier<? extends U> provideMissingRight) {
        return nextStage(new InnerProducer<R>() {
            @Override
            public R produce() {
                T a;
                boolean aMissing = false;
                try {
                    a = producer.produce();
                } catch (ProducerExhaustedException ex) {
                    a = null;
                    aMissing = true;
                }
                
                U b;
                boolean bMissing = false;
                try {
                    b = other.producer.produce();
                } catch (ProducerExhaustedException ex) {
                    b = null;
                    bMissing = true;
                }

                if (aMissing && bMissing) {
                    // Both streams are over
                    throw STOP;
                } else {
                    if (aMissing) {
                        if (null != provideMissingLeft) {
                            a = provideMissingLeft.get();
                        } else {
                            throw STOP;
                        }
                    }
                    if (bMissing) {
                        if (null != provideMissingRight) {
                            b = provideMissingRight.get();
                        } else {
                            throw STOP;
                        }
                    }
                    
                    return zipper.apply(a, b);
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
            public T produce() {
                T result = producer.produce();
                action.accept(result);
                return result;
            }
        });
    }
    
    public SuspendableStream<T> peekAwaitable(ContinuableConsumer<? super T> action) {
        return nextStage(new InnerProducer<T>() {
            @Override
            public T produce() {
                T result = producer.produce();
                action.accept(result);
                return result;
            }
        });
    }
    
    public SuspendableStream<T> ignoreErrors() {
        return nextStage(new InnerProducer<T>() {
            @Override
            public T produce() {
                while (true) {
                    try {
                        return producer.produce();
                    } catch (ProducerExhaustedException | Error ex) {
                        // Propagate Producer.STOP + JVM errors
                        throw ex;
                    } catch (Throwable ex) {
                        // Ignore other exceptions
                    }
                }
            }
        });
    }
    
    public SuspendableStream<T> stopOnError() {
        return nextStage(new InnerProducer<T>() {
            @Override
            public T produce() {
                while (true) {
                    try {
                        return producer.produce();
                    } catch (Error ex) {
                        // Propagate JVM errors
                        throw ex;
                    } catch (Throwable ex) {
                        throw STOP;
                    }
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
            public T produce() {
                try {
                    return producer.produce();
                } catch (ProducerExhaustedException | Error ex) {
                    throw ex;
                } catch (Throwable ex) {
                    return recover.apply(ex);
                }
            }
        });
    }
    
    public SuspendableStream<T> recoverAwaitable(ContinuableFunction<? super Throwable, ? extends T> recover) {
        return nextStage(new InnerProducer<T>() {
            @Override
            public T produce() {
                try {
                    return producer.produce();
                } catch (ProducerExhaustedException | Error ex) {
                    throw ex;
                } catch (Throwable ex) {
                    return recover.apply(ex);
                }
            }
        });
    }
    
    public SuspendableStream<T> drop(long count) {
        return nextStage(new InnerProducer<T>() {
            long idx = 0;
            
            @Override
            public T produce() {
                for (; idx < count; idx++) {
                    // Forward
                    producer.produce();
                }
                return producer.produce();
            }
        });
    }
    
    public SuspendableStream<T> take(long maxSize) {
        return nextStage(new InnerProducer<T>() {
            long idx = 0;
            
            @Override
            public T produce() {
                if (idx < maxSize) {
                    idx++;
                    return producer.produce();                    
                } else {
                    throw STOP;
                }
            }
        });
    }
    
    public @suspendable void forEach(Consumer<? super T> consumer) {
        while (true) {
            T element;
            try {
                element = producer.produce();
            } catch (ProducerExhaustedException ex) {
                break;
            }
            consumer.accept(element);
        }
    }
    
    public @suspendable void forEachAwaitable(ContinuableConsumer<? super T> consumer) {
        while (true) {
            T element;
            try {
                element = producer.produce();
            } catch (ProducerExhaustedException ex) {
                break;
            }
            consumer.accept(element);
        }
    }
    
    public @suspendable Optional<T> reduce(BinaryOperator<T> accumulator) {
        boolean foundAny = false;
        T result = null;
        while (true) {
            T element;
            try {
                element = producer.produce();
            } catch (ProducerExhaustedException ex) {
                break;
            }
            
            if (!foundAny) {
                foundAny = true;
                result = element;
            }
            else {
                result = accumulator.apply(result, element);
            }

        }
        return foundAny ? Optional.of(result) : Optional.empty();
    }
    
    public @suspendable Optional<T> reduceAwaitable(ContinuableBinaryOperator<T> accumulator) {
        boolean foundAny = false;
        T result = null;
        while (true) {
            T element;
            try {
                element = producer.produce();
            } catch (ProducerExhaustedException ex) {
                break;
            }
            
            if (!foundAny) {
                foundAny = true;
                result = element;
            }
            else {
                result = accumulator.apply(result, element);
            }

        }
        return foundAny ? Optional.of(result) : Optional.empty();
    }
    
    public @suspendable <U> U fold(U identity, BiFunction<U, ? super T, U> accumulator) {
        U result = identity;
        while (true) {
            T element;
            try {
                element = producer.produce();
            } catch (ProducerExhaustedException ex) {
                break;
            }
            result = accumulator.apply(result, element);
        }
        return result;
    }
    
    public @suspendable <U> U foldAwaitable(U identity, ContinuableBiFunction<U, ? super T, U> accumulator) {
        U result = identity;
        while (true) {
            T element;
            try {
                element = producer.produce();
            } catch (ProducerExhaustedException ex) {
                break;
            }
            result = accumulator.apply(result, element);
        }
        return result;
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
    
    static final Object IGNORE_PARAM = new Object();
}
