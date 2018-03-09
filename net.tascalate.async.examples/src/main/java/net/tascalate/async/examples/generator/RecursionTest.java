package net.tascalate.async.examples.generator;

import static net.tascalate.async.api.AsyncCall.async;
import static net.tascalate.async.api.AsyncCall.yield;

import org.apache.commons.javaflow.core.StackRecorder;

import net.tascalate.async.api.Generator;
import net.tascalate.async.api.ValuesGenerator;
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
        try (ValuesGenerator<Object> g = producer().values()) {
            while (g.hasNext()) {
                @SuppressWarnings("unused")
                Object v = g.next();
            }
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

}
