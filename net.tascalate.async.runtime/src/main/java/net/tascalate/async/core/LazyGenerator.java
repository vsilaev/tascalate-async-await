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
package net.tascalate.async.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.UnaryOperator;

import net.tascalate.async.AsyncYield;
import net.tascalate.async.CustomizableSequence;
import net.tascalate.async.Scheduler;
import net.tascalate.async.Sequence;
import net.tascalate.async.suspendable;
import net.tascalate.async.util.TypeUtil;

class LazyGenerator<T> extends SuspendableSequence<CompletionStage<T>> 
                       implements InternalAsyncGenerator<T> {
    private static final AtomicReferenceFieldUpdater<LazyGenerator<?>, CompletionStage<?>> DONE_UPDATER = 
            AtomicReferenceFieldUpdater.newUpdater(TypeUtil.cast(LazyGenerator.class), TypeUtil.cast(CompletionStage.class), "done");
    
    private final AsyncGeneratorMethod<?> owner;
    private final Values<T> values; 
    private volatile CompletionStage<?> done;
    
    // Start with locked producer and unlocked consumer
    // Also assume that next() MAY BE called before begin
    private CompletableFuture<AsyncYield.Reply<T>> producerLock = new CompletableFuture<>();
    private CompletableFuture<?> consumerLock;
    private CompletionStage<T> latestFuture;

    private Sequence<? extends CompletionStage<T>> currentDelegate = Sequence.empty();
    private SequenceKind currentDelegateKind = SequenceKind.READY_VALUES;
    
    LazyGenerator(AsyncGeneratorMethod<T> owner) {
    	this.owner = owner;
    	this.done = owner.future;
    	values = new AsyncValues<>(this);
    }
    
    @Override
    public Values<T> values() {
        return values;
    }

    @Override
    public CompletionStage<T> next() {
        return next$(NO_PARAM, null);
    }
    
    @Override
    public CompletionStage<T> next(Object param) {
        return next$(param , null);
    }
    
    @Override
    protected @suspendable CompletionStage<T> next$(AbstractAsyncMethod caller) {
        return next$(NO_PARAM , caller);
    }
    
    @Override
    protected @suspendable CompletionStage<T> next$(Object param, AbstractAsyncMethod caller) {
        // Loop to replace tail recursion - BEGIN
        while (true) {
            if (owner.checkDone()) {
                return null;
            }
            
            // Await previously returned result, if any
            FutureResult<T> latestResult = FutureResult.of(latestFuture, caller);
            
            // Could we advance further current delegate?
            // Switch below is optimization of
            // currentDelegate.next() / currentDelegate.next(param)
            if (null == currentDelegateKind) {
                currentDelegateKind = SequenceKind.kindOf(currentDelegate);
            }
            switch (currentDelegateKind) { 
                case READY_VALUES:
                    // Avoid @suspendable ceremony
                    latestFuture = SuspendableSequence.nextReadyValue(currentDelegate);
                    break;
                case SUSPENDABLE_CUSTOMIZABLE:
                    latestFuture = NO_PARAM == param 
                                   ? SuspendableSequence.nextSuspendable(currentDelegate, caller) 
                                   : SuspendableSequence.nextSuspendable(currentDelegate, param, caller);       
                    break;
                case SUSPENDABLE_REGULAR:
                    latestFuture = SuspendableSequence.nextSuspendable(currentDelegate, caller); 
                    break;
                case NON_SUSPENDABLE_CUSTOMIZABLE: {
                    CustomizableSequence<? extends CompletionStage<T>> typedDelegate 
                            = (CustomizableSequence<? extends CompletionStage<T>>)currentDelegate;
                    latestFuture = NO_PARAM == param 
                                   ? typedDelegate.next() 
                                   : typedDelegate.next(param);                    
                    break;
                }
                case NON_SUSPENDABLE_REGULAR: {
                    latestFuture = currentDelegate.next();
                    break;
                }
                default:
                    throw new IllegalStateException();
            
            }
            
            if (null != latestFuture) {
                // Yes, we can
                return latestFuture;
            }
    
            // No, need to generate new promise;
    
            // Let produce some value (resumes producer)
            latestResult.releaseLock(producerLock, param);
            
            // Wait till value is ready (suspends consumer)
            acquireConsumerLock(caller);
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
    
    @Override
    public Scheduler scheduler() {
        return owner.scheduler();
    }
    
    @Override
    public CompletionStage<?> __completion() {
        return done;
    }

    @Override
    public CompletionStage<?> __completion(UnaryOperator<CompletionStage<?>> mapper) {
        return DONE_UPDATER.updateAndGet(this, mapper);
    }

    final @suspendable AsyncYield.Reply<T> emit(Sequence<? extends CompletionStage<T>> pendingValues) {
        currentDelegate = pendingValues;
        currentDelegateKind = null;
        // Re-set producerLock
        // It's important to reset it before unlocking consumer!
        producerLock = new CompletableFuture<>();
        // Allow to consume new promise(s) yielded
        // Unlock consumer, if locked (initially it's unlocked)
        releaseConsumerLock();
        return acquireProducerLock();
    }

    final @suspendable void begin() {
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
        currentDelegate = Sequence.empty();
        currentDelegateKind = SequenceKind.READY_VALUES;
        releaseConsumerLock();
    }

    private @suspendable AsyncYield.Reply<T> acquireProducerLock() {
        CompletableFuture<AsyncYield.Reply<T>> currentLock = producerLock;
        if (!currentLock.isDone()) {
            return AsyncMethodExecutor.await(currentLock, owner);
        } else {
            // Never returns null while isDone() == true
            return currentLock.getNow(null);
        }
    }
    
    private @suspendable void acquireConsumerLock(AbstractAsyncMethod caller) {
        // When next() is called for first time
        // then consumerLock is NULL
        CompletableFuture<?> currentLock = consumerLock;
    	if (null != currentLock) {
    	    if (!currentLock.isDone()) {
                AsyncMethodExecutor.await(currentLock, caller);
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
            void releaseLock(CompletableFuture<AsyncYield.Reply<T>> lock, Object param) {
                lock.complete(new AsyncYield.Reply<>(result, param == NO_PARAM ? null : param));
            }
        }
        
        static class Failure<T> extends FutureResult<T> {
            final Throwable error;
            
            Failure(Throwable error) { 
                this.error  = error; 
            }
            
            @Override
            void releaseLock(CompletableFuture<AsyncYield.Reply<T>> lock, Object param) {
                lock.completeExceptionally(error);
            }
        }
        
        @suspendable 
        static <T> FutureResult<T> of(CompletionStage<? extends T> future, AbstractAsyncMethod caller) {
            if (null == future) {
                @SuppressWarnings("unchecked")
                FutureResult<T> empty = (FutureResult<T>)EMPTY;
                return empty;
            } else {
                try {
                    return new Success<T>(AsyncMethodExecutor.await(future, caller));
                } catch (Throwable ex) {
                    InternalCallContext.checkExitSignal(ex);
                    return new Failure<T>(ex);
                }
            }
        }
        
        abstract void releaseLock(CompletableFuture<AsyncYield.Reply<T>> lock, Object param);
        
        private static final FutureResult<Object> EMPTY = new Success<Object>(null);
    }
    
    static private final Object NO_PARAM = new Object();
}
