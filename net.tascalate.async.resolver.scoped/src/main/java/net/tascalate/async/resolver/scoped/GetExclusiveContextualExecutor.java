package net.tascalate.async.resolver.scoped;

import org.kohsuke.MetaInfServices;

import net.tascalate.async.spi.ContextualExecutorResolver;

@MetaInfServices(ContextualExecutorResolver.class)
public class GetExclusiveContextualExecutor extends AbstractScopedContextualExecutor {
    public GetExclusiveContextualExecutor() {
        super(ContextualExecutorScope.EXCLUSIVE, 1000);
    }
}
