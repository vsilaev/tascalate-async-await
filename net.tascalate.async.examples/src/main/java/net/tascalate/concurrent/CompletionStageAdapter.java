package net.tascalate.concurrent;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

abstract public class CompletionStageAdapter<T>  implements CompletionStage<T> {
	protected static final Executor SAME_THREAD_EXECUTOR = new Executor() {
		public void execute(Runnable command) {
			command.run();
		}

		public String toString() {
			return "SAME_THREAD_EXECUTOR";
		}
	};
	private final Executor defaultExecutor;

	CompletionStageAdapter(Executor defaultExecutor) {
		this.defaultExecutor = defaultExecutor;
	}

	public <U> CompletionStage<U> thenApply(Function<? super T, ? extends U> fn) {
		return thenApplyAsync(fn, SAME_THREAD_EXECUTOR);
	}

	public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
		return thenApplyAsync(fn, this.defaultExecutor);
	}

	public CompletionStage<Void> thenAccept(Consumer<? super T> action) {
		return thenAcceptAsync(action, SAME_THREAD_EXECUTOR);
	}

	public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action) {
		return thenAcceptAsync(action, this.defaultExecutor);
	}

	public CompletionStage<Void> thenRun(Runnable action) {
		return thenRunAsync(action, SAME_THREAD_EXECUTOR);
	}

	public CompletionStage<Void> thenRunAsync(Runnable action) {
		return thenRunAsync(action, this.defaultExecutor);
	}

	public <U, V> CompletionStage<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
		return thenCombineAsync(other, fn, SAME_THREAD_EXECUTOR);
	}

	public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
		return thenCombineAsync(other, fn, this.defaultExecutor);
	}

	public <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
		return thenAcceptBothAsync(other, action, SAME_THREAD_EXECUTOR);
	}

	public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
		return thenAcceptBothAsync(other, action, this.defaultExecutor);
	}

	public CompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
		return runAfterBothAsync(other, action, SAME_THREAD_EXECUTOR);
	}

	public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
		return runAfterBothAsync(other, action, this.defaultExecutor);
	}

	public <U> CompletionStage<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
		return applyToEitherAsync(other, fn, SAME_THREAD_EXECUTOR);
	}

	public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
		return applyToEitherAsync(other, fn, this.defaultExecutor);
	}

	public CompletionStage<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
		return acceptEitherAsync(other, action, SAME_THREAD_EXECUTOR);
	}

	public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
		return acceptEitherAsync(other, action, this.defaultExecutor);
	}

	public CompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
		return runAfterEitherAsync(other, action, SAME_THREAD_EXECUTOR);
	}

	public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
		return runAfterEitherAsync(other, action, this.defaultExecutor);
	}

	public <U> CompletionStage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
		return thenComposeAsync(fn, SAME_THREAD_EXECUTOR);
	}

	public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
		return thenComposeAsync(fn, this.defaultExecutor);
	}

	public CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
		return whenCompleteAsync(action, SAME_THREAD_EXECUTOR);
	}

	public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
		return whenCompleteAsync(action, this.defaultExecutor);
	}

	public <U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
		return handleAsync(fn, SAME_THREAD_EXECUTOR);
	}

	public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
		return handleAsync(fn, this.defaultExecutor);
	}

	protected final Executor getDefaultExecutor() {
		return this.defaultExecutor;
	}
}
