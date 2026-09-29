/**
 * Copyright 2015-2025 Valery Silaev (http://vsilaev.com)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.

 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.tascalate.async.reactor;

import java.util.concurrent.CompletionStage;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.CallContext;
import net.tascalate.async.Scheduler;
import net.tascalate.async.suspendable;
import net.tascalate.async.core.AsyncMethodExecutor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class ReactorCallContext {
    
    private ReactorCallContext() {}

    public static @suspendable <T> T await(Mono<T> mono) {
        return AsyncMethodExecutor.await(mono.toFuture());
    }

    // Synonym for async in "return async(value)" 
    // I.e. "return mono(value)"
    public static <T> Mono<T> mono(T value) {
        return methodCallMustBeReplaced(); 
    }
    
    @SuppressWarnings("unchecked")
    public static <T> FluxYield<T> flux() {
        return (FluxYield<T>)FluxYield.INSTANCE;
    }
    
    @SuppressWarnings("unchecked")
    public static <T> FluxYield<T> flux(Class<T> type) {
        return (FluxYield<T>)FluxYield.INSTANCE;
    }
    
    public static <T> AsyncGenerator<T> generator(Flux<T> flux) {
        return ReactorAsyncAwaitBridge.createGenerator(flux, CallContext.scheduler());
    }
    
    public static <T> Mono<T> __convert(CompletionStage<T> completionStage) {
        return ReactorAsyncAwaitBridge.mono(completionStage);
    }
    
    public static <T> CompletionStage<T> __convert(Mono<T> mono) {
        return ReactorAsyncAwaitBridge.promise(mono);
    }
    
    public static <T> AsyncGenerator<T> __convert(Flux<T> flux, Scheduler scheduler) {
        return ReactorAsyncAwaitBridge.createGenerator(flux, scheduler);
    }
    
    public static <T> Flux<T> __convert(AsyncGenerator<T> generator) {
        return ReactorAsyncAwaitBridge.createFlux(generator);
    }
    
    static <R> R methodCallMustBeReplaced() {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }
}
