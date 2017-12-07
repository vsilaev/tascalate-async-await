package net.tascalate.async.api;

import java.util.concurrent.Executor;

public interface ContextualExecutor extends Executor {
    
    default Runnable captureContext(Runnable resumeContinuation) {
        return resumeContinuation;
    }
    
    default void scopedRun(Runnable code) {
        ContextualExecutors.scopedRun(this, code);
    }
    
    public static ContextualExecutor from(Executor executor) {
        return executor::execute;
    }
    
    public static ContextualExecutor sameThreadContextless() {
        return ContextualExecutors.SAME_THREAD_EXECUTOR;
    }
}
