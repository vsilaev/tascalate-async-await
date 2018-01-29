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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import net.tascalate.async.api.Generator;
import net.tascalate.async.api.YieldReply;
import net.tascalate.async.api.suspendable;

class LazyGenerator<T> implements Generator<T> {
    private final AsyncGenerator<?> owner;
	
    private CompletableFuture<YieldReply<T>> producerLock;
    private CompletableFuture<?> consumerLock;
    private CompletionStage<T> latestResult;

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
            T latestResultValue = null != latestResult ? AsyncMethodExecutor.await(latestResult) : null;
            
            // Could we advance further current delegate?
            latestResult = currentDelegate.next(param);
                
            if (null != latestResult) {
                // Yes, we can
                return latestResult;
            }
    
            // No, need to generate new promise;
    
            // Let produce some value (resumes producer)
            releaseProducerLock(new YieldReply<>(latestResultValue, param));
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

    @suspendable YieldReply<T> produce(Generator<T> values) {
        currentDelegate = values;
        // Re-set producerLock
        // It's important to reset it before unlocking consumer!
        producerLock = new CompletableFuture<>();
        // Allow to consume new promise(s) yielded
        // Unlock consumer, if locked (initially it's unlocked)
        releaseConsumerLock();
        return acquireProducerLock();
    }

    @suspendable void begin() {
        // Start with locked producer and unlocked consumer
        producerLock = new CompletableFuture<>();
        acquireProducerLock();
    }

    void end(Throwable ex) {
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

    private void releaseProducerLock(YieldReply<T> reply) {
        CompletableFuture<YieldReply<T>> currentLock = producerLock;
        currentLock.complete(reply);
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
}
