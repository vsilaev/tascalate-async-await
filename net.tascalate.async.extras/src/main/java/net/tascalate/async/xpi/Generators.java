package net.tascalate.async.xpi;

import static net.tascalate.async.CallContext.yield;

import java.time.Duration;

import java.util.Iterator;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.Scheduler;
import net.tascalate.async.Sequence;
import net.tascalate.async.async;

import net.tascalate.async.spi.CurrentCallContext;

import net.tascalate.concurrent.CompletableTask;

public class Generators {

    private Generators() {}
    
    @SafeVarargs
    public static <T> AsyncGenerator<T> concat(Sequence<? extends CompletionStage<T>>... sequences) {
        return concat(Stream.of(sequences));
    }
    
    public static <T> AsyncGenerator<T> concat(Iterable<? extends Sequence<? extends CompletionStage<T>>> sequences) {
        return concat(sequences.iterator());
    }
    
    public static <T> AsyncGenerator<T> concat(Stream<? extends Sequence<? extends CompletionStage<T>>> sequences) {
        return concat(sequences.iterator());
    }
    
    private static @async <T> AsyncGenerator<T> concat(Iterator<? extends Sequence<? extends CompletionStage<T>>> sequences) {
        while (sequences.hasNext()) {
            yield( sequences.next() );
        }
        return yield();
    }
    
    public static @async AsyncGenerator<Duration> delays(Duration duration) {
        Executor executor = new CurrentSchedulerExecutor(CurrentCallContext.scheduler());
        while (true) {
            yield( CompletableTask.delay(duration, executor) );
        }
    }
    
    public static @async AsyncGenerator<Duration> delays(long timeout, TimeUnit timeUnit) {
        Executor executor = new CurrentSchedulerExecutor(CurrentCallContext.scheduler());
        while (true) {
            yield( CompletableTask.delay(timeout, timeUnit, executor) );
        }
    }

    
    public static <T> Function<Sequence<? extends CompletionStage<T>>, PromisesSequence<T>> promisesSequence() {
        return DefaultPromisesSequence::new;
    }

    public static <T> Function<AsyncGenerator<T>, PromisesGenerator<T>> promisesGenerator() {
        return DefaultPromisesGenerator::new;
    }
    
    static class CurrentSchedulerExecutor implements Executor {
        private final Scheduler scheduler;
        CurrentSchedulerExecutor(Scheduler scheduler) {
            this.scheduler = scheduler;
        }
        
        @Override
        public void execute(Runnable command) {
            scheduler.schedule(command);
        }
    }
}
