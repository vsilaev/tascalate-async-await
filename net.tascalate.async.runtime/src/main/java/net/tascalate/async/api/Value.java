package net.tascalate.async.api;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.javaflow.api.continuable;
import org.apache.commons.javaflow.extras.ContinuableBiFunction;
import org.apache.commons.javaflow.extras.ContinuableConsumer;
import org.apache.commons.javaflow.extras.ContinuableFunction;
import org.apache.commons.javaflow.extras.ContinuablePredicate;
import org.apache.commons.javaflow.extras.ContinuableSupplier;

public abstract class Value<T> {
    // Package visible to restrict inheritance
    Value() {}
    
    abstract boolean isNone();
    abstract Optional<T> toOptional();
    
    abstract T get();
    
    abstract Value<T> orElse(Supplier<? extends Value<T>> alt);
    abstract @continuable Value<T> orElse(ContinuableSupplier<? extends Value<T>> alt);
    
    abstract <R> Value<R> map(Function<? super T, ? extends R> mapper);
    abstract @suspendable <R> Value<R> map(ContinuableFunction<? super T, ? extends R> mapper);
    
    abstract <R> Value<R> flatMap(Function<? super T, ? extends Value<R>> mapper);
    
    abstract Value<T> filter(Predicate<? super T> predicate);
    abstract @suspendable Value<T> filter(ContinuablePredicate<? super T> predicate);
    
    abstract <U, R> Value<R> combine(Value<U> other, BiFunction<? super T, ? super U, ? extends R> zipper);
    abstract @suspendable <U, R> Value<R> combine(Value<U> other, ContinuableBiFunction<? super T, ? super U, ? extends R> zipper);
    
    abstract void accept(Consumer<? super T> action);
    abstract void accept_(Consumer<? super T> action);
    abstract @suspendable void accept(ContinuableConsumer<? super T> action);
    
    public static class Some<T> extends Value<T> {
        private final T value;
        Some(T value) {
            this.value = value;
        }
        
        boolean isNone() {
            return false;
        }
        
        Optional<T> toOptional() {
            return Optional.ofNullable(value);
        }
        
        T get() {
            return value;
        }
        
        Value<T> orElse(Supplier<? extends Value<T>> alt) {
            return this;
        }
        Value<T> orElse(ContinuableSupplier<? extends Value<T>> alt) {
            return this;
        }
        
        <R> Value<R> map(Function<? super T, ? extends R> mapper) {
            return some(mapper.apply(value));
        }
        
        <R> Value<R> map(ContinuableFunction<? super T, ? extends R> mapper) {
            return some(mapper.apply(value));
        }
        
        Value<T> filter(Predicate<? super T> predicate) {
            return predicate.test(value) ? this : none();
        }
        
        Value<T> filter(ContinuablePredicate<? super T> predicate) {
            return predicate.test(value) ? this : none();
        }
        
        <R> Value<R> flatMap(Function<? super T, ? extends Value<R>> mapper) {
            return mapper.apply(value);
        }
        
        <U, R> Value<R> combine(Value<U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {
            if (other.isNone()) {
                return none();
            } else {
                Some<U> someOther = (Some<U>)other;
                return some(zipper.apply(value, someOther.value));
            }
        }
        
        <U, R> Value<R> combine(Value<U> other, ContinuableBiFunction<? super T, ? super U, ? extends R> zipper) {
            if (other.isNone()) {
                return none();
            } else {
                Some<U> someOther = (Some<U>)other;
                return some(zipper.apply(value, someOther.value));
            }            
        }
        
        void accept(Consumer<? super T> action) {
            action.accept(value);
        }
        
        void accept(ContinuableConsumer<? super T> action) {
            action.accept(value);
        }
        
        void accept_(Consumer<? super T> action) {
            action.accept(value);
        }
    }
    
    public static class None<T> extends Value<T> {
        private None() {}
        
        boolean isNone() {
            return true;
        }
        
        Optional<T> toOptional() {
            return Optional.empty();
        }
        
        T get() {
            throw new NoSuchElementException();
        }
        
        Value<T> orElse(Supplier<? extends Value<T>> alt) {
            return alt.get();
        }
        
        Value<T> orElse(ContinuableSupplier<? extends Value<T>> alt) {
            return alt.get();
        }

        
        <R> Value<R> map(Function<? super T, ? extends R> mapper) {
            return none();
        }
        
        <R> Value<R> map(ContinuableFunction<? super T, ? extends R> mapper) {
            return none();
        }
        
        Value<T> filter(Predicate<? super T> predicate) {
            return this;
        }
        
        Value<T> filter(ContinuablePredicate<? super T> predicate) {
            return this;
        }
        
        <R> Value<R> flatMap(Function<? super T, ? extends Value<R>> mapper) {
            return none();
        }
        
        <U, R> Value<R> combine(Value<U> other, BiFunction<? super T, ? super U, ? extends R> zipper) {
            return none();
        }
        
        <U, R> Value<R> combine(Value<U> other, ContinuableBiFunction<? super T, ? super U, ? extends R> zipper) {
            return none();
        }
        
        void accept(Consumer<? super T> action) {
            
        }
        
        void accept(ContinuableConsumer<? super T> action) {
            
        }
        
        void accept_(Consumer<? super T> action) {
            
        }
    }
    
    private static final None<Object> NONE = new None<>();
    
    public static <T> Value<T> some(T value) {
        return new Some<>(value);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> None<T> none() {
        return (None<T>)NONE;
    }
}
