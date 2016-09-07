package com.farata.lang.async.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;

import com.farata.lang.async.api.Generator;

class GeneratorImpl<T> implements Generator<T> { 
	
	private CompletableFuture<?> consumerLock;
	private CompletableFuture<?> producerLock;
	private boolean done = false;
	
	private State<T> currentState;
	private T currentValue;
	
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
		if (done) {
			return false;
		}
		this.producerParam = producerParam; 
		releaseProducerLock();
		acquireConsumerLock();
		consumerLock = new CompletableFuture<>();
		if (null != currentState) {
			currentValue = currentState.await();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public T current() {
		if (null != currentState) {
			return currentValue;
		} else {
			throw new IllegalStateException();
		}
	}

	@Override
	public void close() {
		
	}
	
	@continuable Object produce(T readyValue) {
		return produce(new ReadyValueState<>(readyValue));
	}
	
	@continuable Object produce(CompletionStage<T> pendingValue) {
		return produce(new PendingValueState<>(pendingValue));
	}
	
	private @continuable Object produce(State<T> state) {
		// Get and re-set producerLock
		acquireProducerLock();
		producerLock = new CompletableFuture<>();	
		currentState = state;
		releaseConsumerLock();
		return producerParam;
	}
	
	@continuable void begin() {
		acquireProducerLock();
	}
	
	void end() {
		done = true;
	}
	
	@continuable void acquireProducerLock() {
		if (null == producerLock || producerLock.isDone()) {
			return;
		}
		AsyncExecutor.await(producerLock);
		producerLock = null;	
	}
	
	private void releaseProducerLock() {
		if (null != producerLock) {
			producerLock.complete(null);
		}
	}
	
	private @continuable void acquireConsumerLock() {
		if (null == consumerLock || consumerLock.isDone()) {
			return;
		}
		AsyncExecutor.await(consumerLock);
		consumerLock = null;		
	}
	
	private void releaseConsumerLock() {
		if (null != consumerLock) {
			consumerLock.complete(null);
		}
	}
	
	abstract static class State<T> {
		abstract @continuable T await();
	}
	
	static class ReadyValueState<T> extends State<T> {
		final private T readyValue;
		
		public ReadyValueState(T readyValue) {
			this.readyValue = readyValue;
		}
		
		T await() {
			return readyValue;
		}
	}
	
	static class PendingValueState<T> extends State<T> {
		final private CompletionStage<T> pendingValue;
		
		public PendingValueState(CompletionStage<T> pendingValue) {
			this.pendingValue = pendingValue;
		}
		
		T await() {
			return AsyncExecutor.await(pendingValue);
		}
	}

	
}
