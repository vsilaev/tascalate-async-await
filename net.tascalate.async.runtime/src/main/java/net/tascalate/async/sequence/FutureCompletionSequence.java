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
package net.tascalate.async.sequence;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import net.tascalate.async.Sequence;
import net.tascalate.async.core.AbstractAsyncMethod;
import net.tascalate.async.core.AsyncMethodExecutor;
import net.tascalate.async.core.SuspendableSequence;
import net.tascalate.async.util.TypeUtil;

public class FutureCompletionSequence<T, F extends CompletionStage<T>> extends SuspendableSequence<F> {
    
    private static final AtomicIntegerFieldUpdater<FutureCompletionSequence<?, ?>> IN_PROGRESS_UPDATER = 
            AtomicIntegerFieldUpdater.newUpdater(TypeUtil.cast(FutureCompletionSequence.class), "inProgress");
    
    private final Iterator<? extends F> pendingPromises;
    private final int chunkSize;
    private final CancelPolicy cancelPolicy;
    
    private final BlockingQueue<F> settledPromises;
    private final Set<CompletionStage<?>> enlistedPromises; 
    
    private volatile int inProgress = 0;
    private volatile CompletableFuture<Void> consumerLock = new CompletableFuture<>();
    
    protected FutureCompletionSequence(Iterator<? extends F> pendingValues, int chunkSize) {
        this(pendingValues, chunkSize, CancelPolicy.ENLISTED);
    }
    
    protected FutureCompletionSequence(Iterator<? extends F> pendingValues, int chunkSize, CancelPolicy cancelPolicy) {  
        this.pendingPromises = pendingValues;
        this.chunkSize = chunkSize;
        this.cancelPolicy = cancelPolicy == null ? CancelPolicy.ENLISTED : cancelPolicy;
        this.settledPromises = chunkSize > 0 ? new LinkedBlockingQueue<>(chunkSize)
                                             : new LinkedBlockingQueue<>();  
        this.enlistedPromises = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }
    
    @Override
    public F next() {
        return next$(null);
    }
    
    @Override
    protected F next$(AbstractAsyncMethod caller) {
        while (true) {
            if (inProgress < 0) {
                // Forcibly closed
                return null;
            } else {
                F readyValue = settledPromises.poll();
                if (null != readyValue) {
                    IN_PROGRESS_UPDATER.decrementAndGet(this);
                    enlistPending();
                    return readyValue;
                } else {
                    // Otherwise await for any result...            
                    if (inProgress > 0) {
                        AsyncMethodExecutor.await(consumerLock, caller);
                        consumerLock = new CompletableFuture<>();
                        // ... and try again
                        // recursion via loop
                        continue;
                    } else {
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
        inProgress = Integer.MIN_VALUE;
        cancelPolicy.apply(enlistedPromises, pendingPromises);
        enlistedPromises.clear();
        settledPromises.clear();
        consumerLock.complete(null);
    }
    
    private boolean enlistPending() {
        boolean enlisted = false;
        while (pendingPromises.hasNext()) {
            F nextPromise = pendingPromises.next();
            
            // +1 before setting completion handler -- 
            // while stage may be completed already
            // we should increment step-by-step 
            // instead of setting the value at once
            int currentInProgress = IN_PROGRESS_UPDATER.incrementAndGet(this);
            if (currentInProgress < 0) {
                // was closed in between
                break;
            }
            /*
            inProgress++;
            */
            enlistedPromises.add(nextPromise);
            nextPromise.whenComplete(enlistResolved(nextPromise));
            enlisted = true;
            
            if (chunkSize > 0 && currentInProgress >= chunkSize) {
                break;
            }
        };  
        return enlisted;
    }

    private BiConsumer<T, Throwable> enlistResolved(F resolvedPromise) {
        return (resolvedValue, ex) -> {
            enlistedPromises.remove(resolvedPromise);
            try {
                settledPromises.put(resolvedPromise);
                consumerLock.complete(null);
            } catch (InterruptedException ie) {
                // Shouldn't happen for the queue with an unlimited size
                if (null != ex) {
                    ie.addSuppressed(ex);
                }
                Thread.currentThread().interrupt(); 
                boolean completedByThisException = consumerLock.completeExceptionally(ie);
                if (!completedByThisException) {
                    consumerLock.whenComplete(($, originalException) -> {
                        if (null != originalException) {
                            originalException.addSuppressed(ie);
                        }
                    });
                }
            }
        };
    }

    
    @Override
    public String toString() {
        return String.format(
            "%s[consumer-lock=%s, remaining=%s, resolved-promises=%s]",
            getClass().getSimpleName(), consumerLock, inProgress, settledPromises
        );
    }

    public static <T, F extends CompletionStage<T>> Sequence<F> create(Stream<? extends F> pendingPromises, int chunkSize) {
        return create(pendingPromises, chunkSize, CancelPolicy.ENLISTED);
    }
    
    public static <T, F extends CompletionStage<T>> Sequence<F> create(Stream<? extends F> pendingPromises, 
                                                                       int chunkSize,
                                                                       CancelPolicy cancelPolicy) {
        return create(pendingPromises.iterator(), chunkSize, cancelPolicy);
    }


    public static <T, F extends CompletionStage<T>> Sequence<F> create(Collection<? extends F> pendingPromises, int chunkSize) {
        return create(pendingPromises, chunkSize, CancelPolicy.ALL);
    }
    
    public static <T, F extends CompletionStage<T>> Sequence<F> create(Collection<? extends F> pendingPromises, 
                                                                       int chunkSize,
                                                                       CancelPolicy cancelPolicy) {
        return create(pendingPromises.iterator(), chunkSize, cancelPolicy);
    }
    
    private static <T, F extends CompletionStage<T>> Sequence< F> create(Iterator<? extends F> pendingPromises, 
                                                                         int chunkSize,
                                                                         CancelPolicy cancelPolicy) {
        return new FutureCompletionSequence<>(pendingPromises, chunkSize, cancelPolicy);
    }

}
