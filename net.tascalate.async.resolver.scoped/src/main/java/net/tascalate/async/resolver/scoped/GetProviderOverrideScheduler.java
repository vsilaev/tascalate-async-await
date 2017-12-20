package net.tascalate.async.resolver.scoped;

import org.kohsuke.MetaInfServices;

import net.tascalate.async.spi.SchedulerResolver;

@MetaInfServices(SchedulerResolver.class)
public class GetProviderOverrideScheduler extends AbstractScopedScheduler {
    public GetProviderOverrideScheduler() {
        super(SchedulerScope.PROVIDER_OVERRIDE, 1000);
    }
}
