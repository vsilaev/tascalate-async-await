package net.tascalate.async.resolvers.inherited;

import org.kohsuke.MetaInfServices;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.async.spi.ActiveAsyncCall;
import net.tascalate.async.spi.ContextualExecutorResolver;

@MetaInfServices
public class InheritAsyncCallContextualExecutor implements ContextualExecutorResolver {

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public ContextualExecutor resolve(Object owner, Class<?> ownerDeclaringClass) {
        return ActiveAsyncCall.contextualExecutor();
    }
    
}
