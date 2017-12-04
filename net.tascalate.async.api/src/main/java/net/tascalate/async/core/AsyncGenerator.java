package net.tascalate.async.core;

import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.api.Generator;

abstract public class AsyncGenerator<T> extends AsyncMethodBody {
    final public LazyGenerator<T> generator;

    protected AsyncGenerator() {
        generator = new LazyGenerator<>();
    }
    
    public final void run() {
    	generator.begin();
    	try {
    		doRun();
    	} finally {
    		 generator.end();
    	}
    }
    
    abstract protected @continuable void doRun();

    protected @continuable static <T, V> T $$await$$(CompletionStage<T> future, AsyncGenerator<V> self) {
    	return AsyncExecutor.await(future);
    }
    
    protected @continuable static <T> Object $$yield$$(T readyValue, AsyncGenerator<T> self) {
        return self.generator.produce(readyValue);
    }

    protected @continuable static <T> Object $$yield$$(CompletionStage<T> pendingValue, AsyncGenerator<T> self) {
        return self.generator.produce(pendingValue);
    }

    protected @continuable static <T> Object $$yield$$(Generator<T> values, AsyncGenerator<T> self) {
        return self.generator.produce(values);
    }
}
