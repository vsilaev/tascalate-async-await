package net.tascalate.async.examples.generator;

import static net.tascalate.async.api.AsyncCall.async;
import static net.tascalate.async.api.AsyncCall.await;
import static net.tascalate.async.api.AsyncCall.yield;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.tascalate.async.api.Generator;
import net.tascalate.async.api.YieldReply;
import net.tascalate.async.api.async;
import net.tascalate.concurrent.CompletableTask;
import net.tascalate.concurrent.Promise;

public class ExceptionsTest {
    final private static ExecutorService executor = Executors.newFixedThreadPool(4);
    
    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        try {
            System.out.println( consumer().get() );
        } finally {
            executor.shutdownNow();
        }
        double finish = System.currentTimeMillis();
        System.out.println((finish - start) / 1000 + " seconds");
    }
    
    @async static Promise<String> consumer() {
        try (Generator<Object> g = producer()) {
            CompletionStage<Object> f = null;
            while (null != (f = g.next())) {
                System.out.println("Consumed future: " + f);
                try {
                    System.out.println("RESULT: " + await(f));
                } catch (IllegalArgumentException ex) {
                    System.out.println("Consumed exception (await): " + ex);
                }
                System.out.println(f);
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("Consumed exception (next): " + ex);
        }

        return async("Done");
    }
    
    @async static Generator<Object> producer() {
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                YieldReply<String> reply = yield(waitString("VALUE " + i, 100));
                System.out.println("REPLY AFTER NORMAL: " + reply);
            } else {
                try {
                    YieldReply<String> reply = yield(waitError(100));
                    System.out.println("REPLY AFTER ERROR: " + reply);
                } catch (IllegalArgumentException ex) {
                    System.out.println("EXCEPTION ON iter#" + i + ": " + ex);
                    //throw ex;
                }
            }
        }
        return yield();
    }

    
    static CompletionStage<String> waitString(final String value, final long delay) {
        final CompletionStage<String> promise = CompletableTask.supplyAsync(() -> {
            try { 
                Thread.sleep(delay);
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CompletionException(ex);
            }
            return value;
        }, executor);
        return promise;
    }
    
    static CompletionStage<String> waitError(final long delay) {
        final CompletionStage<String> promise = CompletableTask.supplyAsync(() -> {
            try { 
                Thread.sleep(delay);
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CompletionException(ex);
            }
            throw new IllegalArgumentException("Just for fun!");
        }, executor);
        return promise;
    }
}
