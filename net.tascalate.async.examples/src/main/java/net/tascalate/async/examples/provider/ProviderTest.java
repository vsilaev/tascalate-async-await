package net.tascalate.async.examples.provider;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.async.api.ContextualExecutorProvider;
import net.tascalate.async.spi.ContextualExecutorProviderLookup;

public class ProviderTest {

    static class Abc {
        @ContextualExecutorProvider
        private ContextualExecutor exec = ContextualExecutor.sameThreadContextless();
    }
    
    static class Xyz {
        @ContextualExecutorProvider
        public ContextualExecutor exec() {
            return ContextualExecutor.sameThreadContextless();
        }
    }
    
    static class BaseByField {
        @ContextualExecutorProvider
        ContextualExecutor baseExec = ContextualExecutor.sameThreadContextless();
    }

    static class InheritedByField extends BaseByField {

    }

    static class BaseByMethod {
        @ContextualExecutorProvider
        protected ContextualExecutor baseExec() {
            return ContextualExecutor.sameThreadContextless();
        }
    }

    static class InheritedByMethod extends BaseByMethod {

    }

    interface IntfA {
        //@ContextualExecutorProvider -- uncomment to see error
        ContextualExecutor intfExec();
    }
    
    interface IntfB {
        @ContextualExecutorProvider
        default ContextualExecutor intfExec() {
            return ContextualExecutor.sameThreadContextless();
        }
    }

    static class InheritedByInterfaces implements IntfA, IntfB {

        @Override
        public ContextualExecutor intfExec() {
            return IntfB.super.intfExec();
        }
        
    }
    
    public static void main(String[] args) {
        ContextualExecutorProviderLookup lookup = new ContextualExecutorProviderLookup(true, true, true, false);
        tryAccessor(lookup, new Abc());
        tryAccessor(lookup, new Xyz());
        tryAccessor(lookup, new InheritedByField());
        tryAccessor(lookup, new InheritedByMethod());
        tryAccessor(lookup, new InheritedByInterfaces());

    }
    
    private static void tryAccessor(ContextualExecutorProviderLookup lookup, Object o) {
        ContextualExecutorProviderLookup.Accessor reader = lookup.getAccessor(o.getClass());
        System.out.println("Class: " + o.getClass().getName());
        System.out.println("Accessor: " + reader);
        if (null != reader) {
            System.out.println("Value: " + reader.read(o));
        }
    }

}
