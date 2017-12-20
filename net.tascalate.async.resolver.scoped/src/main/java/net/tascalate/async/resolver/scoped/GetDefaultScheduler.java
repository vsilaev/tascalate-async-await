package net.tascalate.async.resolver.scoped;

import org.kohsuke.MetaInfServices;

import net.tascalate.async.spi.SchedulerResolver;

@MetaInfServices(SchedulerResolver.class)
public class GetDefaultScheduler extends AbstractScopedScheduler {
    public GetDefaultScheduler() {
        super(SchedulerScope.DEFAULTS, 10);
    }
}
