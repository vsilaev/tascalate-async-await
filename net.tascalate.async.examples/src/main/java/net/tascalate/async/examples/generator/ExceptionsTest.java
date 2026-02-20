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

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.AsyncYield;
import net.tascalate.async.async;
import net.tascalate.concurrent.CompletableTask;
import net.tascalate.concurrent.Promise;

public class ExceptionsTest {
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    
    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        try {
            System.out.println( consumer().get() );
        } finally {
            executor.shutdownNow();
        }
        double finish = System.currentTimeMillis();
        System.out.println((finish - start) / 1000 + " seconds");
    }
    
    @async static Promise<String> consumer() {
        try (AsyncGenerator<Object> generator = producer()) {
            CompletionStage<Object> f = generator.itemType();
            while (null != (f = generator.next())) {
                System.out.println("Consumed future: " + f);
                try {
                    System.out.println("RESULT: " + await(f));
                } catch (IllegalArgumentException ex) {
                    System.out.println("Consumed exception (await): " + ex);
                }
                System.out.println(f);
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("Consumed exception (next): " + ex);
        }

        return async("Done");
    }
    
    @async static AsyncGenerator<Object> producer() {
        AsyncYield<Object> async = AsyncGenerator.start();
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                AsyncYield.Reply<String> reply = async.yield( waitString("VALUE " + i, 100) );
                System.out.println("REPLY AFTER NORMAL: " + reply);
            } else {
                try {
                    AsyncYield.Reply<String> reply = async.yield( waitError(100) );
                    System.out.println("REPLY AFTER ERROR: " + reply);
                } catch (IllegalArgumentException ex) {
                    System.out.println("EXCEPTION ON iter#" + i + ": " + ex);
                    //throw ex;
                }
            }
        }
        return async.yield();
    }

    
    static CompletionStage<String> waitString(String value, long delay) {
        CompletionStage<String> promise = CompletableTask.supplyAsync(() -> {
            try { 
                Thread.sleep(delay);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CompletionException(ex);
            }
            return value;
        }, executor);
        return promise;
    }
    
    static CompletionStage<String> waitError(long delay) {
        CompletionStage<String> promise = CompletableTask.supplyAsync(() -> {
            try { 
                Thread.sleep(delay);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CompletionException(ex);
            }
            throw new IllegalArgumentException("Just for fun!");
        }, executor);
        return promise;
    }
}
