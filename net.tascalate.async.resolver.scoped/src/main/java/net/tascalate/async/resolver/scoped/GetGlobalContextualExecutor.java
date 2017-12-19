package net.tascalate.async.resolver.scoped;

import org.kohsuke.MetaInfServices;

import net.tascalate.async.spi.ContextualExecutorResolver;

@MetaInfServices(ContextualExecutorResolver.class)
public class GetGlobalContextualExecutor extends AbstractScopedContextualExecutor {
    public GetGlobalContextualExecutor() {
        super(ContextualExecutorScope.GLOBAL, 10);
    }
}
