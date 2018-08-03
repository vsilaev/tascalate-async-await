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
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.javaflow.extras.ContinuableBiFunction;
import org.apache.commons.javaflow.extras.ContinuableBinaryOperator;
import org.apache.commons.javaflow.extras.ContinuableConsumer;
import org.apache.commons.javaflow.extras.ContinuableFunction;
import org.apache.commons.javaflow.extras.ContinuablePredicate;

public class SuspendableStream<T> {
    
    public static interface Producer<T> extends AutoCloseable {
        @suspendable T produce(Object param) throws NoSuchElementException;
        void close();
        
        @SuppressWarnings("serial")
        public static final NoSuchElementException STOP = new NoSuchElementException() {
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
        };
        
    }
    
    private final Producer<T> delegate;
    
    public SuspendableStream(Producer<T> delegate) {
        this.delegate = delegate;
    }
    
    public void close() {
        delegate.close();
    }
    
    protected <U> SuspendableStream<U> nextStage(Producer<U> producer) {
        return new SuspendableStream<>(producer);
    }
    
    public <R> SuspendableStream<R> map(Function<? super T, ? extends R> mapper) {
        return nextStage(new Producer<R>() {
            @Override
            public R produce(Object param) {
                return mapper.apply(delegate.produce(param));
            }

            @Override
            public void close() {
                SuspendableStream.this.close();
            }
        });
    }
    
    public <R> SuspendableStream<R> mapAwaitable(ContinuableFunction<? super T, ? extends R> mapper) {
        return nextStage(new Producer<R>() {
            @Override
            public R produce(Object param) {
                return mapper.apply(delegate.produce(param));
            }

            @Override
            public void close() {
                SuspendableStream.this.close();
            }
        });
    }

    
    public SuspendableStream<T> filter(Predicate<? super T> predicate) {
        return nextStage(new Producer<T>() {
            @Override
            public T produce(Object param) {
                while (true) {
                    T original = delegate.produce(param);
                    if (predicate.test(original)) {
                        return original;
                    }
                }
            }

            @Override
            public void close() {
                SuspendableStream.this.close();
            }
        });
    }
    
