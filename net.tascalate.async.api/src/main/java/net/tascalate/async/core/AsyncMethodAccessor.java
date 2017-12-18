package net.tascalate.async.core;

import org.apache.commons.javaflow.core.StackRecorder;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.async.api.NoActiveAsyncCallException;

public class AsyncMethodAccessor {
    private AsyncMethodAccessor() {}
    
    public static ContextualExecutor currentContextualExecutor() {
        AsyncMethod asyncMethod = currentAsyncMethod(false);
        return asyncMethod != null ? asyncMethod.contextualExecutor() : null;
    }
    
    static AsyncMethod currentAsyncMethod() {
        return currentAsyncMethod(true);
    }
    
    private static AsyncMethod currentAsyncMethod(boolean mustBeAvailable) {
        StackRecorder stackRecorder = StackRecorder.get();
        if (null == stackRecorder && mustBeAvailable) {
            throw new NoActiveAsyncCallException(
                "Continuation was continued incorrectly - are your classes instrumented for javaflow?"
            );
        }
        Runnable result = stackRecorder.getRunnable();
        if (result instanceof AsyncMethod) {
            return (AsyncMethod)result;
        } else if (mustBeAvailable) {
            throw new NoActiveAsyncCallException(
                "Current runnable is not " + AsyncMethod.class.getName() + " - are your classes instrumented for javaflow?"
            );
        } else {
            return null;
        }
    }
}
