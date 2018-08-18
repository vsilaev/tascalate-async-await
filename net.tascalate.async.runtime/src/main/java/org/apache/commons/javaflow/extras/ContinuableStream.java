/**
 * Copyright 2013-2018 Valery Silaev (http://vsilaev.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.javaflow.extras;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.api.suspendable;

public class ContinuableStream<T> implements AutoCloseable {
    
    protected static final ContinuableStream<Object> EMPTY = new ContinuableStream<>(new ContinuableProducer<Object>() {
        
        @Override
        public Option<Object> produce() {
            return Option.none();
        }
        
        @Override
        public void close() {}
    });
    
    protected final ContinuableProducer<T> producer;
    
    public ContinuableStream(ContinuableProducer<T> producer) {
        this.producer = producer;
    }
    
    @Override
    public void close() {
        producer.close();
    }
    
    protected <U> ContinuableStream<U> nextStage(ContinuableProducer<U> producer) {
        return new ContinuableStream<>(producer);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> ContinuableStream<T> empty() {
        return (ContinuableStream<T>)EMPTY;
    }
    
    public static <T> ContinuableStream<T> of(T value) {
        return ContinuableStream.repeat(value).take(1);
    }
    
    @SafeVarargs
    public static <T> ContinuableStream<T> of(T... values) {
        return of(Stream.of(values));
    }
    
    public static <T> ContinuableStream<T> of(Iterable<? extends T> values) {
        return of(values.iterator());
    }
    
    public static <T> ContinuableStream<T> of(Stream<? extends T> values) {
        return of(values.iterator());
    }
    
    private static <T> ContinuableStream<T> of(Iterator<? extends T> values) {
        return new ContinuableStream<>(new RootProducer<T>() {
            @Override
            public Option<T> produce() {
                if (values.hasNext()) {
                    return Option.some(values.next());
                } else {
                    return Option.none();
                }
            }
        });
    }
    
    public static <T> ContinuableStream<T> repeat(T value) {
        return new ContinuableStream<>(new RootProducer<T>() {
            @Override
            public Option<T> produce() {
                return Option.some(value);
            }
        });
    }
    
    public static <T> ContinuableStream<T> generate(Supplier<? extends T> supplier) {
        return new ContinuableStream<>(new RootProducer<T>() {
            @Override
            public Option<T> produce() {
                return Option.some(supplier.get());
            }
        });
    }
    
    public static <T> ContinuableStream<T> generate$(ContinuableSupplier<? extends T> supplier) {
        return new ContinuableStream<>(new RootProducer<T>() {
            @Override
            public Option<T> produce() {
                return Option.some(supplier.get());
            }
        });
    }

    public static <T> ContinuableStream<T> iterate(T seed, UnaryOperator<T> f) {
        return new ContinuableStream<>(new RootProducer<T>() {
            Option<T> current = Option.none();
            @Override
            public Option<T> produce() {
                return current.exists() ? current.map(f) : Option.some(seed);
            }
        });
    }
    
    public static <T> ContinuableStream<T> iterate$(T seed, ContinuableUnaryOperator<T> f) {
        return new ContinuableStream<>(new RootProducer<T>() {
            Option<T> current = Option.none();
            @Override
            public Option<T> produce() {
                return current.exists() ? current.map$(f) : Option.some(seed);
            }
        });
    }
    
    
    @SafeVarargs
    public static <T> ContinuableStream<T> union(ContinuableStream<? extends T>... streams) {
        return union(Arrays.asList(streams));
    }
    
    public static <T> ContinuableStream<T> union(Collection<? extends ContinuableStream<? extends T>> streams) {
        return ContinuableStream.of(streams.stream()).flatMap(Function.identity());
    }
    
    public static <T, U, R> ContinuableStream<R> zip(ContinuableStream<T> a, 
                                                     ContinuableStream<U> b,
                                                     BiFunction<? super T, ? super U, ? extends R> zipper) {
        return zip(a, b, zipper, null, null);
    }
    
    public static <T, U, R> ContinuableStream<R> zip(ContinuableStream<T> a, 
                                                     ContinuableStream<U> b, 
                                                     BiFunction<? super T, ? super U, ? extends R> zipper,
                                                     Supplier<? extends T> onAMissing,
                                                     Supplier<? extends U> onBMissing) {
        return a.zip(b, zipper, onAMissing, onBMissing);
    }
    
    public static <T, U, R> ContinuableStream<R> zip$(ContinuableStream<T> a,
                                                      ContinuableStream<U> b,
                                                      ContinuableBiFunction<? super T, ? super U, ? extends R> zipper) {
        return zip$(a, b, zipper, null, null);
    }
    
    public static <T, U, R> ContinuableStream<R> zip$(ContinuableStream<T> a, 
                                                      ContinuableStream<U> b,
                                                      ContinuableBiFunction<? super T, ? super U, ? extends R> zipper,
                                                      ContinuableSupplier<? extends T> onAMissing,
                                                      ContinuableSupplier<? extends U> onBMissing) {
        return a.zip$(b, zipper, onAMissing, onBMissing);
    }

    
    public <R> ContinuableStream<R> map(Function<? super T, ? extends R> mapper) {
        return nextStage(new NestedStageProducer<R>() {
            @Override
            public Option<R> produce() {
                return producer.produce().map(mapper);
            }
        });
    }
    
    public <R> ContinuableStream<R> map$(ContinuableFunction<? super T, ? extends R> mapper) {
        return nextStage(new NestedStageProducer<R>() {
            @Override
            public Option<R> produce() {
                return producer.produce().map$(mapper);
            }
        });
    }

    
    public ContinuableStream<T> filter(Predicate<? super T> predicate) {
        return nextStage(new NestedStageProducer<T>() {
            @Override
            public Option<T> produce() {
                Option<T> v;
                while ((v = producer.produce()).exists()) {
                    v = v.filter(predicate);
                    if (v.exists()) {
                        return v;
                    }
                }
                return Option.none();
            }
        });
    }
    
    public ContinuableStream<T> filter$(ContinuablePredicate<? super T> predicate) {
        return nextStage(new NestedStageProducer<T>() {
            @Override
            public Option<T> produce() {
                Option<T> v;
                while ((v = producer.produce()).exists()) {
                    v = v.filter$(predicate);
                    if (v.exists()) {
                        return v;
                    }
                }
                return Option.none();
            }
        });
    }
    
    
    public <R> ContinuableStream<R> flatMap(Function<? super T, ? extends ContinuableStream<? extends R>> mapper) {
        StreamToOption<R> nextByStreamProducer = nextByStreamProducer();
        return nextStage(new NestedStageProducer<R>() {
            Option<? extends ContinuableStream<? extends R>> current = Option.none();
            
            @Override
            public Option<R> produce() {
                Option<R> r = current.flatMap$(nextByStreamProducer);
                if (r.exists()) {
                    return r;
                } else {
                    current.accept(ContinuableStream::close);
                    Option<T> v;
                    while ((v = producer.produce()).exists()) {
                        current = v.map(mapper);
                        r = current.flatMap$(nextByStreamProducer);
                        if (r.exists()) {
                            return r;
                        }
                    }
                    return Option.none();
                }
            }

            @Override
            public void close() {
                try {
                    current.accept(ContinuableStream::close);
                } finally {
                    super.close();
                }
            }
        });
    }
    
    public <R> ContinuableStream<R> flatMap$(ContinuableFunction<? super T, ? extends ContinuableStream<? extends R>> mapper) {
        StreamToOption<R> nextByStreamProducer = nextByStreamProducer();
        return nextStage(new NestedStageProducer<R>() {
            Option<? extends ContinuableStream<? extends R>> current = Option.none();
            
            @Override
            public Option<R> produce() {
                Option<R> r = current.flatMap$(nextByStreamProducer);
                if (r.exists()) {
                    return r;
                } else {
                    current.accept(ContinuableStream::close);
                    Option<T> v;
                    while ((v = producer.produce()).exists()) {
                        current = v.map$(mapper);
                        r = current.flatMap$(nextByStreamProducer);
                        if (r.exists()) {
                            return r;
                        }
                    }
                    return Option.none();
                }
            }

            @Override
            public void close() {
                try {
                    current.accept(ContinuableStream::close);
                } finally {
                    super.close();
                }
            }
        });
    }
    
    public ContinuableStream<T> union(ContinuableStream<? extends T> other) {
        return union(this, other);
    }

    public <U, R> ContinuableStream<R> zip(ContinuableStream<U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {
        return zip(other, zipper, null, null);
    }
    
    public <U, R> ContinuableStream<R> zip(ContinuableStream<U> other, 
                                           BiFunction<? super T, ? super U, ? extends R> zipper,
                                           Supplier<? extends T> onLeftMissing, 
                                           Supplier<? extends U> onRightMissing) {
        
        Supplier<Option<T>> aMissing = toValueSupplier(onLeftMissing); 
        Supplier<Option<U>> bMissing = toValueSupplier(onRightMissing);
        return nextStage(new NestedStageProducer<R>() {
            @Override
            public Option<R> produce() {
                Option<T> a = producer.produce();
                Option<U> b = other.producer.produce();
                if (a.exists() || b.exists()) {
                    // Try to combine if at least one exist
                    return a.orElse(aMissing).combine(b.orElse(bMissing), zipper);                    
                } else {
                    return Option.none();
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
    
    public <U, R> ContinuableStream<R> zip$(ContinuableStream<U> other, ContinuableBiFunction<? super T, ? super U, ? extends R> zipper) {
        return zip$(other, zipper, null, null);
    }
    
    public <U, R> ContinuableStream<R> zip$(ContinuableStream<U> other, 
                                            ContinuableBiFunction<? super T, ? super U, ? extends R> zipper,
                                            ContinuableSupplier<? extends T> onLeftMissing,
                                            ContinuableSupplier<? extends U> onRightMissing) {
        ContinuableSupplier<Option<T>> aMissing = toValueSupplier(onLeftMissing); 
        ContinuableSupplier<Option<U>> bMissing = toValueSupplier(onRightMissing);
        return nextStage(new NestedStageProducer<R>() {
            @Override
            public Option<R> produce() {
                Option<T> a = producer.produce();
                Option<U> b = other.producer.produce();
                if (a.exists() || b.exists()) {
                    // Try to combine if at least one exist
                    return a.orElse$(aMissing).combine$(b.orElse$(bMissing), zipper);                    
                } else {
                    return Option.none();
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
    
    public ContinuableStream<T> peek(Consumer<? super T> action) {
        return nextStage(new NestedStageProducer<T>() {
            @Override
            public Option<T> produce() {
                Option<T> result = producer.produce();
                result.accept(action);
                return result;
            }
        });
    }
    
    public ContinuableStream<T> peek$(ContinuableConsumer<? super T> action) {
        return nextStage(new NestedStageProducer<T>() {
            @Override
            public Option<T> produce() {
                Option<T> result = producer.produce();
                result.accept$(action);
                return result;
            }
        });
    }
    
    public ContinuableStream<T> ignoreErrors() {
        return nextStage(new NestedStageProducer<T>() {
            @Override
            public Option<T> produce() {
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
    
    public ContinuableStream<T> stopOnError() {
        return nextStage(new NestedStageProducer<T>() {
            @Override
            public Option<T> produce() {
                try {
                    return producer.produce();
                } catch (Throwable ex) {
                    return Option.none();
                }
            }
        });
    }    
    
    public ContinuableStream<T> recover(T value) {
        return recover(ex -> value);
    }    
    
    public ContinuableStream<T> recover(Function<? super Throwable, ? extends T> recover) {
        return nextStage(new NestedStageProducer<T>() {
            @Override
            public Option<T> produce() {
                try {
                    return producer.produce();
                } catch (Throwable ex) {
                    return Option.some(recover.apply(ex));
                }
            }
        });
    }
    
    public ContinuableStream<T> recover$(ContinuableFunction<? super Throwable, ? extends T> recover) {
        return nextStage(new NestedStageProducer<T>() {
            @Override
            public Option<T> produce() {
                try {
                    return producer.produce();
                } catch (Throwable ex) {
                    return Option.some(recover.apply(ex));
                }
            }
        });
    }
    
    public ContinuableStream<T> drop(long count) {
        return nextStage(new NestedStageProducer<T>() {
            long idx = 0;
            
            @Override
            public Option<T> produce() {
                for (; idx < count; idx++) {
                    // Forward
                    Option<T> v = producer.produce();
                    if (!v.exists()) {
                        idx = count;
                        return Option.none();
                    }
                }
                return producer.produce();
            }
        });
    }
    
    public ContinuableStream<T> take(long maxSize) {
        return nextStage(new NestedStageProducer<T>() {
            long idx = 0;
            
            @Override
            public Option<T> produce() {
                if (idx < maxSize) {
                    idx++;
                    Option<T> v = producer.produce();
                    if (!v.exists()) {
                        idx = maxSize;
                        return Option.none();
                    } else {
                        return v;
                    }
                } else {
                    // Close as long as we potentially terminating preliminary
                    close();
                    return Option.none();
                }
            }
        });
    }
    
    public @continuable void forEach(Consumer<? super T> action) {
        Option<T> v;
        while ((v = producer.produce()).exists()) {
            v.accept(action);
        }
    }
    
    public @continuable void forEach$(ContinuableConsumer<? super T> action) {
        Option<T> v;
        while ((v = producer.produce()).exists()) {
            v.accept$(action);
        }
    }
    
    public @continuable Optional<T> reduce(BinaryOperator<T> accumulator) {
        Option<T> result = Option.none();
        Option<T> v;
        while ((v = producer.produce()).exists()) {
            if (result.exists()) {
                result = result.combine(v, accumulator);
            } else {
                result = v;                
            }
        }
        return toOptional(result);
    }
    
    public @continuable Optional<T> reduce$(ContinuableBinaryOperator<T> accumulator) {
        Option<T> result = Option.none();
        Option<T> v;
        while ((v = producer.produce()).exists()) {
            if (result.exists()) {
                result = result.combine$(v, accumulator);
            } else {
                result = v;                
            }
        }
        return toOptional(result);
    }
    
    public @continuable <U> U fold(U identity, BiFunction<U, ? super T, U> accumulator) {
        Option<U> result = Option.some(identity);
        Option<T> v;
        while ((v = producer.produce()).exists()) {
            result = result.combine(v, accumulator);
        }
        return result.get();
    }
    
    public @continuable <U> U fold$(U identity, ContinuableBiFunction<U, ? super T, U> accumulator) {
        Option<U> result = Option.some(identity);
        Option<T> v;
        while ((v = producer.produce()).exists()) {
            result = result.combine$(v, accumulator);
        }
        return result.get();
    }
    
    public <R> R apply(Function<? super ContinuableStream<T>, R> converter) {
        return converter.apply(this);
    }
    
    public <R> R as(Function<? super ContinuableProducer<T>, R> converter) {
        return converter.apply(producer);
    }
    
    public ContinuableIterator<T> iterator() {
        return new StreamIterator();
    }

    abstract class NestedStageProducer<U> implements ContinuableProducer<U> {
        @Override
        public void close() {
            ContinuableStream.this.close();
        }
    }
    
    abstract static class RootProducer<U> implements ContinuableProducer<U> {
        @Override
        public void close() {
        }
    }
    
    final class StreamIterator implements ContinuableIterator<T> {
        private boolean advance  = true;
        private Option<T> current = Option.none();
        
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
            ContinuableStream.this.close();
        }
        
        protected @suspendable void advanceIfNecessary() {
            if (advance) {
                current = producer.produce();
            }
            advance = false;
        }

        @Override
        public String toString() {
            return String.format("%s[owner=%s, current=%s]", getClass().getSimpleName(), ContinuableStream.this, current);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static <T> Supplier<Option<T>> toValueSupplier(Supplier<? extends T> s) {
        return s != null ? () -> Option.some(s.get()) : (Supplier<Option<T>>)NONE_SUPPLIER_R;
    }
    
    @SuppressWarnings("unchecked")
    private static <T> ContinuableSupplier<Option<T>> toValueSupplier(ContinuableSupplier<? extends T> s) {
        return s != null ? () -> Option.some(s.get()) : (ContinuableSupplier<Option<T>>)NONE_SUPPLIER_C;
    }

    private static <T> Optional<T> toOptional(Option<T> maybeValue) {
        return maybeValue.exists() ? Optional.ofNullable( maybeValue.get() ) : Optional.empty();
    }

    private static <R> StreamToOption<R> nextByStreamProducer() {
        return new StreamToOption<R>() {
            @Override
            public Option<? extends R> apply(ContinuableStream<? extends R> stream) {
                return stream.producer.produce();
            }
        };
    }
    
    // To capture insane generic signature
    private static interface StreamToOption<T> 
        extends ContinuableFunction<ContinuableStream<? extends T>, Option<? extends T>> {}
    
    private static final Supplier<?> NONE_SUPPLIER_R = () -> Option.none();
    private static final ContinuableSupplier<?> NONE_SUPPLIER_C = () -> Option.none();
}
