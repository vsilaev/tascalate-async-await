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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.tascalate.async.ContextualRunnable;
import net.tascalate.async.Scheduler;
import net.tascalate.async.SchedulerProvider;
import net.tascalate.async.async;
import net.tascalate.async.xpi.Generators;
import net.tascalate.concurrent.CompletableTask;
import net.tascalate.javaflow.SuspendableIterator;

public class ContextPassingExamples {
    
    final private static ExecutorService foreignExecutor = Executors.newFixedThreadPool(4);
    final private static ExecutorService ownExecutor = Executors.newFixedThreadPool(4);
    
    private static final ThreadLocal<String> MY_CONTEXT_VAR = new ThreadLocal<>();

    public static void main(String[] argv) {
        Scheduler scheduler = Scheduler.interruptible(ownExecutor, ContextualRunnable.relayContextVars(MY_CONTEXT_VAR));
        MY_CONTEXT_VAR.set("CORRECT");
        
        CompletableFuture<String> f = new ContextPassingExamples().asyncMethod(scheduler, 10);
        System.out.println(f.join());
        foreignExecutor.shutdownNow();
        ownExecutor.shutdownNow();
    }
    
    @async CompletableFuture<String> asyncMethod(@SchedulerProvider Scheduler scheduler, long v) {
        IDemo z = new IDemo() {
        };
        String id = await(z.run());
        System.out.println("FROM IDEMO: " + id);
        System.out.println("Context A:" + MY_CONTEXT_VAR.get() + ", thread " + Thread.currentThread());
        await( waitString("1") );
        System.out.println("Context B:" + MY_CONTEXT_VAR.get() + ", thread " + Thread.currentThread());
        await( waitString("2") );
        System.out.println("Context C:" + MY_CONTEXT_VAR.get() + ", thread " + Thread.currentThread());
        int c = 0;
        MY_CONTEXT_VAR.set("ALTERED");
        try (SuspendableIterator<CompletionStage<Duration>> i = Generators.delays(Duration.ofSeconds(1)).iterator()) {
            while (i.hasNext()) {
                await( i.next() );
                System.out.println(++v);
                System.out.println("Context X:" + MY_CONTEXT_VAR.get() + ", thread " + Thread.currentThread());
                c++;
                if (c >= 5) {
                    break;
                }
            }
        }
        return async("Done");
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
        }, foreignExecutor);
        return promise;
    }
    
}
