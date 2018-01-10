package net.tascalate.async.examples.generator;

import net.tascalate.async.api.Generator;
import net.tascalate.async.api.ValuesGenerator;

import static net.tascalate.async.api.AsyncCall.*;
import net.tascalate.async.api.async;
import net.tascalate.concurrent.Promise;

public class RecursionTest {

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        System.out.println( consumer().get() );
        long finish = System.currentTimeMillis();
        System.out.println((finish - start) / 1000 + " seconds");
        System.out.println((finish - start) / 10_000 + " ns each");
    }
    
    @async static Promise<String> consumer() {
        try (ValuesGenerator<Object> g = producer().values()) {
            while (g.hasNext()) {
                @SuppressWarnings("unused")
                Object v = g.next();
            }
        }
        return async("Done");
    }
    
    @async static Generator<Object> producer() {
        for (int i = 0; i < 10_000_000; i++) {
            yield("");
            /* 
             * If you replace yield("") with yielding empty generator 
             * then this will cause stack overflow after 3120-3168 iterations
             * this is an edge case with LazyGenerator.next(param)
            yield(Generator.empty());
            */
        }
        return yield();
    }

}
