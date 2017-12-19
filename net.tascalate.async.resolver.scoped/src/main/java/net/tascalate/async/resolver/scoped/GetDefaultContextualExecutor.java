package net.tascalate.async.resolver.scoped;

import org.kohsuke.MetaInfServices;

import net.tascalate.async.spi.ContextualExecutorResolver;

@MetaInfServices(ContextualExecutorResolver.class)
public class GetDefaultContextualExecutor extends AbstractScopedContextualExecutor {
    public GetDefaultContextualExecutor() {
        super(ContextualExecutorScope.DEFAULTS, 200);
    }
}
