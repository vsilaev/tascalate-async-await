package net.tascalate.async.resolver.scoped;

import org.kohsuke.MetaInfServices;

import net.tascalate.async.spi.SchedulerResolver;

@MetaInfServices(SchedulerResolver.class)
public class GetDefaultsOverrideScheduler extends AbstractScopedScheduler {
    public GetDefaultsOverrideScheduler() {
        super(SchedulerScope.DEFAULTS_OVERRIDE, 200);
    }
}
