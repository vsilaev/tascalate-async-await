package net.tascalate.async.spi;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.async.core.AsyncMethodAccessor;

final public class ActiveAsyncCall {
    private ActiveAsyncCall() {}
    
    public static ContextualExecutor contextualExecutor() {
        return AsyncMethodAccessor.currentContextualExecutor();
    }
}
