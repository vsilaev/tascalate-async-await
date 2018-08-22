package net.tascalate.async.resolver.scoped;

import net.tascalate.async.Scheduler;
import net.tascalate.async.spi.SchedulerResolver;

public class AbstractScopedScheduler implements SchedulerResolver {

    private final SchedulerScope scope;
    private final int priority;
    
    protected AbstractScopedScheduler(SchedulerScope scope, int priority) {
        this.scope    = scope;
        this.priority = priority;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public Scheduler resolve(Object owner, Class<?> ownerDeclaringClass) {
        return scope.currentExecutor.get();
    }

    @Override
    public String toString() {
        return String.format("<scheduler-resolver{%s}>[priority=%d, scope=%s]", getClass().getSimpleName(), priority(), scope);
    }
}
