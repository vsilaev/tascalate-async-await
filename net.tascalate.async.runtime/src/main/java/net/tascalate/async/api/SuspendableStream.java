package net.tascalate.async.api;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;

public class SuspendableStream<T> {
    private final SuspendableProducer<T> delegate;
    
    public SuspendableStream(SuspendableProducer<T> delegate) {
        this.delegate = delegate;
    }
    
    public void close() {
        delegate.close();
    }
    
    public <R> SuspendableStream<R> map(Function<? super T, ? extends R> mapper) {
        return new SuspendableStream<>(new SuspendableProducer<R>() {

            @Override
            public R produce(Object param) {
                T original = delegate.produce(param);
                if (null == original) {
                    return null;
                } else {
                    return mapper.apply(original);
                }
            }

            @Override
            public void close() {
                SuspendableStream.this.close();
            }
        });
    }
    
    public SuspendableStream<T> filter(Predicate<? super T> predicate) {
        return new SuspendableStream<>(new SuspendableProducer<T>() {

            @Override
            public T produce(Object param) {
                T original;
                while (null != (original= delegate.produce(param))) {
                    if (predicate.test(original)) {
                        return original;
                    }
                }
                return null;
            }

            @Override
            public void close() {
                SuspendableStream.this.close();
            }
        });
    }
    
    public <R> SuspendableStream<R> flatMap(Function<? super T, ? extends SuspendableStream<? extends R>> mapper) {
        SuspendableProducer<R> producer = new SuspendableProducer<R>() {
            SuspendableStream<? extends R> current = null;
            
            @Override
            public R produce(Object param) {
                if (null != current) {
                    R result = current.delegate.produce(param);
                    if (null == result) {
                        current.close();
                        current = null;
                    } else {
                        return result;
                    }
                }

                while (null == current) {
                    T original = delegate.produce(param);
                    if (null == original) {
                        return null;
                    } else {
                        current = mapper.apply(original);
                    }
                    
                    if (null != current) {
                        R result = current.delegate.produce(param);
                        if (null == result) {
                            current.close();
                            current = null;
                        } else {
                            return result;
                        }
                    }
                }
                return null;
            }

            @Override
            public void close() {
                SuspendableStream.this.close();
            }
        };
        return new SuspendableStream<>(producer);
    }
    
    public SuspendableStream<T> skip(long count) {
        return new SuspendableStream<T>(new SuspendableProducer<T>() {
            long idx = 0;
            
            @Override
            public T produce(Object param) {
                for (; idx < count; idx++) {
                    T original = delegate.produce(param);
                    if (null == original) {
                        return null;
                    }
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
        return new SuspendableStream<T>(new SuspendableProducer<T>() {
            long idx = 0;
            
            @Override
            public T produce(Object param) {
                if (idx < maxSize) {
                    idx++;
                    return delegate.produce(param);                    
                } else {
                    return null;
                }
            }
            
            @Override
            public void close() {
                SuspendableStream.this.close();
            }
        });
    }
    
    public @suspendable Optional<T> reduce(BinaryOperator<T> accumulator) {
        boolean foundAny = false;
        T result = null;
        for (T element = delegate.produce(null); null != element; element = delegate.produce(null)) {
            if (!foundAny) {
                foundAny = true;
                result = element;
            }
            else
                result = accumulator.apply(result, element);
        }
        return foundAny ? Optional.of(result) : Optional.empty();
    }
    
    public @suspendable <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        U result = identity;
        for (T element = delegate.produce(null); null != element; element = delegate.produce(null)) {
            result = accumulator.apply(result, element);
        }
        return result;
    }
}
