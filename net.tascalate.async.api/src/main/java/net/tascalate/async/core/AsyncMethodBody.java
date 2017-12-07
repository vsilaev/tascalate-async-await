package net.tascalate.async.core;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.api.ContextualExecutor;

abstract public class AsyncMethodBody implements Runnable {
    private final ContextualExecutor contextualExecutor;
    
    protected AsyncMethodBody(ContextualExecutor contextualExecutor) {
        this.contextualExecutor = contextualExecutor != null ? 
            contextualExecutor : ContextualExecutor.sameThreadContextless();
    }
    
    abstract public @continuable void run();
    
    ContextualExecutor contextualExecutor() {
        return contextualExecutor;
    }
}
