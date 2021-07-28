package net.tascalate.async.examples.provider;

import java.lang.invoke.MethodHandles;

import net.tascalate.async.Scheduler;
import net.tascalate.async.SchedulerProvider;
import net.tascalate.async.spi.SchedulerProviderLookup;

public class ProviderTest {

    static class Abc {
        @SchedulerProvider
        private Scheduler exec = Scheduler.sameThreadContextless();
        public void run(SchedulerProviderLookup lookup) {
            ProviderTest.tryAccessor(lookup, this, MethodHandles.lookup());
        }
    }
    
    static class Xyz {
        @SchedulerProvider
        public Scheduler exec() {
            return Scheduler.sameThreadContextless();
        }
        public void run(SchedulerProviderLookup lookup) {
            ProviderTest.tryAccessor(lookup, this, MethodHandles.lookup());
        }
    }
    
    static class BaseByField {
        @SchedulerProvider
        Scheduler baseExec = Scheduler.sameThreadContextless();

    }

    static class InheritedByField extends BaseByField {
        public void run(SchedulerProviderLookup lookup) {
            ProviderTest.tryAccessor(lookup, this, MethodHandles.lookup());
        }
    }

    static class BaseByMethod {
        @SchedulerProvider
        protected Scheduler baseExec() {
            return Scheduler.sameThreadContextless();
        }
    }

    static class InheritedByMethod extends BaseByMethod {
        public void run(SchedulerProviderLookup lookup) {
            ProviderTest.tryAccessor(lookup, this, MethodHandles.lookup());
        }
    }

    interface IntfA {
        //@SchedulerProvider //-- uncomment to see error
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
        
        public void run(SchedulerProviderLookup lookup) {
            ProviderTest.tryAccessor(lookup, this, MethodHandles.lookup());
        }
     
    }
    
    public static void main(String[] args) {
        SchedulerProviderLookup lookup = new SchedulerProviderLookup(true, true, true, false);
        new Abc().run(lookup);
        new Xyz().run(lookup);
        new InheritedByField().run(lookup);
        new InheritedByMethod().run(lookup);
        new InheritedByInterfaces().run(lookup);

    }
    
    static void tryAccessor(SchedulerProviderLookup lookup, Object o, MethodHandles.Lookup ownerLookup) {
        SchedulerProviderLookup.InstanceAccessor reader = lookup.getInstanceAccessor(ownerLookup);
        System.out.println("Class: " + o.getClass().getName());
        System.out.println("Accessor: " + reader);
        if (null != reader) {
            System.out.println("Value: " + reader.read(o));
        }
    }

}
