package com.farata.lang.async.api;

import org.apache.commons.javaflow.api.continuable;

public interface Generator<T> extends AutoCloseable {
	abstract public @continuable boolean next(Object producerParam);
	abstract public @continuable boolean next();
	abstract public T current();
	abstract void close(); 
	
}
