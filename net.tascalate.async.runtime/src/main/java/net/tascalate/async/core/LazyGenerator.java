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
package net.tascalate.async.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import net.tascalate.async.api.Generator;
import net.tascalate.async.api.YieldReply;
import net.tascalate.async.api.suspendable;

class LazyGenerator<T> implements Generator<T> {
    private final AsyncGenerator<?> owner;
	
    private CompletableFuture<YieldReply<T>> producerLock;
    private CompletableFuture<?> consumerLock;
    private CompletionStage<T> latestFuture;

    private Generator<T> currentDelegate = Generator.empty();
    
    LazyGenerator(AsyncGenerator<T> owner) {
    	this.owner = owner;
    }

    @Override
    public CompletionStage<T> next(Object param) {
        // Loop to replace tail recursion - BEGIN
        while (true) {
            if (owner.checkDone()) {
                return null;
            }
            
            // Await previously returned result, if any
            FutureResult<T> latestResult = FutureResult.of(latestFuture);
            
            // Could we advance further current delegate?
            latestFuture = currentDelegate.next(param);
                
            if (null != latestFuture) {
                // Yes, we can
                return latestFuture;
            }
    
            // No, need to generate new promise;
    
            // Let produce some value (resumes producer)
            latestResult.releaseLock(producerLock, param);
            // Wait till value is ready (suspends consumer)
            acquireConsumerLock();
            consumerLock = new CompletableFuture<>();
            // Check everything once again after wait
        }
        // Loop to replace tail recursion - END
        // The actual tail recursive call is:
        //return next(param);
    }

    @Override
    public void close() {
        owner.future.cancel(true);
        currentDelegate.close();
        end(null);
    }

    final @suspendable YieldReply<T> produce(Generator<T> values) {
        currentDelegate = values;
        // Re-set producerLock
        // It's important to reset it before unlocking consumer!
        producerLock = new CompletableFuture<>();
        // Allow to consume new promise(s) yielded
        // Unlock consumer, if locked (initially it's unlocked)
        releaseConsumerLock();
        return acquireProducerLock();
    }

    final @suspendable void begin() {
        // Start with locked producer and unlocked consumer
        producerLock = new CompletableFuture<>();
        acquireProducerLock();
    }

    final void end(Throwable ex) {
        // Set synchronous error in generator method
        // (as opposed to asynchronous that is managed by consumerLock        
        if (null == ex) {
            owner.success(null);
        } else {
            owner.failure(ex);
        }
        currentDelegate = Generator.empty();
        releaseConsumerLock();
    }

    private @suspendable YieldReply<T> acquireProducerLock() {
        CompletableFuture<YieldReply<T>> currentLock = producerLock;
        if (!currentLock.isDone()) {
            return AsyncMethodExecutor.await(currentLock);
        } else {
            // Never returns null while isDone() == true
            return currentLock.getNow(null);
        }
    }
    
    private @suspendable void acquireConsumerLock() {
        // When next() is called for first time
        // then consumerLock is NULL
        CompletableFuture<?> currentLock = consumerLock;
    	if (null != currentLock) {
    	    if (!currentLock.isDone()) {
                AsyncMethodExecutor.await(currentLock);
    	    }
            // Order matters - set to null only after wait      
            consumerLock = null;
    	}
    }
    
    private void releaseConsumerLock() {
        final CompletableFuture<?> currentLock = consumerLock;
        if (null != currentLock) {
            consumerLock = null;
            currentLock.complete(null);
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "<generator{%s}>[consumer-lock=%s, producer-lock=%s, current-delegate=%s]", 
            getClass().getSimpleName(), consumerLock, producerLock, currentDelegate
        );
    }
    
    abstract static class FutureResult<T> {
        static class Success<T> extends FutureResult<T> {
            final T result;
            
            Success(T result) { 
                this.result = result; 
            }
            
            @Override
            void releaseLock(CompletableFuture<YieldReply<T>> lock, Object param) {
                lock.complete(new YieldReply<>(result, param));
            }
        }
        
        static class Failure<T> extends FutureResult<T> {
            final Throwable error;
            
            Failure(Throwable error) { 
                this.error  = error; 
            }
            
            @Override
            void releaseLock(CompletableFuture<YieldReply<T>> lock, Object param) {
                lock.completeExceptionally(error);
            }
        }
        
        @suspendable 
        static <T> FutureResult<T> of(CompletionStage<? extends T> future) {
            if (null == future) {
                @SuppressWarnings("unchecked")
                FutureResult<T> empty = (FutureResult<T>)EMPTY;
                return empty;
            } else {
                try {
                    return new Success<T>(AsyncMethodExecutor.await(future));
                } catch (Exception ex) {
                    return new Failure<T>(ex);
                }
            }
        }
        
        abstract void releaseLock(CompletableFuture<YieldReply<T>> lock, Object param);
        
        private static final FutureResult<Object> EMPTY = new Success<Object>(null);
    }
}
