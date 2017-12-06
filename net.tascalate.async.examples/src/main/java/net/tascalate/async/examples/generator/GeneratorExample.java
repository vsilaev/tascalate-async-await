package net.tascalate.async.examples.generator;

import static net.tascalate.async.api.AsyncCall.asyncResult;
import static net.tascalate.async.api.AsyncCall.yield;
import static net.tascalate.async.api.AsyncCall.await;

import java.io.FileNotFoundException;
import java.util.Date;
import java.util.StringJoiner;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.tascalate.async.api.Generator;
import net.tascalate.async.api.async;
import net.tascalate.concurrent.CompletableTask;

public class GeneratorExample {

    final private static ExecutorService executor = Executors.newFixedThreadPool(4);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        final GeneratorExample example = new GeneratorExample();
        example.asyncOperation();
        final CompletionStage<String> result1 = example.mergeStrings();
        final CompletionStage<String> result2 = example.iterateStringsEx();
        
        result2.thenCombine(result1, (v1, v2) -> "\n" + v1 + "\n" + v2)
        .whenComplete((v, e) -> {
            long finishTime = System.currentTimeMillis();
            if (null == e) {
                System.out.println("Calculates: " + v + "\nTask take " + (finishTime - startTime) + "ms");
            } else {
                e.printStackTrace(System.err);
            }
            executor.submit(executor::shutdown);
        });
    }

    @async
    CompletionStage<String> mergeStrings() {
        StringJoiner joiner = new StringJoiner(", ");
        try (Generator<String> generator = produceStrings()) {
            String param = "GO!";
            int i = 0;
            while (generator.next(param)) {
                System.out.println("Received: " + generator.current());
                param = "VAL #" + ++i;
                joiner.add(generator.current());
                if (i == 17) {
                    break;
                }
            }
        }
        return asyncResult(joiner.toString());
    }
    
    @async
    CompletionStage<String> iterateStringsEx() {
        try (Generator<String> generator = moreStringsEx()) {
            while (generator.next()) {
                System.out.println("Received: " + generator.current());
            }
        } catch (FileNotFoundException | IllegalArgumentException ex) {
            System.out.println("EXCEPTION!!!!");
            return asyncResult("ERROR: " + ex);
        }
        return asyncResult("NO ERROR");
    }
    
    @async
    void asyncOperation() {
        System.out.println("Before await!");
        System.out.println("Done");
        await( waitString("111") );
        System.out.println("After await!");
    }
    
    @async
    Generator<String> produceStrings() {
        Object o;
        o = yield(waitString("ABC"));
        System.out.println("Processed: " + o + ", " + new Date());

        String s = await(waitString("InternalAsync"));
        System.out.println("INTERNALLY: " + s);

        o = yield(Generator.empty());
        System.out.println("AFTER EMPTY: " + o);
        
        o = yield(Generator.produce("RV-1", "RV-2", "RV-3"));
        System.out.println("AFTER LIST READY: " + o);
        
        o = yield(Generator.await(waitString("PV-1", 100L), waitString("PV-2", 100L), waitString("PV-3", 200L)));
        System.out.println("AFTER LIST PENDING: " + o);

        
        o = yield(waitString("DEF"));
        System.out.println("Processed: " + o + ", " + new Date());

        o = yield("NO-WAIT");
        System.out.println("Processed: " + o + ", " + new Date());

        yield(chainedGenerator());

        try (Generator<String> nested = moreStrings()) {
            while (nested.next()) {
                System.out.println("Nested: " + nested.current());
                if (Integer.parseInt(nested.current()) % 2 == 0) {
                    o = yield(waitString("NESTED-" + nested.current()));
                    System.out.println("Nested Processed: " + o + ", " + new Date());
                }
            }
        }

        String x;
        yield(x = await(waitString("AWYV")));

        System.out.println("Awaited&Yielded:" + x);

        o = yield(waitString("XYZ"));
        System.out.println("Processed Final: " + o + ", " + new Date());

        o = yield(waitString("SHOULD BE SKIPPEDM IN OUTOUT"));

        System.out.println("::produceStrings FINALLY CALLED::");
        return yield();
    }

    // Private to ensure that generated accessor methods work 
    @async
    private Generator<String> moreStrings() {
        yield(waitString("111"));
        yield(waitString("222"));
        yield("333");
        yield(waitString("444"));
        System.out.println("::moreStrings FINALLY CALLED::");
        return yield();
    }
    
    @async
    private Generator<String> moreStringsEx() throws FileNotFoundException {
        yield(waitString("111"));
        yield(waitString("222"));
        yield("333");
        // Comment out to check synchronously thrown exception
        // Below is asynchronously sent one
        yield(waitError(1));
        yield(waitString("444"));
        throw new FileNotFoundException();
    }

    @async
    Generator<String> chainedGenerator() {
        yield(waitString("CHAINED-1"));
        yield(waitString("CHAINED-2"));
        yield("CHAINED-3");
        yield(waitString("CHAINED-4"));

        System.out.println("::chainedGenerator FINALLY CALLED::");
        return yield();
    }
    
    static CompletionStage<String> waitString(final String value) {
        return waitString(value, 150L);
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
