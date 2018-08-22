package net.tascalate.async.resolver.provided;

import org.kohsuke.MetaInfServices;

import net.tascalate.async.Scheduler;
import net.tascalate.async.spi.SchedulerProviderLookup;
import net.tascalate.async.spi.SchedulerResolver;

@MetaInfServices
public class GetSchedulerFromProvider implements SchedulerResolver {
    
    private final SchedulerProviderLookup lookup = new SchedulerProviderLookup(true, true, true, false);

    @Override
    public int priority() {
        return 500;
    }

    @Override
    public Scheduler resolve(Object owner, Class<?> ownerDeclaringClass) {
        if (null == owner) {
            if (null == ownerDeclaringClass) {
                return null;
            }
            SchedulerProviderLookup.ClassAccessor cAccessor = lookup.getClassAccessor(ownerDeclaringClass);
            return null != cAccessor ? cAccessor.read() : null;
        } else {
            // Use concrete owner class, though someone may find correct to use declaring class
            Class<?> targetClass = owner.getClass();  
            SchedulerProviderLookup.InstanceAccessor iAccessor = lookup.getInstanceAccessor(targetClass);
            if (null == iAccessor) {
                // If no instance accessor then try class accessor
                SchedulerProviderLookup.ClassAccessor cAccessor = lookup.getClassAccessor(targetClass);
                return null != cAccessor ? cAccessor.read() : null;
            } else {
                return iAccessor.read(owner);
            }
        }
    }
    
    @Override
    public String toString() {
        return String.format("<scheduler-resolver{%s}>[priority=%d, lookup=%s]", getClass().getSimpleName(), priority(), lookup);
    }
}
