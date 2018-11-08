/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
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
import static net.tascalate.async.CallContext.interrupted;
import static net.tascalate.async.CallContext.yield;

import java.io.FileNotFoundException;

import java.util.Date;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.Sequence;
import net.tascalate.async.YieldReply;
import net.tascalate.async.async;
import net.tascalate.async.suspendable;

import net.tascalate.concurrent.CompletableTask;

import net.tascalate.javaflow.SuspendableIterator;

public class GeneratorExample {

    final private static ExecutorService executor = Executors.newFixedThreadPool(4);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        final GeneratorExample example = new GeneratorExample();
        example.asyncOperation();
        final CompletionStage<String> result1 = example.mergeStrings(", ");
        final CompletionStage<String> result2 = example.iterateStringsEx(11);
        
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
    CompletableFuture<String> mergeStrings(String delimeter) {
        StringJoiner joiner = new StringJoiner(", ");
        try (AsyncGenerator<String> generator = produceStrings()) {
        	System.out.println("%%MergeStrings - before iterations");
            String param = "GO!";
            int i = 0;
            CompletionStage<String> singleResult; 
            while (null != (singleResult = generator.next(param))) {
            	System.out.println(">>Future is ready: " + Future.class.cast(singleResult).isDone());
            	String v = await(singleResult);
                System.out.println("Received: " + v);
                ++i;
                param = "VAL #" + i + "(AFTER " + v + ")";
                joiner.add(v);
                if (i == 17) {
                    break;
                }
            }
        }
        return async(joiner.toString());
    }
    
    @async
    CompletionStage<String> iterateStringsEx(int z) {
        z += 2;
        System.out.println(z);
        int x = 3;
        x++;
        if (x < z) {
            x += 5;
        }
        System.out.println(x);
        
        try (SuspendableIterator<String> values = moreStringsEx().valuesIterator()) {
            
            while (values.hasNext()) {
                String v = values.next();
                System.out.println("+++Received: " + v);
            }
        } catch (FileNotFoundException | IllegalArgumentException ex) {
            System.out.println("EXCEPTION!!!!");
            return async("ERROR: " + ex);
        }
        return async("NO ERROR");
    }
    
    @async
    void asyncOperation() {
        System.out.println("Before await!");
        System.out.println("Done");
        await( waitString("111") );
        System.out.println("Is async interrupted: " + interrupted());
        System.out.println("CONTINUABLE WAIT: " + continuableWait());
        System.out.println("After await!");
    }
    
    public @suspendable String continuableWait() {
        System.out.println("Is suspendable interrupted: " + interrupted());
        return await( waitString("ZZZ") );
    }
    
    @async
    AsyncGenerator<String> produceStrings() {
       
    	System.out.println("%%ProduceStrings - starting + ");
        YieldReply<String> o;
        
        o = yield(Sequence.empty());
        System.out.println("INITIAL PARAM: " + o.param);
        
        o = yield(waitString("ABC"));
        System.out.println("Processed: " + o + ", " + new Date());
        
        o = yield( AsyncGenerator.readyFirst(waitString("PV-1", 2000L), waitString("PV-2", 1500L), waitString("PV-3", 1000L)) );
        System.out.println("AFTER LIST PENDING: " + o);

        String s = await(waitString("InternalAsync"));
        System.out.println("INTERNALLY: " + s);

        o = yield(Sequence.empty());
        System.out.println("AFTER EMPTY: " + o);
        
        o = yield(AsyncGenerator.from("RV-1", "RV-2", "RV-3"));
        System.out.println("AFTER LIST READY: " + o);

        System.out.println("Is generator interrupted: " + interrupted());
        
        o = yield(waitString("DEF"));
        System.out.println("Processed: " + o + ", " + new Date());

        o = yield("NO-WAIT");
        System.out.println("Processed: " + o + ", " + new Date());

        yield(chainedGenerator());

        try (AsyncGenerator<String> nested = moreStrings()) {
        	CompletionStage<String> singleResult; 
            while (null != (singleResult = nested.next())) {
            	String v = await(singleResult);
                System.out.println("Nested: " + v);
                if (Integer.parseInt(v) % 2 == 0) {
                    o = yield(waitString("NESTED-" + v));
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
    private AsyncGenerator<String> moreStrings() {
        yield(waitString("111"));
        yield(waitString("222"));
        yield("333");
        yield(waitString("444"));
        System.out.println("::moreStrings FINALLY CALLED::");
        return yield();
    }
    
    @async
    private AsyncGenerator<String> moreStringsEx() throws FileNotFoundException {
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
    AsyncGenerator<String> chainedGenerator() {
        yield(waitString("CHAINED-1"));
        yield(waitString("CHAINED-2"));
        yield("CHAINED-3");
        yield(waitString("CHAINED-4"));

        System.out.println("::chainedGenerator FINALLY CALLED::");
        return yield();
    }
    
    static CompletionStage<String> waitString(final String value) {
        return waitString(value, 250L);
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
