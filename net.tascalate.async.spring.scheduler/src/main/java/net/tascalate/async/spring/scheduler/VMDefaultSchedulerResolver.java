package net.tascalate.async.spring.scheduler;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.concurrent.atomic.AtomicBoolean;

import net.tascalate.async.Scheduler;
import net.tascalate.async.spi.MethodDefinition;
import net.tascalate.async.spi.SchedulerResolver;

public class VMDefaultSchedulerResolver implements SchedulerResolver {

    private static volatile SchedulerResolver DELEGATE;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    
    @Override
    public int priority() {
        SchedulerResolver delegate = DELEGATE;
        if (null == delegate) {
            throw new IllegalStateException("VM Scheduler Resolver has no delegate installed yet");
        }
        return delegate.priority();
    }

    @Override
    public Scheduler resolve(Object owner, Lookup ownerClassLookup, MethodDefinition methodDef) {
        SchedulerResolver delegate = DELEGATE;
        return null == delegate ? null : delegate.resolve(owner, ownerClassLookup, methodDef);
    }

    public static void install(SchedulerResolver delegate) {
        if (INSTALLED.compareAndSet(false, true)) {
            DELEGATE = delegate;
        } else {
            throw new IllegalStateException("VM Scheduler Resolver delegate was already installed");
        }
    }
}
