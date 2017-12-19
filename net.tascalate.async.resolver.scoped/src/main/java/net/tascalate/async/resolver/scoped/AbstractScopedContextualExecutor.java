package net.tascalate.async.resolver.scoped;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.async.spi.ContextualExecutorResolver;

public class AbstractScopedContextualExecutor implements ContextualExecutorResolver {

    private final ContextualExecutorScope scope;
    private final int priority;
    
    protected AbstractScopedContextualExecutor(ContextualExecutorScope scope, int priority) {
        this.scope    = scope;
        this.priority = priority;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public ContextualExecutor resolve(Object owner, Class<?> ownerDeclaringClass) {
        return scope.currentExecutor.get();
    }

}
