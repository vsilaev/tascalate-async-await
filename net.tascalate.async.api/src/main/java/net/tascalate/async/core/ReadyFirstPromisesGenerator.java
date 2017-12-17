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
package net.tascalate.async.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import net.tascalate.async.api.Generator;

public class ReadyFirstPromisesGenerator<T> implements Generator<T> {
    
    final private BlockingQueue<CompletionStage<T>> resolvedPromises = new LinkedBlockingQueue<>();
    final private AtomicInteger remaining = new AtomicInteger(0);
    
    private volatile CompletableFuture<Void> consumerLock = new CompletableFuture<>();
    private Generator<T> current = Generator.empty();
    
    private ReadyFirstPromisesGenerator() {  }
    
    private void enlistResolved(CompletionStage<T> resolvedPromise) {
        try {
            resolvedPromises.put(resolvedPromise);
        } catch (InterruptedException e) {
            throw new RuntimeException(e); // Shouldn't happen for queue with unlimited size
        }

        remaining.decrementAndGet();
        consumerLock.complete(null);
    }

    @Override
    public CompletionStage<T> next(Object producerParam) {
        // If we may return more without switching state...
    	CompletionStage<T> resolvedValue = current.next(producerParam); 
        if (null != resolvedValue) {
            return resolvedValue;
        }

        int unprocessed = remaining.get();
        if (unprocessed < 0) {
            // Forcibly closed
            return null;
        } else {
            final Collection<CompletionStage<T>> readyValues = new ArrayList<>();
            resolvedPromises.drainTo(readyValues);

            if (!readyValues.isEmpty()) {
                // If we are consuming slower than producing 
                // then use available results right away
                current = Generator.ofOrdered(readyValues);
                return next(producerParam);
            } else {
                // Otherwise await for any result...            
                if (unprocessed > 0) {
                    AsyncMethodExecutor.await(consumerLock);
                    consumerLock = new CompletableFuture<>();
                    // ... and try again
                    return next(producerParam);
                } else {
                    //...or stop if no more results...
                    current = Generator.empty();
                    return null;
                }
            }
        }
        
    }

    @Override
    public void close() {
        remaining.set(-1);
        current.close();
        current = Generator.empty();
    }

    public static <T> Generator<T> create(Stream<CompletionStage<T>> pendingPromises) {
        return create(pendingPromises.iterator());
    }

    public static <T> Generator<T> create(Iterable<CompletionStage<T>> pendingPromises) {
        return create(pendingPromises.iterator());
    }
    
    private static <T> Generator<T> create(Iterator<CompletionStage<T>> pendingPromises) {
        ReadyFirstPromisesGenerator<T> result = new ReadyFirstPromisesGenerator<>();
        while(pendingPromises.hasNext()) {
            // +1 before setting completion handler -- 
            // while stage may be completed already
            // we should increment step-by-step 
            // instead of setting the value at once
            result.remaining.incrementAndGet(); 
            CompletionStage<T> nextPromise = pendingPromises.next(); 
            nextPromise.whenComplete((r, e) -> result.enlistResolved(nextPromise));
        };
        
        return result;
    }
}