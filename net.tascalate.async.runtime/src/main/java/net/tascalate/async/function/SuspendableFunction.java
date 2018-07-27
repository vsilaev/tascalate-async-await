package net.tascalate.async.function;

import java.util.Objects;

import net.tascalate.async.api.suspendable;

@FunctionalInterface
public interface SuspendableFunction<T, R> {

    /**
     * Applies this function to the given argument.
     *
     * @param t the function argument
     * @return the function result
     */
    @suspendable R apply(T t);

    /**
     * Returns a composed function that first applies the {@code before}
     * function to its input, and then applies this function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V> the type of input to the {@code before} function, and to the
     *           composed function
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     * @throws NullPointerException if before is null
     *
     * @see #andThen(Function)
     */
    default <V> SuspendableFunction<V, R> compose(SuspendableFunction<? super V, ? extends T> before) {
        Objects.requireNonNull(before);
        SuspendableFunction<T, R> self = this;
        return new SuspendableFunction<V, R>() {
            @Override
            public @suspendable R apply(V v) {
                return self.apply(before.apply(v));                
            }
        };
    }

    /**
     * Returns a composed function that first applies this function to
     * its input, and then applies the {@code after} function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V> the type of output of the {@code after} function, and of the
     *           composed function
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the {@code after} function
     * @throws NullPointerException if after is null
     *
     * @see #compose(Function)
     */
    default <V> SuspendableFunction<T, V> andThen(SuspendableFunction<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        SuspendableFunction<T, R> self = this;
        return new SuspendableFunction<T, V>() {
            @Override
            public @suspendable V apply(T v) {
                return after.apply(self.apply(v));                
            }
        };
    }

    /**
     * Returns a function that always returns its input argument.
     *
     * @param <T> the type of the input and output objects to the function
     * @return a function that always returns its input argument
     */
    static <T> SuspendableFunction<T, T> identity() {
        return new SuspendableFunction<T, T>() {
            @Override
            public @suspendable T apply(T v) {
                return v;                
            }
        };
    }
}