package net.tascalate.async.examples.generator;

import static net.tascalate.async.api.AsyncCall.asyncResult;
//import static net.tascalate.async.api.AsyncCall.await;

import java.util.Date;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.api.Generator;
import net.tascalate.async.api.async;
import net.tascalate.async.core.AsyncExecutor;
import net.tascalate.async.core.AsyncGenerator;

public class GeneratorExample {

    final private static ExecutorService executor = Executors.newFixedThreadPool(4);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        final CompletionStage<String> result = new GeneratorExample().mergeStrings();
        result.whenComplete((v, e) -> {
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

    @continuable
    Generator<String> produceStrings() {
        final AsyncGenerator<String> method = new AsyncGenerator<String>() {

            @Override
            public void doRun() {
                Object o;
                o = $$yield$$(waitString("ABC"), this);
                System.out.println("Processed: " + o + ", " + new Date());

                String s = $$await$$(waitString("InternalAsync"), this);
                System.out.println("INTERNALLY: " + s);

                o = $$yield$$(Generator.empty(), this);
                System.out.println("AFTER EMPTY: " + o);
                
                o = $$yield$$(Generator.produce("RV-1", "RV-2", "RV-3"), this);
                System.out.println("AFTER LIST READY: " + o);
                
                o = $$yield$$(Generator.await(waitString("PV-1", 100L), waitString("PV-2", 100L), waitString("PV-3", 200L)), this);
                System.out.println("AFTER LIST PENDING: " + o);

                
                o = $$yield$$(waitString("DEF"), this);
                System.out.println("Processed: " + o + ", " + new Date());

                o = $$yield$$("NO-WAIT", this);
                System.out.println("Processed: " + o + ", " + new Date());

                $$yield$$(chainedGenerator(), this);

                try (Generator<String> nested = moreStrings()) {
                    while (nested.next()) {
                        System.out.println("Nested: " + nested.current());
                        if (Integer.parseInt(nested.current()) % 2 == 0) {
                            o = $$yield$$(waitString("NESTED-" + nested.current()), this);
                            System.out.println("Nested Processed: " + o + ", " + new Date());
                        }
                    }
                }

                String x;
                $$yield$$(x = $$await$$(waitString("AWYV"), this), this);

                System.out.println("Awaited&Yielded:" + x);

                o = $$yield$$(waitString("XYZ"), this);
                System.out.println("Processed Final: " + o + ", " + new Date());

                o = $$yield$$(waitString("SHOULD BE SKIPPEDM IN OUTOUT"), this);

                System.out.println("::produceStrings FINALLY CALLED::");
            }

        };
        AsyncExecutor.execute(method);
        return method.generator;
    }

    @continuable
    Generator<String> moreStrings() {
        final AsyncGenerator<String> method = new AsyncGenerator<String>() {
            @Override
            public void doRun() {
                $$yield$$(waitString("111"), this);
                $$yield$$(waitString("222"), this);
                $$yield$$("333", this);
                $$yield$$(waitString("444"), this);
                System.out.println("::moreStrings FINALLY CALLED::");
            }
        };
        AsyncExecutor.execute(method);
        return method.generator;
    }

    @continuable
    Generator<String> chainedGenerator() {
        final AsyncGenerator<String> method = new AsyncGenerator<String>() {
            @Override
            public void doRun() {
                $$yield$$(waitString("CHAINED-1"), this);
                $$yield$$(waitString("CHAINED-2"), this);
                $$yield$$("CHAINED-3", this);
                $$yield$$(waitString("CHAINED-4"), this);

                System.out.println("::chainedGenerator FINALLY CALLED::");
            }
        };
        AsyncExecutor.execute(method);
        return method.generator;
    }

    private CompletionStage<String> waitString(final String value) {
        return waitString(value, 150L);
    }
    
    private CompletionStage<String> waitString(final String value, final long delay) {
        final CompletableFuture<String> promise = CompletableFuture.supplyAsync(() -> {
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
}
