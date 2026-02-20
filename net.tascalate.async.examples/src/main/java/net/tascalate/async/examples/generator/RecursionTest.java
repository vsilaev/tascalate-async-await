/**
 * Copyright 2015-2025 Valery Silaev (http://vsilaev.com)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.

 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.tascalate.async.examples.generator;

import static net.tascalate.async.CallContext.async;
import static net.tascalate.async.CallContext.await;
import static net.tascalate.async.AsyncGenerator.awaitValue;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.AsyncYield;
import net.tascalate.async.Scheduler;
import net.tascalate.async.SchedulerProvider;
import net.tascalate.async.Sequence;
import net.tascalate.async.async;
import net.tascalate.concurrent.Promise;
import net.tascalate.javaflow.SuspendableStream;

public class RecursionTest {

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Scheduler scheduler = Scheduler.interruptible(executor);
        
        for (int i = 0; i < 20; i++) {
            long start = System.nanoTime();
            long value = consumer(scheduler).get();
            double finish = System.nanoTime();
            System.out.println( value );
            System.out.println((finish - start) / 1_000_000_000 + " seconds");
            System.out.println((finish - start) / ITERATIONS + " ns each");
        }
        
        executor.shutdownNow();
    }
    
    @async static Promise<Long> consumer(@SchedulerProvider Scheduler scheduler) {
        /*
        System.out.println( StackRecorder.get().getRunnable().toString() );
        */
        /*
        try (SuspendableStream<Object> g = producer().stream().map$(awaitValue())) {
            g.forEach(NOP);
        }
        */
        long result = 0;
        try (AsyncGenerator<Long> g = producer(scheduler)) {
            CompletionStage<Long> pv = null;
            while ((pv = g.next()) != null) {
                result += await(pv);
            }
        }
        return async(result);
    }
    
    @async static AsyncGenerator<Long> producer(@SchedulerProvider Scheduler scheduler) {
        AsyncYield<Long> async = AsyncGenerator.start();
        /*
        System.out.println( StackRecorder.get().getRunnable().toString() );
        */
        for (int i = 0; i < ITERATIONS; i++) {
            
            async.yield(VALUE);
            
            //emit(Sequence.empty());
        }
        return async.yield();
    }
    
    private static Long VALUE = Long.valueOf(3);

    private static final int ITERATIONS = 10_000_000;
    private static final Consumer<Object> NOP = v -> {};
}
