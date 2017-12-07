package net.tascalate.async.core;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.api.ContextualExecutor;

abstract public class AsyncMethodBody implements Runnable {
    private ContextualExecutor contextualExecutor = ContextualExecutor.SAME_THREAD_EXECUTOR;
    
    abstract public @continuable void run();
    
    public ContextualExecutor contextualExecutor() {
        return contextualExecutor;
    }
}
