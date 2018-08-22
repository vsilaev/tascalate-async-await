package net.tascalate.async.resolver.scoped;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

import net.tascalate.async.Scheduler;

public enum SchedulerScope {
    DEFAULTS, DEFAULTS_OVERRIDE, PROVIDER_OVERRIDE;
    
    final ThreadLocal<Scheduler> currentExecutor = new ThreadLocal<>();
    
    public void runWith(Scheduler ctxExecutor, Runnable code) {
        supplyWith(ctxExecutor, () -> {
            code.run();
            return null;
        });
    }
    
    public <V> V supplyWith(Scheduler ctxExecutor, Supplier<V> code) {
        try {
            return callWith(ctxExecutor, code::get);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Unexpceted checked exception thrown", ex);
        }
    }
    
    
    public <V> V callWith(Scheduler ctxExecutor, Callable<V> code) throws Exception {
        Scheduler previous = currentExecutor.get();
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
