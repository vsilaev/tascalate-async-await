package net.tascalate.async.core;

import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.async.api.Generator;

abstract public class AsyncGenerator<T> extends AsyncMethodBody {
    public final LazyGenerator<T> generator;

    protected AsyncGenerator(ContextualExecutor contextualExecutor) {
        super(contextualExecutor);
        generator = new LazyGenerator<>();
    }
    
    @Override
    public final @continuable void run() {
    	generator.begin();
    	boolean success = false;
    	try {
    		doRun();
    		success = true;
    	} catch (Throwable ex) {
    	    generator.end(ex);
    	} finally {
    	    if (success) {
    	        generator.end(null);
    	    }
    	}
    }
    
    abstract protected @continuable void doRun() throws Throwable;

    protected @continuable static <T, V> T $$await$$(CompletionStage<T> future, AsyncGenerator<V> self) {
    	return AsyncMethodExecutor.await(future);
    }
    
    protected static <T> Generator<T> $$yield$$(AsyncGenerator<T> self) {
        return self.generator;
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
