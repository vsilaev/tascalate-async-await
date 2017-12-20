package net.tascalate.async.examples.generator;

import static net.tascalate.async.api.AsyncCall.asyncResult;
import static net.tascalate.async.api.AsyncCall.await;

import java.util.Date;
import java.util.StringJoiner;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import net.tascalate.async.api.AsyncCall;
import net.tascalate.async.api.Generator;
import net.tascalate.async.api.Scheduler;
import net.tascalate.async.api.SchedulerProvider;
import net.tascalate.async.api.async;

public class SimpleArgs {
    final private static AtomicLong idx = new AtomicLong(0);
    final private static ExecutorService executor = Executors.newFixedThreadPool(4, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread result = Executors.defaultThreadFactory().newThread(r);
            result.setName("ABC-ARGS_TEST" + idx.getAndIncrement());
            return result;
        }
    });

    public static void main(String[] args) {
        final SimpleArgs example = new SimpleArgs();
        //CompletionStage<?> f = example.testArgs("ABC", Scheduler.from(executor, true));
        CompletionStage<?> f = example.mergeStrings("|", Scheduler.from(executor, true), 10);
        f.whenComplete((r, e) -> {
            System.out.println(r);
            executor.shutdownNow();
        });
    }

    @async CompletionStage<Date> testArgs(String abs, @SchedulerProvider Scheduler scheduler) {
        Integer x = Integer.valueOf(10);
        x.hashCode();
        System.out.println(Thread.currentThread().getName());
        System.out.println(abs + " -- " + x + ", " + scheduler);
        return AsyncCall.asyncResult(new Date());
    }
    
    //vin.var - argTypes.length + thisShiftNecessary + thisWasInOriginal
    // oldThis - 0, delimeter - 1, scheduler - 2, joiner - 3, generator - 4, param - 5,  i - 6, single result - 7
    // thisShift
    @async
    CompletionStage<String> mergeStrings(String delimeter, @SchedulerProvider Scheduler scheduler, int zz) {
        StringJoiner joiner = new StringJoiner(delimeter);
        System.out.println(Thread.currentThread().getName());
        try (Generator<String> generator = Generator.of("ABC", "XYZ")) {
            System.out.println("%%MergeStrings - before iterations");
            String param = "GO!";
            int i = 0;
            CompletionStage<String> singleResult; 
            while (null != (singleResult = generator.next(param))) {
                //System.out.println(">>Future is ready: " + Future.class.cast(singleResult).isDone());
                String v = await(singleResult);
                System.out.println("Received: " + v + ", " + param);
                ++i;
                if (i > 0) param = "VAL #" + i;
                joiner.add(v);
                if (i == 17) {
                    break;
                }
            }
        }
        return asyncResult(joiner.toString());
    }
}
