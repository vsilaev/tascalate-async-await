/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.mutiny;

import java.util.concurrent.CompletionStage;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.CallContext;
import net.tascalate.async.Scheduler;
import net.tascalate.async.suspendable;
import net.tascalate.async.core.AsyncMethodExecutor;

public final class MutinyCallContext {
    
    private MutinyCallContext() {}

    public static @suspendable <T> T await(Uni<T> uni) {
        return AsyncMethodExecutor.await(uni.subscribeAsCompletionStage());
    }

    // Synonym for async in "return async(value)" 
    // I.e. "return uni(value)"
    public static <T> Uni<T> uni(T value) {
        return methodCallMustBeReplaced(); 
    }
    
    @SuppressWarnings("unchecked")
    public static <T> MultiYield<T> multi() {
        return (MultiYield<T>)MultiYield.INSTANCE;
    }
    
    @SuppressWarnings("unchecked")
    public static <T> MultiYield<T> multi(Class<T> type) {
        return (MultiYield<T>)MultiYield.INSTANCE;
    }
    
    public static <T> AsyncGenerator<T> generator(Multi<? extends T> multi) {
        return MutinyAsyncAwaitBridge.createGenerator(multi, CallContext.scheduler());
    }
    
    public static <T> Uni<T> __convert(CompletionStage<T> completionStage) {
        return MutinyAsyncAwaitBridge.uni(completionStage);
    }
    
    public static <T> CompletionStage<T> __convert(Uni<T> uni) {
        return MutinyAsyncAwaitBridge.promise(uni);
    }
    
    public static <T> AsyncGenerator<T> __convert(Multi<? extends T> multi, Scheduler scheduler) {
        return MutinyAsyncAwaitBridge.createGenerator(multi, scheduler);
    }
    
    public static <T> Multi<T> __convert(AsyncGenerator<? extends T> generator) {
        return MutinyAsyncAwaitBridge.createMulti(generator);
    }
    
    static <R> R methodCallMustBeReplaced() {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }
}
