package net.tascalate.async;

import java.util.concurrent.CompletionStage;

abstract public class TAsyncYield<T, MR> {
    
    protected TAsyncYield() {
        
    }
    
    public final static class Reply<T> {
        
        final public T value;
        final public Object param;
        
        public Reply(T value, Object param) {
            this.value = value;
            this.param = param;
        }
        
        @Override
        public String toString() {
            return String.format("%s[value=%s, param=%s]", getClass().getSimpleName(), value, param);
        }
    }
    
    public final MR yield() {
        return CallContext.methodCallMustBeReplaced();
    }
    
    public @suspendable final <R extends T> Reply<R> yield(R readyValue) {
        return CallContext.methodCallMustBeReplaced();
    }

    public @suspendable final <R extends T> Reply<R> yield(CompletionStage<R> pendingValue) {
        return CallContext.methodCallMustBeReplaced();
    }

    public @suspendable final <R extends T> Reply<R> yield(Sequence<? extends CompletionStage<R>> values) {
        return CallContext.methodCallMustBeReplaced();
    }
   
}
