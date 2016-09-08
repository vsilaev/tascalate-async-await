package com.farata.lang.async.core;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;

import com.farata.lang.async.api.Generator;

class GeneratorImpl<T> implements Generator<T> {

    private CompletableFuture<?> consumerLock;
    private CompletableFuture<?> producerLock;
    private boolean done = false;

    private State<T> currentState;
    private Object producerParam;

    GeneratorImpl() {
        producerLock = new CompletableFuture<>();
    }

    @Override
    public @continuable boolean next() {
        return next(null);
    }

    @Override
    public boolean next(Object producerParam) {
        // Could we advance further current state?
        if (null != currentState && currentState.advance()) {
            // Should be checked before done to let iterate over 
            // chained generators fully
            return true;
        } 
        
        if (done) {
            return false;
        }
        
        this.producerParam = producerParam;
        releaseProducerLock();
        acquireConsumerLock();
        consumerLock = new CompletableFuture<>();
        if (null != currentState) {
            return currentState.advance();
        } else {
            return false;
        }
    }

    @Override
    public T current() {
        if (null != currentState) {
            return currentState.currentValue();
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public void close() {
        if (null != currentState) {
            currentState.close();
            currentState = null;
            if (null != producerLock) {
                final CompletableFuture<?> lock = producerLock;
                producerLock = null;
                lock.completeExceptionally(CloseSignal.INSTANCE);
            }
        }
        end();
    }

    @continuable
    Object produce(T readyValue) {
        return produce(new ReadyValueState<>(readyValue));
    }

    @continuable
    Object produce(CompletionStage<T> pendingValue) {
        return produce(new PendingValueState<>(pendingValue));
    }

    @continuable
    Object produce(Generator<T> values) {
        return produce(new ChainedGeneratorState<T>(values));
    }

    private @continuable Object produce(State<T> state) {
        // Get and re-set producerLock
        acquireProducerLock();
        producerLock = new CompletableFuture<>();
        currentState = state;
        releaseConsumerLock();
        // return producerParam; // To have a semi-lazy generator that forwards till next yield
        return acquireProducerLock();
    }

    @continuable
    void begin() {
        acquireProducerLock();
    }

    void end() {
        done = true;
        releaseConsumerLock();
    }

    @continuable
    Object acquireProducerLock() {
        if (null == producerLock || producerLock.isDone()) {
            return null;
        }
        // Order matters - set to null only after wait
        AsyncExecutor.await(producerLock);
        producerLock = null;
        if (null == producerParam) {
        	return currentState == null ? null : currentState.currentValue();
        } else {
        	return producerParam;
        }
    }

    private void releaseProducerLock() {
        if (null != producerLock) {
            final CompletableFuture<?> lock = producerLock;
            producerLock = null;
            lock.complete(null);
        }
    }

    private @continuable void acquireConsumerLock() {
        if (null == consumerLock || consumerLock.isDone()) {
            return;
        }
        // Order matters - set to null only after wait        
        AsyncExecutor.await(consumerLock);
        consumerLock = null;
    }

    private void releaseConsumerLock() {
        if (null != consumerLock) {
            final CompletableFuture<?> lock = consumerLock;
            consumerLock = null;
            lock.complete(null);
        }
    }

    abstract static class State<T> {
        abstract @continuable boolean advance();
        abstract T currentValue();
        abstract void close();
    }

    static class ReadyValueState<T> extends State<T> {
        final private T readyValue;
        private boolean iterated = false;

        public ReadyValueState(T readyValue) {
            this.readyValue = readyValue;
        }
        
        @Override boolean advance() {
            if (iterated) {
                return false;
            }
            iterated = true;
            return true;
        }

        @Override T currentValue() {
            if (!iterated) {
                throw new IllegalStateException();
            }
            return readyValue;
        }
        
        @Override void close() {
            iterated = true;
        }
    }

    static class PendingValueState<T> extends State<T> {
        private CompletionStage<T> pendingValue;
        private T readyValue;
        
        public PendingValueState(CompletionStage<T> pendingValue) {
            this.pendingValue = pendingValue;
        }

        @Override boolean advance() {
            if (null == pendingValue) {
                return false;
            }
            readyValue = AsyncExecutor.await(pendingValue);
            pendingValue = null;
            return true;
        }
        
        @Override T currentValue() {
            if (null != pendingValue) {
                throw new IllegalStateException();
            }
            return readyValue;
        }
        
        @Override void close() {
            if (null != pendingValue) {
                if (pendingValue instanceof CompletableFuture) {
                    CompletableFuture.class.cast(pendingValue)
                        .completeExceptionally(CloseSignal.INSTANCE);
                } else {
                    // Have to resort to reflection due to lack of 
                    // better interfaces hierarchy for CompletionStage
                    try {
                        final Method m = pendingValue.getClass().getMethod("completeExceptionally", Throwable.class);
                        m.invoke(pendingValue, CloseSignal.INSTANCE);
                    } catch (final ReflectiveOperationException | RuntimeException ex) {
                        // if no such method or it is unsupported
                    }
                }
            }
            pendingValue = null;
        }
    }
    
    static class ChainedGeneratorState<T> extends State<T> {
        final private Generator<T> source;

        public ChainedGeneratorState(Generator<T> source) {
            this.source = source;
        }

        @Override boolean advance() {
            return source.next();
        }
        
        @Override T currentValue() {
            return source.current();
        }
        
        @Override void close() {
            source.close();
        }
    }


}
