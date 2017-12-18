package net.tascalate.async.resolver.scoped;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

import net.tascalate.async.api.ContextualExecutor;

public class ContextualExecutorScope {
    
    public static void runWith(ContextualExecutor ctxExecutor, Runnable code) {
        supplyWith(ctxExecutor, () -> {
            code.run();
            return null;
        });
    }
    
    public static <V> V supplyWith(ContextualExecutor ctxExecutor, Supplier<V> code) {
        try {
            return callWith(ctxExecutor, code::get);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Unexpceted checked exception thrown", ex);
        }
    }
    
    
    public static <V> V callWith(ContextualExecutor ctxExecutor, Callable<V> code) throws Exception {
        ContextualExecutor previous = CURRENT_EXECUTOR.get();
        CURRENT_EXECUTOR.set(ctxExecutor);
        try {
            return code.call();
        } finally {
            if (null == previous) {
                CURRENT_EXECUTOR.remove();
            } else {
                CURRENT_EXECUTOR.set(previous);
            }
        }
    }
    
    
    static final ThreadLocal<ContextualExecutor> CURRENT_EXECUTOR = new ThreadLocal<>();
}
