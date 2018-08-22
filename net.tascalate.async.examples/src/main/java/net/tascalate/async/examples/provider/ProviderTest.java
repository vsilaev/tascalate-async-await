package net.tascalate.async.examples.provider;

import net.tascalate.async.Scheduler;
import net.tascalate.async.SchedulerProvider;
import net.tascalate.async.spi.SchedulerProviderLookup;

public class ProviderTest {

    static class Abc {
        @SchedulerProvider
        private Scheduler exec = Scheduler.sameThreadContextless();
    }
    
    static class Xyz {
        @SchedulerProvider
        public Scheduler exec() {
            return Scheduler.sameThreadContextless();
        }
    }
    
    static class BaseByField {
        @SchedulerProvider
        Scheduler baseExec = Scheduler.sameThreadContextless();
    }

    static class InheritedByField extends BaseByField {

    }

    static class BaseByMethod {
        @SchedulerProvider
        protected Scheduler baseExec() {
            return Scheduler.sameThreadContextless();
        }
    }

    static class InheritedByMethod extends BaseByMethod {

    }

    interface IntfA {
        //@SchedulerProvider -- uncomment to see error
        Scheduler intfExec();
    }
    
    interface IntfB {
        @SchedulerProvider
        default Scheduler intfExec() {
            return Scheduler.sameThreadContextless();
        }
    }

    static class InheritedByInterfaces implements IntfA, IntfB {

        @Override
        public Scheduler intfExec() {
            return IntfB.super.intfExec();
        }
        
    }
    
    public static void main(String[] args) {
        SchedulerProviderLookup lookup = new SchedulerProviderLookup(true, true, true, false);
        tryAccessor(lookup, new Abc());
        tryAccessor(lookup, new Xyz());
        tryAccessor(lookup, new InheritedByField());
        tryAccessor(lookup, new InheritedByMethod());
        tryAccessor(lookup, new InheritedByInterfaces());

    }
    
    private static void tryAccessor(SchedulerProviderLookup lookup, Object o) {
        SchedulerProviderLookup.InstanceAccessor reader = lookup.getInstanceAccessor(o.getClass());
        System.out.println("Class: " + o.getClass().getName());
        System.out.println("Accessor: " + reader);
        if (null != reader) {
            System.out.println("Value: " + reader.read(o));
        }
    }

}
