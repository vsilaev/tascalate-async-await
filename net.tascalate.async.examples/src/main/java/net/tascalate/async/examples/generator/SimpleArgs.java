package net.tascalate.async.examples.generator;

import static net.tascalate.async.CallContext.async;
import static net.tascalate.async.CallContext.await;

import java.util.Date;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import net.tascalate.async.Scheduler;
import net.tascalate.async.SchedulerProvider;
import net.tascalate.async.Sequence;
import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.async;
import net.tascalate.async.extras.TaskScheduler;
import net.tascalate.async.spi.CurrentCallContext;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.Promises;

public class SimpleArgs extends SamePackageSubclass {
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
        Scheduler scheduler = Scheduler.interruptible(executor);
        //SimpleArgs.scheduler = scheduler;
        final SimpleArgs example = new SimpleArgs(scheduler);
        CompletionStage<?> f1 = example.outerCall("ABC"/*, scheduler*/);
        CompletionStage<?> f2 = example.outerCallExplicit("|", new TaskScheduler(executor), 10);
        f1.thenCombine(f2, (a, b) -> {
            System.out.println("==>" + a);
            System.out.println("==>" + b);
            executor.shutdownNow();
            return "";
        });
    }

    @SchedulerProvider
    final Scheduler scheduler;
    
    SimpleArgs(Scheduler scheduler) {
        this.scheduler = scheduler;
    }
    
    @async CompletionStage<Date> outerCall(String abs/*, @SchedulerProvider Scheduler scheduler*/) {
        Integer x = Integer.valueOf(10);
        x.hashCode();
        System.out.println("Outer call, current scheduler - " + CurrentCallContext.scheduler());
        System.out.println("Outer call, thread : " + Thread.currentThread().getName());
        System.out.println(abs + " -- " + x + ", " + scheduler);
        System.out.println("Inherited method (other package) " + inheritedMethod(10));
        System.out.println("Inherited method (same package) " + samePackageMethod(10));
        System.out.println("Inherited method (public method) " + super.publicMethod());
        System.out.println("Inherited field (other package) " + inheritedField);
        System.out.println("Inherited field (same package) " + samePackageField);
        System.out.println("Inherited field (public field) " + publicField);
        await(innerCall());
        return async(new Date());
    }
    
    @async CompletionStage<String> innerCall() {
        System.out.println("Inner call, current scheduler - " + CurrentCallContext.scheduler());
        String v = await(CompletableFuture.supplyAsync(() -> "XYZ", executor));
        System.out.println("Inner call, thread : " + Thread.currentThread().getName());
        System.out.println(v);
        return async("Done");
    }

    @async
     Promise<String> outerCallExplicit(String delimeter, @SchedulerProvider Scheduler scheduler, int zz) {
        System.out.println("Outer call explicit, current scheduler - " + CurrentCallContext.scheduler());
        System.out.println("Outer call explicit, thread : " + Thread.currentThread().getName());
        await(innerCallImplicit());
        
        StringJoiner joiner = new StringJoiner(delimeter);
        try (Sequence<Promise<String>> generator = AsyncGenerator.from("ABC", "KLM", "XYZ").stream().map(Promises::from).convert(Sequence.fromStream())) {
            System.out.println("%%MergeStrings - before iterations");
            CompletionStage<String> singleResult; 
            while (null != (singleResult = generator.next())) {
                //System.out.println(">>Future is ready: " + Future.class.cast(singleResult).isDone());
                String v = await(singleResult);
                System.out.println("Thread in B: " + Thread.currentThread().getName());
                System.out.println("Received: " + v);
                joiner.add(v);
            }
        }

        return async(joiner.toString());
    }
    
    @async
     Promise<String> innerCallImplicit() {
        System.out.println("Inner call explicit, current scheduler - " + CurrentCallContext.scheduler());
        String v = await(CompletableFuture.supplyAsync(() -> "XYZ", executor));
        System.out.println("Inner call explicit, thread : " + Thread.currentThread().getName());
        System.out.println(v);
        return async("Done");
    }
}
