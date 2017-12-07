package net.tascalate.async.api;

import java.util.concurrent.Executor;

public interface ContextualExecutor extends Executor {
    
    default Runnable captureContext(Runnable resumeContinuation) {
        return resumeContinuation;
    }
    
    public static ContextualExecutor from(Executor executor) {
        return executor::execute;
    }
    
    public static final ContextualExecutor SAME_THREAD_EXECUTOR = from(Runnable::run);
}
