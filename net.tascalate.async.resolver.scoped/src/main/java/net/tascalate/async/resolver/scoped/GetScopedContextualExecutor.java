package net.tascalate.async.resolver.scoped;

import org.kohsuke.MetaInfServices;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.async.spi.ContextualExecutorResolver;

@MetaInfServices
public class GetScopedContextualExecutor implements ContextualExecutorResolver {

    @Override
    public int priority() {
        return 1000;
    }

    @Override
    public ContextualExecutor resolve(Object owner, Class<?> ownerDeclaringClass) {
        return ContextualExecutorScope.CURRENT_EXECUTOR.get();
    }

}
