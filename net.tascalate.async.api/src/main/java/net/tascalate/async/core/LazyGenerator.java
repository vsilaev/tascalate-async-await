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

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.api.Generator;

class LazyGenerator<T> implements Generator<T> {
    private final ResultPromise<?> result;
	
    private CompletableFuture<?> producerLock;
    private CompletableFuture<?> consumerLock;

    private CompletionStage<T> latestResult;

    private Throwable lastError = null;
    private boolean done = false;

    private Generator<T> currentState = Generator.empty();
    private Object producerParam = Generator.NO_PARAM;
    
    LazyGenerator(ResultPromise<?> result) {
    	this.result = result;
        this.producerLock = new CompletableFuture<>();
    }

    @Override
    public CompletionStage<T> next(Object producerParam) {
        // If we have synchronous error in generator method
        // (as opposed to asynchronous that is managed by consumerLock
        if (null != lastError) {
            Throwable error = lastError;
            lastError = null;
            Either.sneakyThrow(error);
        }
        // Could we advance further current state?
        latestResult = producerParam == Generator.NO_PARAM ? 
            currentState.next() : currentState.next(producerParam);
            
        if (null != latestResult) {
            // Should be checked before done to let iterate over 
            // chained generators fully
            return latestResult;
        }
        
        if (done) {
            return null;
        }

        this.producerParam = producerParam;
        // Let produce some value (resumes producer)
        releaseProducerLock();
        // Wait till value is ready (suspends consumer)
        acquireConsumerLock();
        consumerLock = new CompletableFuture<>();
        // Check everything once again after wait
        return next(producerParam);
    }


    @Override
    public void close() {
        currentState.close();
        result.cancel(true);
        end(null);
    }

    @continuable
    Object produce(T readyValue) {
        return produce(Generator.of(readyValue));
    }

    @continuable
    Object produce(CompletionStage<T> pendingValue) {
        return produce(Generator.of(pendingValue));
    }

    @continuable
    Object produce(Generator<T> values) {
        return doProduce(values);
    }

    private @continuable Object doProduce(Generator<T> state) {
        // Get and re-set producerLock
        acquireProducerLock();
        producerLock = new CompletableFuture<>();
        currentState = state;
        releaseConsumerLock();
        // To have a semi-lazy generator that forwards till next yield
        // return producerParam;
        return acquireProducerLock();
    }

    @continuable
    void begin() {
        acquireProducerLock();
    }

    void end(Throwable ex) {
        // Set synchronous error in generator method
        // (as opposed to asynchronous that is managed by consumerLock        
        lastError = ex;
    	
        done = true;
        currentState = Generator.empty();
        releaseConsumerLock();
        
        if (null == ex) {
            result.internalCompleWithResult(null);
        } else {
            result.internalCompleWithFailure(ex);
        }
    }

    private @continuable Object acquireProducerLock() {
    	CompletableFuture<?> currentLock = producerLock;
        if (null != currentLock && !currentLock.isDone()) {
            AsyncMethodExecutor.await(currentLock);
            // Order matters - set to null only after wait
            if (currentLock == producerLock) {
                producerLock = null;
            }
        }
        T latestResultValue = awaitLatestResult();
        return producerFeedback(latestResultValue);
    }

    private void releaseProducerLock() {
        final CompletableFuture<?> currentLock = producerLock;
        if (null != currentLock) {
            producerLock = null;
            currentLock.complete(null);
        }
    }
    
    private @continuable void acquireConsumerLock() {
    	// logically it should not be here
    	//awaitLatestResult(); 
        CompletableFuture<?> currentLock = consumerLock;
    	if (null != currentLock && !currentLock.isDone()) {
            // Order matters - set to null only after wait      
            AsyncMethodExecutor.await(currentLock);
            if (currentLock == consumerLock) {
                consumerLock = null;
            }
    	}
    }
    
    private void releaseConsumerLock() {
        final CompletableFuture<?> currentLock = consumerLock;
        if (null != currentLock) {
            consumerLock = null;
            currentLock.complete(null);
        }
    }

    private @continuable T awaitLatestResult() {
        CompletionStage<T> currentLock = latestResult;
    	if (null != currentLock) {
    	    try {
    	        return AsyncMethodExecutor.await(currentLock);
    	    } finally {
    	        if (currentLock == latestResult) {
    	            latestResult = null;
    	        }
    	    }
    	} else {
            return null;
    	}    	
    }
    
    private Object producerFeedback(T latestResultValue) {
        if (Generator.NO_PARAM == producerParam) {
            return latestResultValue;
        } else {
            return producerParam;
        }        
    }
}
