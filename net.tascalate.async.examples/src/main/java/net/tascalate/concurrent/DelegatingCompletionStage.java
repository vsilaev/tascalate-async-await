package net.tascalate.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class DelegatingCompletionStage<T, D extends CompletionStage<T>> implements CompletionStage<T> {
    final protected D completionStage;

    protected DelegatingCompletionStage(final D delegate) {
        this.completionStage = delegate;
    }

    public <U> CompletionStage<U> thenApply(Function<? super T, ? extends U> fn) {
        return completionStage.thenApply(fn);
    }

    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return completionStage.thenApplyAsync(fn);
    }

    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return completionStage.thenApplyAsync(fn, executor);
    }

    public CompletionStage<Void> thenAccept(Consumer<? super T> action) {
        return completionStage.thenAccept(action);
    }

    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action) {
        return completionStage.thenAcceptAsync(action);
    }

    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return completionStage.thenAcceptAsync(action, executor);
    }

    public CompletionStage<Void> thenRun(Runnable action) {
        return completionStage.thenRun(action);
    }

    public CompletionStage<Void> thenRunAsync(Runnable action) {
        return completionStage.thenRunAsync(action);
    }

    public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
        return completionStage.thenRunAsync(action, executor);
    }

    public <U, V> CompletionStage<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return completionStage.thenCombine(other, fn);
    }

    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return completionStage.thenCombineAsync(other, fn);
    }

    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                      BiFunction<? super T, ? super U, ? extends V> fn, 
                                                      Executor executor) {
        
        return completionStage.thenCombineAsync(other, fn, executor);
    }

    public <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return completionStage.thenAcceptBoth(other, action);
    }

    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return completionStage.thenAcceptBothAsync(other, action);
    }

    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                         BiConsumer<? super T, ? super U> action, 
                                                         Executor executor) {
        
        return completionStage.thenAcceptBothAsync(other, action, executor);
    }

    public CompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return completionStage.runAfterBoth(other, action);
    }

    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return completionStage.runAfterBothAsync(other, action);
    }

    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return completionStage.runAfterBothAsync(other, action, executor);
    }

    public <U> CompletionStage<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return completionStage.applyToEither(other, fn);
    }

    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return completionStage.applyToEitherAsync(other, fn);
    }

    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                     Function<? super T, U> fn,
                                                     Executor executor) {
        
        return completionStage.applyToEitherAsync(other, fn, executor);
    }

    public CompletionStage<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return completionStage.acceptEither(other, action);
    }

    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return completionStage.acceptEitherAsync(other, action);
    }

    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                   Consumer<? super T> action,
                                                   Executor executor) {
        
        return completionStage.acceptEitherAsync(other, action, executor);
    }

    public CompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return completionStage.runAfterEither(other, action);
    }

    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return completionStage.runAfterEitherAsync(other, action);
    }

    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return completionStage.runAfterEitherAsync(other, action, executor);
    }

    public <U> CompletionStage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return completionStage.thenCompose(fn);
    }

    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return completionStage.thenComposeAsync(fn);
    }

    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return completionStage.thenComposeAsync(fn, executor);
    }

    public CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return completionStage.exceptionally(fn);
    }

    public CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return completionStage.whenComplete(action);
    }

    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return completionStage.whenCompleteAsync(action);
    }

    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return completionStage.whenCompleteAsync(action, executor);
    }

    public <U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return completionStage.handle(fn);
    }

    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return completionStage.handleAsync(fn);
    }

    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return completionStage.handleAsync(fn, executor);
    }

    public CompletableFuture<T> toCompletableFuture() {
        return completionStage.toCompletableFuture();
    }

}