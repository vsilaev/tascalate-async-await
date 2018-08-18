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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.javaflow.extras.ContinuableBiFunction;
import org.apache.commons.javaflow.extras.ContinuableConsumer;
import org.apache.commons.javaflow.extras.ContinuableFunction;
import org.apache.commons.javaflow.extras.ContinuablePredicate;
import org.apache.commons.javaflow.extras.ContinuableSupplier;

public abstract class Value<T> {
    // Package visible to restrict inheritance
    Value() {}
    
    abstract public boolean exist();
    abstract public T get();
    
    public final Value<T> orElse(Value<T> alt) {
        return orElse(toSupplier(() -> alt));
    }
    
    abstract public Value<T> orElse(Supplier<? extends Value<T>> alt);
    abstract public @suspendable Value<T> orElse(ContinuableSupplier<? extends Value<T>> alt);
    
    abstract public <R> Value<R> map(Function<? super T, ? extends R> mapper);
    abstract public @suspendable <R> Value<R> map(ContinuableFunction<? super T, ? extends R> mapper);
    
    abstract public <R> Value<R> flatMap(Function<? super T, ? extends Value<R>> mapper);
    
    abstract public Value<T> filter(Predicate<? super T> predicate);
    abstract public @suspendable Value<T> filter(ContinuablePredicate<? super T> predicate);
    
    abstract public <U, R> Value<R> combine(Value<U> other, BiFunction<? super T, ? super U, ? extends R> zipper);
    abstract public @suspendable <U, R> Value<R> combine(Value<U> other, ContinuableBiFunction<? super T, ? super U, ? extends R> zipper);
    
    abstract public void accept(Consumer<? super T> action);
    abstract public @suspendable void accept(ContinuableConsumer<? super T> action);
    
    public static class Some<T> extends Value<T> {
        private final T value;
        Some(T value) {
            this.value = value;
        }
        
        @Override
        public boolean exist() {
            return true;
        }
        
        @Override
        public T get() {
            return value;
        }
        
        @Override
        public Value<T> orElse(Supplier<? extends Value<T>> alt) {
            return this;
        }
        
        @Override
        public Value<T> orElse(ContinuableSupplier<? extends Value<T>> alt) {
            return this;
        }
        
        @Override
        public <R> Value<R> map(Function<? super T, ? extends R> mapper) {
            return some(mapper.apply(value));
        }
        
        @Override
        public <R> Value<R> map(ContinuableFunction<? super T, ? extends R> mapper) {
            return some(mapper.apply(value));
        }
        
        @Override
        public Value<T> filter(Predicate<? super T> predicate) {
            return predicate.test(value) ? this : none();
        }
        
        @Override
        public Value<T> filter(ContinuablePredicate<? super T> predicate) {
            return predicate.test(value) ? this : none();
        }
        
        @Override
        public <R> Value<R> flatMap(Function<? super T, ? extends Value<R>> mapper) {
            return mapper.apply(value);
        }
        
        @Override
        public <U, R> Value<R> combine(Value<U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {
            if (!other.exist()) {
                return none();
            } else {
                Some<U> someOther = (Some<U>)other;
                return some(zipper.apply(value, someOther.value));
            }
        }
        
        @Override
        public <U, R> Value<R> combine(Value<U> other, ContinuableBiFunction<? super T, ? super U, ? extends R> zipper) {
            if (!other.exist()) {
                return none();
            } else {
                Some<U> someOther = (Some<U>)other;
                return some(zipper.apply(value, someOther.value));
            }            
        }
        
        @Override
        public void accept(Consumer<? super T> action) {
            action.accept(value);
        }
        
        @Override
        public void accept(ContinuableConsumer<? super T> action) {
            action.accept(value);
        }
    }
    
    public static class None<T> extends Value<T> {
        private None() {}
        
        @Override
        public boolean exist() {
            return false;
        }
        
        @Override
        public T get() {
            throw new NoSuchElementException();
        }
        
        @Override
        public Value<T> orElse(Supplier<? extends Value<T>> alt) {
            return alt.get();
        }
        
        @Override
        public Value<T> orElse(ContinuableSupplier<? extends Value<T>> alt) {
            return alt.get();
        }

        
        @Override
        public <R> Value<R> map(Function<? super T, ? extends R> mapper) {
            return none();
        }
        
        @Override
        public <R> Value<R> map(ContinuableFunction<? super T, ? extends R> mapper) {
            return none();
        }
        
        @Override
        public Value<T> filter(Predicate<? super T> predicate) {
            return this;
        }
        
        @Override
        public Value<T> filter(ContinuablePredicate<? super T> predicate) {
            return this;
        }
        
        @Override
        public <R> Value<R> flatMap(Function<? super T, ? extends Value<R>> mapper) {
            return none();
        }
        
        @Override
        public <U, R> Value<R> combine(Value<U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {
            return none();
        }
        
        @Override
        public <U, R> Value<R> combine(Value<U> other, ContinuableBiFunction<? super T, ? super U, ? extends R> zipper) {
            return none();
        }
        
        @Override
        public void accept(Consumer<? super T> action) {
            
        }
        
        @Override
        public void accept(ContinuableConsumer<? super T> action) {
            
        }
    }
    
    public static <T> Value<T> some(T value) {
        return new Some<>(value);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> Supplier<Value<T>> useNull() {
        return (Supplier<Value<T>>)(Object)USE_NULL;
    }
    
    @SuppressWarnings("unchecked")
    public static <T> Value<T> none() {
        return (Value<T>)NONE;
    }
    

    private static <T> Supplier<T> toSupplier(Supplier<T> supplier) {
        return supplier;
    }
    
    private static final Value<?> NONE = new None<>();
    private static final Value<?> NULL = new Some<>(null);
    private static final Supplier<Value<?>> USE_NULL = () -> NULL;
}
