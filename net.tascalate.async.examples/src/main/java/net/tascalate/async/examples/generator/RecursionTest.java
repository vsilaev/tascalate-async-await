package net.tascalate.async.examples.generator;

import static net.tascalate.async.api.AsyncCall.async;
import static net.tascalate.async.api.AsyncCall.yield;

import java.util.function.Consumer;

import org.apache.commons.javaflow.core.StackRecorder;

import net.tascalate.async.api.Converters;
import net.tascalate.async.api.Generator;
import net.tascalate.async.api.SuspendableStream;
import net.tascalate.async.api.async;
import net.tascalate.concurrent.Promise;

public class RecursionTest {

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        System.out.println( consumer().get() );
        double finish = System.currentTimeMillis();
        System.out.println((finish - start) / 1000 + " seconds");
        System.out.println((finish - start) / 10_000 + " ns each");
    }
    
    @async static Promise<String> consumer() {
        System.out.println( StackRecorder.get().getRunnable().toString() );
        try (SuspendableStream<Object> g = producer().stream().mapAwaitable(Converters.readyValues())) {
            g.forEach(NOP);
        }
        return async("Done");
    }
    
    @async static Generator<Object> producer() {
        System.out.println( StackRecorder.get().getRunnable().toString() );        
        for (int i = 0; i < 10_000_000; i++) {
            /*
            yield("");
            */
            yield(Generator.empty());
        }
        return yield();
    }

    private static final Consumer<Object> NOP = v -> {};
}
