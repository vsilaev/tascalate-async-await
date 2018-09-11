package net.tascalate.async.examples.generator;

import static net.tascalate.async.CallContext.async;
import static net.tascalate.async.CallContext.yield;
import static net.tascalate.async.CallContext.awaitValue;

import java.util.function.Consumer;

import org.apache.commons.javaflow.core.StackRecorder;

import net.tascalate.async.Generator;
import net.tascalate.async.Sequence;
import net.tascalate.async.async;

import net.tascalate.concurrent.Promise;
import net.tascalate.javaflow.SuspendableStream;

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
        try (SuspendableStream<Object> g = producer().stream().map$(awaitValue())) {
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
            yield(Sequence.empty());
        }
        return yield();
    }

    private static final Consumer<Object> NOP = v -> {};
}
