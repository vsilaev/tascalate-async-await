package net.tascalate.async.function;

import java.util.Objects;

import net.tascalate.async.api.suspendable;

/**
 * Represents a predicate (boolean-valued function) of one argument.
 *
 * <p>This is a functional interface
 * whose functional method is {@link #test(Object)}.
 *
 * @param <T> the type of the input to the predicate
 *
 * @since 1.8
 */
@FunctionalInterface
public interface SuspendablePredicate<T> {

    /**
     * Evaluates this predicate on the given argument.
     *
     * @param t the input argument
     * @return {@code true} if the input argument matches the predicate,
     * otherwise {@code false}
     */
    @suspendable boolean test(T t);

    /**
     * Returns a composed predicate that represents a short-circuiting logical
     * AND of this predicate and another.  When evaluating the composed
     * predicate, if this predicate is {@code false}, then the {@code other}
     * predicate is not evaluated.
     *
     * <p>Any exceptions thrown during evaluation of either predicate are relayed
     * to the caller; if evaluation of this predicate throws an exception, the
     * {@code other} predicate will not be evaluated.
     *
     * @param other a predicate that will be logically-ANDed with this
     *              predicate
     * @return a composed predicate that represents the short-circuiting logical
     * AND of this predicate and the {@code other} predicate
     * @throws NullPointerException if other is null
     */
    default SuspendablePredicate<T> and(SuspendablePredicate<? super T> other) {
        Objects.requireNonNull(other);
        SuspendablePredicate<T> self = this;
        return new SuspendablePredicate<T>() {
            @Override
            public @suspendable boolean test(T t) {
                return self.test(t) && other.test(t);
            }
        };
    }

    /**
     * Returns a predicate that represents the logical negation of this
     * predicate.
     *
     * @return a predicate that represents the logical negation of this
     * predicate
     */
    default SuspendablePredicate<T> negate() {
        SuspendablePredicate<T> self = this;
        return new SuspendablePredicate<T>() {
            @Override
            public @suspendable boolean test(T t) {
                return !self.test(t);
            }
        };
    }

    /**
     * Returns a composed predicate that represents a short-circuiting logical
     * OR of this predicate and another.  When evaluating the composed
     * predicate, if this predicate is {@code true}, then the {@code other}
     * predicate is not evaluated.
     *
     * <p>Any exceptions thrown during evaluation of either predicate are relayed
     * to the caller; if evaluation of this predicate throws an exception, the
     * {@code other} predicate will not be evaluated.
     *
     * @param other a predicate that will be logically-ORed with this
     *              predicate
     * @return a composed predicate that represents the short-circuiting logical
     * OR of this predicate and the {@code other} predicate
     * @throws NullPointerException if other is null
     */
    default SuspendablePredicate<T> or(SuspendablePredicate<? super T> other) {
        Objects.requireNonNull(other);
        SuspendablePredicate<T> self = this;
        return new SuspendablePredicate<T>() {
            @Override
            public @suspendable boolean test(T t) {
                return self.test(t) || other.test(t);
            }
        };
    }

    /**
     * Returns a predicate that tests if two arguments are equal according
     * to {@link Objects#equals(Object, Object)}.
     *
     * @param <T> the type of arguments to the predicate
     * @param targetRef the object reference with which to compare for equality,
     *               which may be {@code null}
     * @return a predicate that tests if two arguments are equal according
     * to {@link Objects#equals(Object, Object)}
     */
    static <T> SuspendablePredicate<T> isEqual(Object targetRef) {
        return new SuspendablePredicate<T>() {
            @Override
            public @suspendable boolean test(T t) {
                return (null == targetRef) ? t == null : targetRef.equals(t);
            }
        };
    }
}

