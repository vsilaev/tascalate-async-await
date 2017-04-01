package net.tascalate.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

class DelegatingCallable<T> implements Callable<T> {
	
	final private AtomicBoolean setupGuard = new AtomicBoolean(false);
	private Callable<T> delegate;
	
	void setup(Callable<T> delegate) {
		if (setupGuard.compareAndSet(false, true)) {
			this.delegate = delegate;
		} else {
			throw new IllegalStateException("Delegate may be set only once");
		}
	}

	@Override
	public T call() throws Exception {
		if (!setupGuard.get()) {
			throw new IllegalStateException("Call is not configured");
		} else {
			return delegate.call();
		}
	}
	
	
}
