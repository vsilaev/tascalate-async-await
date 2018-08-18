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

import static net.tascalate.async.api.AsyncCall.await;
import static net.tascalate.async.api.AsyncCall.async;
import static net.tascalate.async.api.AsyncCall.yield;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.tascalate.async.api.Converters;
import net.tascalate.async.api.Generator;
import net.tascalate.async.api.SuspendableStream;
import net.tascalate.async.api.async;
import net.tascalate.async.api.suspendable;
import net.tascalate.concurrent.CompletableTask;
import net.tascalate.concurrent.Promises;

public class StreamTest {

    final private static ExecutorService executor = Executors.newFixedThreadPool(4);

    public static void main(String[] args) {
        final StreamTest example = new StreamTest();
        example.div = 2;
        example.asyncOperation(2).toCompletableFuture().join();
        executor.shutdown();
    }
    
    int div;
    
    boolean isEven(String v) {
        return Integer.parseInt(v.substring(0, 3)) % 2 == 0;
    }

    public @suspendable String waitFuture(CompletionStage<String> f) {
        return await(f); 
    }

    /*
    void print(Object v) {
        System.out.println(v);
    }*/

    @async
    public CompletionStage<Void> asyncOperation(int outerDiv) {
        produceMergedStrings()
            .stream()  
            //.mapAwaitable(f -> await(f))      // -- worked, static
            //.mapAwaitable(this::waitFuture)   // -- worked, instance ref
            //.mapAwaitable(f -> waitFuture(f)) // -- worked, instance
            .mapAwaitable(f -> await(f))
            .filter(v -> Integer.parseInt(v.substring(0, 3)) % div == 0) 
            .filter(this::isEven)
            .map(v -> "000" + v) 
            .forEach(System.out::println)
            ; 
        System.out.println("Return after for each"); 
        return async(null); 
    }
    
    @async Generator<String> produceMergedStrings() {
        SuspendableStream<CompletionStage<String>> alphas = 
            produceAlphaStrings()
                .stream()
                .map(p -> p.thenApply(v -> v + " VALUE"));
        
        SuspendableStream<CompletionStage<String>> numerics =         
            produceNumericStrings()
                .stream()
                .map( Promises::from )
                .map( p -> p.orTimeout(Duration.ofMillis(500)) );

        yield(
            numerics
            .zip( alphas, (a, b) -> a.thenCombine(b, (av, bv) -> av + " - " + bv) )
            .as( Converters.generator() )
        );
        return yield();
    }
    
    // Private to ensure that generated accessor methods work 
    @async
    private Generator<String> produceNumericStrings() {
        yield(Generator.empty());
        yield(waitString("111"));
        yield(waitString("222"));
        yield("333");
        yield(waitString("444"));
        System.out.println("::produceNumericStrings FINALLY CALLED::");
        return yield();
    }
    
    @async
    Generator<String> produceAlphaStrings() {
        for (String s : Arrays.asList("AAA", "BBB", "CCC", "DDD")) {
            yield(waitString(s, 400));
        }
        System.out.println("::produceAlphaStrings FINALLY CALLED::");
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
}
