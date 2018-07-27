package net.tascalate.async.function;

import java.util.Objects;

import net.tascalate.async.api.suspendable;

/**
 * Represents an operation that accepts a single input argument and returns no
 * result. Unlike most other functional interfaces, {@code SuspendableConsumer} is expected
 * to operate via side-effects.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #accept(Object)}.
 *
 * @param <T> the type of the input to the operation
 *
 */
@FunctionalInterface
public interface SuspendableConsumer<T> {

    /**
     * Performs this operation on the given argument.
     *
     * @param t the input argument
     */
    @suspendable void accept(T t);

    /**
     * Returns a composed {@code Consumer} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the
     * composed operation.  If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code Consumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default SuspendableConsumer<T> andThen(SuspendableConsumer<? super T> after) {
        Objects.requireNonNull(after);
        SuspendableConsumer<T> self = this;
        return new SuspendableConsumer<T>() {
            @Override
            public @suspendable void accept(T t) {
                self.accept(t);
                after.accept(t);
            }
        };
    }
}