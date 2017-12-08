package net.tascalate.async.api;

public interface ContextualExecutorResolver {
    default ContextualExecutor resolveByOwner(Object owner) {
        return null;
    }
    
    abstract ContextualExecutor resolveByContext();
}
