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
import static net.tascalate.async.CallContext.yield;
import static net.tascalate.async.CallContext.awaitValue;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.Sequence;
import net.tascalate.async.YieldReply;
import net.tascalate.async.async;
import net.tascalate.async.suspendable;
import net.tascalate.concurrent.CompletableTask;
import net.tascalate.concurrent.Promises;

import net.tascalate.javaflow.SuspendableStream;

public class StreamTest {

    final private static ExecutorService executor = Executors.newFixedThreadPool(4);

    public static void main(String[] args) {
        final StreamTest example = new StreamTest();
        example.div = 2;
        
        String v1 = example.asyncOperation(2).toCompletableFuture().join();
        System.out.println(v1);
        
        String v2 = example.asyncFlatMap().toCompletableFuture().join();
        System.out.println(v2);
        
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
    public CompletionStage<String> asyncFlatMap() {
        String result = 
        produceAlphaStrings()
            .stream()
            .flatMap(px -> producePrefixedStrings(px).stream())
            .drop(2)
            .take(18)
            .map$(awaitValue())
            .fold("", (r, s) -> r + "\n" + s)
        ;
        return async(result);
    }

    @async
    public CompletionStage<String> asyncOperation(int outerDiv) {
        produceMergedStrings()
            .stream()  
            //.mapAwaitable(f -> await(f))      // -- worked, static
            //.mapAwaitable(this::waitFuture)   // -- worked, instance ref
            //.mapAwaitable(f -> waitFuture(f)) // -- worked, instance
            .map$(f -> await(f))
            .filter(v -> Integer.parseInt(v.substring(0, 3)) % div == 0) 
            .filter(this::isEven)
            .map(v -> "000" + v) 
            .forEach(System.out::println)
            ; 
        System.out.println("Return after for each"); 
       
        return async( 
            produceNumericStrings()
            .stream()
            .map$(awaitValue())
            .reduce((t, s) -> t + "\n" + s)
            .orElse("<error-reduce")
        ); 
    }
    
    @async AsyncGenerator<String> produceMergedStrings() {
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
            SuspendableStream
                .zip(numerics, alphas, (a, b) -> a.thenCombine(b, (av, bv) -> av + " - " + bv) )
                .convert(Sequence.fromStream())
        );
        return yield();
    }
    
    // Private to ensure that generated accessor methods work 
    @async
    private AsyncGenerator<String> produceNumericStrings() {
        try {
            yield(Sequence.empty());
            yield(waitString("111"));
            yield(waitString("222"));
            yield("333");
            yield(waitString("444"));
        } finally {
            System.out.println("::produceNumericStrings FINALLY CALLED::");
        }
        return yield();
    }
    
    @async
    AsyncGenerator<String> produceAlphaStrings() {
        try {
            for (String s : Arrays.asList("AAA", "BBB", "CCC", "DDD")) {
                yield( waitString(s, 400) );
            }
        } finally {
            System.out.println("::produceAlphaStrings FINALLY CALLED::");
        }
        return yield();
    }
    
    @async
    AsyncGenerator<String> producePrefixedStrings(CompletionStage<String> prefix) {
        try {
            for (int i = 1; i <= 9; i++) {
                YieldReply<String> r = yield( prefix.thenCombine(waitString(String.valueOf(i)), (px,v) -> px + v) );
                System.out.println(">> Yield " + r.value);
            }
        } finally {
            System.out.println("::producePrefixedStrings " + await(prefix) + " FINALLY CALLED::");
        }
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
