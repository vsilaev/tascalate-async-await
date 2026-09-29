package net.tascalate.async.reactor;

import net.tascalate.async.TAsyncYield;
import net.tascalate.async.suspendable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class FluxYield<T> extends TAsyncYield<T, Flux<T>> {
    
    private FluxYield() {}
    
    public final @suspendable Reply<T> yield(Mono<T> mono) {
        return ReactorCallContext.methodCallMustBeReplaced();
    }
    
    public final @suspendable Reply<T> yield(Flux<T> mono) {
        return ReactorCallContext.methodCallMustBeReplaced();
    }
    
    static final FluxYield<Object> INSTANCE = new FluxYield<>(); 
}
