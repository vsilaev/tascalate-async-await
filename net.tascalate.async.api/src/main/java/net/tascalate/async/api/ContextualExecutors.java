package net.tascalate.async.api;

public final class ContextualExecutors {
    private ContextualExecutors() {
        
    }
    
    public static void scopedRun(ContextualExecutor ctxExecutor, Runnable code) {
        ContextualExecutor previous = CURRENT_EXECUTOR.get();
        CURRENT_EXECUTOR.set(ctxExecutor);
        try {
            code.run();
        } finally {
            if (null == previous) {
                CURRENT_EXECUTOR.remove();
            } else {
                CURRENT_EXECUTOR.set(previous);
            }
        }
    }
    
    public static ContextualExecutor current(Object owner) {
        ContextualExecutor current = CURRENT_EXECUTOR.get();
        return null != current ? current : SAME_THREAD_EXECUTOR;
    }
    
    private 
    static final ThreadLocal<ContextualExecutor> CURRENT_EXECUTOR = new ThreadLocal<>();
    static final ContextualExecutor SAME_THREAD_EXECUTOR = ContextualExecutor.from(Runnable::run);
    
}
