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

import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.javaflow.api.continuable;

public abstract class Option<T> {
    // Package visible to restrict inheritance
    Option() {}
    
    abstract public boolean exists();
    abstract public T get();
    
    public final Option<T> orElseNull() {
        return orElse(useNull());
    }
    
    public final Option<T> orElse(Option<T> alt) {
        return orElse(() -> alt);
    }
    
    abstract public Option<T> orElse(Supplier<? extends Option<T>> alt);
    abstract public @continuable Option<T> orElse$(ContinuableSupplier<? extends Option<? extends T>> alt);
    
    abstract public <R> Option<R> map(Function<? super T, ? extends R> mapper);
    abstract public @continuable <R> Option<R> map$(ContinuableFunction<? super T, ? extends R> mapper);
    
    abstract public <R> Option<R> flatMap(Function<? super T, ? extends Option<? extends R>> mapper);
    abstract public @continuable <R> Option<R> flatMap$(ContinuableFunction<? super T, ? extends Option<? extends R>> mapper);
    
    abstract public Option<T> filter(Predicate<? super T> predicate);
    abstract public @continuable Option<T> filter$(ContinuablePredicate<? super T> predicate);
    
    abstract public <U, R> Option<R> combine(Option<U> other, BiFunction<? super T, ? super U, ? extends R> zipper);
    abstract public @continuable <U, R> Option<R> combine$(Option<U> other, ContinuableBiFunction<? super T, ? super U, ? extends R> zipper);
    
    abstract public void accept(Consumer<? super T> action);
    abstract public @continuable void accept$(ContinuableConsumer<? super T> action);
    
    public static class Some<T> extends Option<T> {
        private final T value;
        Some(T value) {
            this.value = value;
        }
        
        @Override
        public boolean exists() {
            return true;
        }
        
        @Override
        public T get() {
            return value;
        }
        
        @Override
        public Option<T> orElse(Supplier<? extends Option<T>> alt) {
            return this;
        }
        
        @Override
        public Option<T> orElse$(ContinuableSupplier<? extends Option<? extends T>> alt) {
            return this;
        }
        
        @Override
        public <R> Option<R> map(Function<? super T, ? extends R> mapper) {
            return some(mapper.apply(value));
        }
        
        @Override
        public <R> Option<R> map$(ContinuableFunction<? super T, ? extends R> mapper) {
            return some(mapper.apply(value));
        }
        
        @Override
        public Option<T> filter(Predicate<? super T> predicate) {
            return predicate.test(value) ? this : none();
        }
        
        @Override
        public Option<T> filter$(ContinuablePredicate<? super T> predicate) {
            return predicate.test(value) ? this : none();
        }
        
        @Override
        public <R> Option<R> flatMap(Function<? super T, ? extends Option<? extends R>> mapper) {
            return narrow(mapper.apply(value));
        }
        
        @Override
        public <R> Option<R> flatMap$(ContinuableFunction<? super T, ? extends Option<? extends R>> mapper) {
            return narrow(mapper.apply(value));
        }
        
        @Override
        public <U, R> Option<R> combine(Option<U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {
            if (other.exists()) {
                Some<U> someOther = (Some<U>)other;
                return some(zipper.apply(value, someOther.value));
            } else {
                return none();
            }
        }
        
        @Override
        public <U, R> Option<R> combine$(Option<U> other, ContinuableBiFunction<? super T, ? super U, ? extends R> zipper) {
            if (other.exists()) {
                Some<U> someOther = (Some<U>)other;
                return some(zipper.apply(value, someOther.value));
            } else {
                return none();
            }            
        }
        
        @Override
        public void accept(Consumer<? super T> action) {
            action.accept(value);
        }
        
        @Override
        public void accept$(ContinuableConsumer<? super T> action) {
            action.accept(value);
        }
    }
    
    public static class None<T> extends Option<T> {
        private None() {}
        
        @Override
        public boolean exists() {
            return false;
        }
        
        @Override
        public T get() {
            throw new NoSuchElementException();
        }
        
        @Override
        public Option<T> orElse(Supplier<? extends Option<T>> alt) {
            return alt.get();
        }
        
        @Override
        public Option<T> orElse$(ContinuableSupplier<? extends Option<? extends T>> alt) {
            return narrow(alt.get());
        }

        
        @Override
        public <R> Option<R> map(Function<? super T, ? extends R> mapper) {
            return none();
        }
        
        @Override
        public <R> Option<R> map$(ContinuableFunction<? super T, ? extends R> mapper) {
            return none();
        }
        
        @Override
        public Option<T> filter(Predicate<? super T> predicate) {
            return this;
        }
        
        @Override
        public Option<T> filter$(ContinuablePredicate<? super T> predicate) {
            return this;
        }

        @Override
        public <R> Option<R> flatMap(Function<? super T, ? extends Option<? extends R>> mapper) {
            return none();
        }
        
        @Override
        public <R> Option<R> flatMap$(ContinuableFunction<? super T, ? extends Option<? extends R>> mapper) {
            return none();
        }
        
        @Override
        public <U, R> Option<R> combine(Option<U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {
            return none();
        }
        
        @Override
        public <U, R> Option<R> combine$(Option<U> other, ContinuableBiFunction<? super T, ? super U, ? extends R> zipper) {
            return none();
        }
        
        @Override
        public void accept(Consumer<? super T> action) {
            
        }
        
        @Override
        public void accept$(ContinuableConsumer<? super T> action) {
            
        }
        
        static final Option<?> INSTANCE = new None<>();
    }
    
    public static <T> Option<T> some(T value) {
        return new Some<>(value);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> Option<T> none() {
        return (Option<T>)None.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private static <T> Supplier<Option<T>> useNull() {
        return (Supplier<Option<T>>)USE_NULL;
    }
    
    @SuppressWarnings("unchecked")
    static <T> Option<T> narrow(Option<? extends T> v) {
        return (Option<T>)v;
    }
    
    private static final Option<?> NULL = new Some<>(null);
    private static final Supplier<?> USE_NULL = () -> NULL;
}
