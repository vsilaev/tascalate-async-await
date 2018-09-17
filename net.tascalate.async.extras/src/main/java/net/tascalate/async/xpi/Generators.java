package net.tascalate.async.xpi;

import static net.tascalate.async.CallContext.yield;

import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import net.tascalate.async.Generator;
import net.tascalate.async.Scheduler;
import net.tascalate.async.Sequence;
import net.tascalate.async.async;
import net.tascalate.async.spi.ActiveAsyncCall;

import net.tascalate.concurrent.CompletableTask;

public class Generators {

    private Generators() {}
    
    @SafeVarargs
    public static <T, F extends CompletionStage<T>> Generator<T> concat(Sequence<T, F>... sequences) {
        return concat(Stream.of(sequences));
    }
    
    public static <T, F extends CompletionStage<T>> Generator<T> concat(Iterable<? extends Sequence<T, F>> sequences) {
        return concat(sequences.iterator());
    }
    
    public static <T, F extends CompletionStage<T>> Generator<T> concat(Stream<? extends Sequence<T, F>> sequences) {
        return concat(sequences.iterator());
    }
    
    private static @async <T, F extends CompletionStage<T>> Generator<T> concat(Iterator<? extends Sequence<T, F>> sequences) {
        while (sequences.hasNext()) {
            yield( sequences.next() );
        }
        return yield();
    }
    
    public static @async Generator<Duration> delays(Duration duration) {
        Executor executor = new CurrentSchedulerExecutor(ActiveAsyncCall.scheduler());
        while (true) {
            yield( CompletableTask.delay(duration, executor) );
        }
    }
    
    public static @async Generator<Duration> delays(long timeout, TimeUnit timeUnit) {
        Executor executor = new CurrentSchedulerExecutor(ActiveAsyncCall.scheduler());
        while (true) {
            yield( CompletableTask.delay(timeout, timeUnit, executor) );
        }
    }

    
    public static <T> Function<Sequence<T, ? extends CompletionStage<T>>, PromisesSequence<T>> promisesSequence() {
        return DefaultPromisesSequence::new;
    }

    public static <T> Function<Generator<T>, PromisesGenerator<T>> promisesGenerator() {
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
