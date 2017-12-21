package net.tascalate.async.core;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

final class Exceptions {
    private Exceptions() {}
    
    @SuppressWarnings("unchecked")
    static <T, E extends Throwable> T sneakyThrow(Throwable ex) throws E {
        throw (E)ex;
    }
    

    
    static Throwable unrollExecutionException(Throwable ex) {
        Throwable nested = ex;
        while (nested instanceof ExecutionException) {
            nested = nested.getCause();
        }
        return null == nested ? ex : nested;
    }
    
    static Throwable unrollCompletionException(Throwable ex) {
        Throwable nested = ex;
        while (nested instanceof CompletionException) {
            nested = nested.getCause();
        }
        return null == nested ? ex : nested;
    }
    
}
