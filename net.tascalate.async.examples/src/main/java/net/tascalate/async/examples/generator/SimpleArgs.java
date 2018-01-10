package net.tascalate.async.examples.generator;


import static net.tascalate.async.api.AsyncCall.async;
import static net.tascalate.async.api.AsyncCall.await;
import static net.tascalate.async.xpi.PromisesGenerator.promises;

import java.util.Date;
import java.util.StringJoiner;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import net.tascalate.async.api.Generator;
import net.tascalate.async.api.Scheduler;
import net.tascalate.async.api.SchedulerProvider;
import net.tascalate.async.api.async;
import net.tascalate.async.xpi.PromisesGenerator;
import net.tascalate.async.xpi.TaskScheduler;
import net.tascalate.concurrent.Promise;

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
        //final SimpleArgs example = new SimpleArgs();
        //CompletionStage<?> f = example.testArgs("ABC", Scheduler.from(executor, true));
        CompletionStage<?> f = SimpleArgs.mergeStrings("|", new TaskScheduler(executor), 10);
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
        return async(new Date());
    }

    @async
    static Promise<String> mergeStrings(String delimeter, @SchedulerProvider Scheduler scheduler, int zz) {
        StringJoiner joiner = new StringJoiner(delimeter);
        try (PromisesGenerator<String> generator = Generator.of("ABC", "XYZ").as(promises())) {
            System.out.println("%%MergeStrings - before iterations");
            String param = "GO!";
            int i = 0;
            CompletionStage<String> singleResult; 
            while (null != (singleResult = generator.next(param))) {
                //System.out.println(">>Future is ready: " + Future.class.cast(singleResult).isDone());
                String v = await(singleResult);
                System.out.println(Thread.currentThread().getName());
                System.out.println("Received: " + v + ", " + param);
                ++i;
                zz++;
                if (i > 0) param = "VAL #" + i;
                joiner.add(v);
                if (i == 17) {
                    break;
                }
            }
        }

        return async(joiner.toString());
    }
}
