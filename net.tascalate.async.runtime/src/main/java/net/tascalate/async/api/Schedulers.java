package net.tascalate.async.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import net.tascalate.async.api.Scheduler.Characteristics;
import net.tascalate.concurrent.CompletableTask;

public class Schedulers {
    private Schedulers() {}
    
    static private final Scheduler SAME_THREAD_SCHEDULER = create(Runnable::run);
    
    public static Scheduler sameThreadContextless() {
        return SAME_THREAD_SCHEDULER;
    }
    
    public static Scheduler create(Executor executor) {
        return create(executor, Collections.emptySet());
    }
    
    public static Scheduler create(Executor executor, Set<Characteristics> characteristics) {
        return create(executor, characteristics, Function.identity());
    }
    
    public static Scheduler create(Executor executor, Function<? super Runnable, ? extends Runnable> contextualizer) {
        return create(executor, EnumSet.of(Characteristics.INTERRUPTIBLE), contextualizer);
    }
    
    public static Scheduler create(Executor executor, Set<Characteristics> characteristics, Function<? super Runnable, ? extends Runnable> contextualizer) {
        if (characteristics.contains(Characteristics.INTERRUPTIBLE)) {
            return createInterruptibleScheduler(executor, characteristics, contextualizer);
        } else {
            return createNonInterruptibleScheduler(executor, characteristics, contextualizer);
        }
    }   
    
    private static Scheduler createInterruptibleScheduler(Executor executor, Set<Characteristics> characteristics, Function<? super Runnable, ? extends Runnable> contextualizer) {
        return new AbstractScheduler(characteristics, contextualizer) {
            @Override
            public CompletionStage<?> schedule(Runnable command) {
                return CompletableTask.runAsync(command, executor);
            }
        };
    }
    
    private static Scheduler createNonInterruptibleScheduler(Executor executor, Set<Characteristics> characteristics, Function<? super Runnable, ? extends Runnable> contextualizer) {
        return new AbstractScheduler(characteristics, contextualizer) {
            @Override
            public CompletionStage<?> schedule(Runnable command) {
                CompletableFuture<?> result = new CompletableFuture<>();
                executor.execute(() -> {
                    try {
                        command.run();
                        result.complete(null);
                    } catch (final Throwable ex) {
                        result.completeExceptionally(ex);
                    }
                });
                return result;
            }
        };
    }
    
    abstract static class AbstractScheduler implements Scheduler {
        private final Set<Characteristics> characteristics;
        private final Function<? super Runnable, ? extends Runnable> contextualizer;
        
        AbstractScheduler(Set<Characteristics> characteristics, Function<? super Runnable, ? extends Runnable> contextualizer) {
            this.characteristics = characteristics;
            this.contextualizer  = contextualizer;
        }
        
        @Override
        public Set<Characteristics> characteristics() {
            return characteristics;
        }
        
        @Override
        public Runnable contextualize(Runnable resumeContinuation) {
            return contextualizer == null ? resumeContinuation : contextualizer.apply(resumeContinuation);
        }        
    }
}
