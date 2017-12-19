package net.tascalate.async.resolver.scoped;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

import net.tascalate.async.api.ContextualExecutor;

public enum ContextualExecutorScope {
    GLOBAL, DEFAULTS, EXCLUSIVE;
    
    final ThreadLocal<ContextualExecutor> currentExecutor = new ThreadLocal<>();
    
    public void runWith(ContextualExecutor ctxExecutor, Runnable code) {
        supplyWith(ctxExecutor, () -> {
            code.run();
            return null;
        });
    }
    
    public <V> V supplyWith(ContextualExecutor ctxExecutor, Supplier<V> code) {
        try {
            return callWith(ctxExecutor, code::get);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Unexpceted checked exception thrown", ex);
        }
    }
    
    
    public <V> V callWith(ContextualExecutor ctxExecutor, Callable<V> code) throws Exception {
        ContextualExecutor previous = currentExecutor.get();
        currentExecutor.set(ctxExecutor);
        try {
            return code.call();
        } finally {
            if (null == previous) {
                currentExecutor.remove();
            } else {
                currentExecutor.set(previous);
            }
        }
    }
}
