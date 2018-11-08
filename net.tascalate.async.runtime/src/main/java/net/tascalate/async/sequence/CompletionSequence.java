/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.sequence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.stream.Stream;

import net.tascalate.async.Sequence;
import net.tascalate.async.core.AsyncMethodExecutor;

public class CompletionSequence<T, F extends CompletionStage<T>> implements Sequence<F> {
    
    private final Iterator<? extends F> pendingPromises;
    private final int chunkSize;
    private final BlockingQueue<F> settledPromises = new LinkedBlockingQueue<>();
    private final AtomicInteger remaining = new AtomicInteger(0);
    
    private volatile CompletableFuture<Void> consumerLock = new CompletableFuture<>();
    private Sequence<F> current = Sequence.empty();
    
    protected CompletionSequence(Iterator<? extends F> pendingValues, int chunkSize) {  
        this.pendingPromises = pendingValues;
        this.chunkSize = chunkSize;
    }
    
    @Override
    public F next() {
        while (true) {
            // If we may return more without switching state...
            F resolvedValue = current.next(); 
            if (null != resolvedValue) {
                return resolvedValue;
            }
    
            int unprocessed = remaining.get();
            if (unprocessed < 0) {
                // Forcibly closed
                return null;
            } else {
                final Collection<F> readyValues = new ArrayList<>(/*Math.max(0, chunkSize)*/);
                settledPromises.drainTo(readyValues);
    
                if (!readyValues.isEmpty()) {
                    // If we are consuming slower than producing 
                    // then use available results right away
                    current = Sequence.of(readyValues);
                    // recursion via loop
                    continue; 
                } else {
                    // Otherwise await for any result...            
                    if (unprocessed > 0) {
                        AsyncMethodExecutor.await(consumerLock);
                        consumerLock = new CompletableFuture<>();
                        // ... and try again
                        // recursion via loop
                        continue;
                    } else {
                        current = Sequence.empty();
                        if (enlistPending()) {
                            // More was enlisted
                            continue; //recursion via loop
                        } else {
                            // ...or stop when over
                            return null;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void close() {
        remaining.set(Integer.MIN_VALUE);
        current.close();
        current = Sequence.empty();
    }
    
    private boolean enlistPending() {
        boolean enlisted = false;
        int i = 0;
        while (pendingPromises.hasNext()) {
            F nextPromise = pendingPromises.next();
            
            // +1 before setting completion handler -- 
            // while stage may be completed already
            // we should increment step-by-step 
            // instead of setting the value at once
            remaining.incrementAndGet(); 
            nextPromise.whenComplete((r, e) -> enlistResolved(nextPromise));
            enlisted = true;
            
            i++;
            if (chunkSize > 0 && i >= chunkSize) {
                break;
            }
        };  
        return enlisted;
    }
    
    private void enlistResolved(F resolvedPromise) {
        try {
            settledPromises.put(resolvedPromise);
        } catch (InterruptedException e) {
            throw new RuntimeException(e); // Shouldn't happen for the queue with an unlimited size
        }

        remaining.decrementAndGet();
        consumerLock.complete(null);
    }

    
    @Override
    public String toString() {
        return String.format(
            "%s[current=%s, consumer-lock=%s, remaining=%s, resolved-promises=%s]",
            getClass().getSimpleName(), current, consumerLock, remaining, settledPromises
        );
    }

    public static <T, F extends CompletionStage<T>> Sequence<F> create(Stream<? extends F> pendingPromises, int chunkSize) {
        return create(pendingPromises.iterator(), chunkSize);
    }

    public static <T, F extends CompletionStage<T>> Sequence<F> create(Iterable<? extends F> pendingPromises, int chunkSize) {
        return create(pendingPromises.iterator(), chunkSize);
    }
    
    private static <T, F extends CompletionStage<T>> Sequence< F> create(Iterator<? extends F> pendingPromises, int chunkSize) {
        return new CompletionSequence<>(pendingPromises, chunkSize);
    }
}