    public SuspendableStream<T> filterAwaitable(ContinuablePredicate<? super T> predicate) {
        return nextStage(new Producer<T>() {
            @Override
            public T produce(Object param) {
                while (true) {
                    T original = delegate.produce(param);
                    if (predicate.test(original)) {
                        return original;
                    }
                }
            }

            @Override
            public void close() {
                SuspendableStream.this.close();
            }
        });
    }
    
    
    public <R> SuspendableStream<R> flatMap(Function<? super T, ? extends SuspendableStream<? extends R>> mapper) {
        return nextStage(new Producer<R>() {
            SuspendableStream<? extends R> current = null;
            
            @Override
            public R produce(Object param) {
                if (null != current) {
                    try {
                        return current.delegate.produce(param);
                    } catch (NoSuchElementException ex) {
                        current.close();
                        current = null;
                    }
                }

                while (null == current) {
                    // If NoSuchElementException is thrown then delegate is over
                    current = mapper.apply(delegate.produce(param));
                    try {
                        return current.delegate.produce(param);
                    } catch (NoSuchElementException ex) {
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
                    SuspendableStream.this.close();
                }
            }
        });
    }
    
    public <R> SuspendableStream<R> flatMapAwaitable(ContinuableFunction<? super T, ? extends SuspendableStream<? extends R>> mapper) {
        return nextStage(new Producer<R>() {
            SuspendableStream<? extends R> current = null;
            
            @Override
            public R produce(Object param) {
                if (null != current) {
                    try {
                        return current.delegate.produce(param);
                    } catch (NoSuchElementException ex) {
                        current.close();
                        current = null;
                    }
                }

                while (null == current) {
                    // If NoSuchElementException is thrown then delegate is over
                    current = mapper.apply(delegate.produce(param));
                    try {
                        return current.delegate.produce(param);
                    } catch (NoSuchElementException ex) {
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
                    SuspendableStream.this.close();
                }
            }
        });
    }
    
    public <U, R> SuspendableStream<R> zip(SuspendableStream<U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {
        return nextStage(new Producer<R>() {
            @Override
            public R produce(Object param) {
                T a;
                boolean aFailed = false;
                try {
                    a = delegate.produce(param);
                } catch (NoSuchElementException ex) {
                    a = null;
                    aFailed = true;
                }
                
                U b;
                boolean bFailed = false;
                try {
                    b = other.delegate.produce(param);
                } catch (NoSuchElementException ex) {
                    b = null;
                    bFailed = true;
                }

                if (aFailed && bFailed) {
                    throw STOP;
                } else {
                    return zipper.apply(a, b);
                }
            }

            @Override
            public void close() {
                try {
                    other.close();
                } finally {
                    SuspendableStream.this.close();
                }
            }
        });        
    }
    
    public <U, R> SuspendableStream<R> zipAwaitable(SuspendableStream<U> other, ContinuableBiFunction<? super T, ? super U, ? extends R> zipper) {
        return nextStage(new Producer<R>() {
            @Override
            public R produce(Object param) {
                T a;
                boolean aFailed = false;
                try {
                    a = delegate.produce(param);
                } catch (NoSuchElementException ex) {
                    a = null;
                    aFailed = true;
                }
                
                U b;
                boolean bFailed = false;
                try {
                    b = other.delegate.produce(param);
                } catch (NoSuchElementException ex) {
                    b = null;
                    bFailed = true;
                }

                if (aFailed && bFailed) {
                    throw STOP;
                } else {
                    return zipper.apply(a, b);
                }
            }

            @Override
            public void close() {
                try {
                    other.close();
                } finally {
                    SuspendableStream.this.close();
                }
            }
        });        
    }
    
    public SuspendableStream<T> skip(long count) {
        return nextStage(new Producer<T>() {
            long idx = 0;
            
            @Override
            public T produce(Object param) {
                for (; idx < count; idx++) {
                    // Forward
                    delegate.produce(param);
                }
                return delegate.produce(param);
            }
            
            @Override
            public void close() {
                SuspendableStream.this.close();
            }
        });
    }
    
    public SuspendableStream<T> limit(long maxSize) {
        return nextStage(new Producer<T>() {
            long idx = 0;
            
            @Override
            public T produce(Object param) {
                if (idx < maxSize) {
                    idx++;
                    return delegate.produce(param);                    
                } else {
                    throw STOP;
                }
            }
            
            @Override
            public void close() {
                SuspendableStream.this.close();
            }
        });
    }
    
    public @suspendable void forEach(Consumer<? super T> consumer) {
        while (true) {
            T element;
            try {
                element = delegate.produce(null);
            } catch (NoSuchElementException ex) {
                break;
            }
            consumer.accept(element);
        }
    }
    
    public @suspendable void forEachAwaitable(ContinuableConsumer<? super T> consumer) {
        while (true) {
            T element;
            try {
                element = delegate.produce(null);
            } catch (NoSuchElementException ex) {
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
                element = delegate.produce(null);
            } catch (NoSuchElementException ex) {
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
                element = delegate.produce(null);
            } catch (NoSuchElementException ex) {
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
    
    public @suspendable <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        U result = identity;
        while (true) {
            T element;
            try {
                element = delegate.produce(null);
            } catch (NoSuchElementException ex) {
                break;
            }
            result = accumulator.apply(result, element);
        }
        return result;
    }
    
    public <R> R as(Function<? super SuspendableStream<T>, R> converter) {
        return converter.apply(this);
    }
    
    public static <T> Generator<T> generator(SuspendableStream<? extends CompletionStage<T>> stream) {
        return new Generator<T>() {

            @Override
            public CompletionStage<T> next(Object producerParam) {
                return stream.delegate.produce(producerParam);
            }

            @Override
            public void close() {
                stream.close();
            }
            
        };
    }
}
