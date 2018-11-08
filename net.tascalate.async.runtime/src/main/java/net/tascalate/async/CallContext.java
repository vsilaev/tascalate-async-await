/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;

import net.tascalate.async.core.AsyncMethodExecutor;
import net.tascalate.async.core.InternalCallContext;
import net.tascalate.javaflow.function.SuspendableFunction;

/**
 * @author Valery Silaev
 * 
 */
public class CallContext {

    private CallContext() {}
    
    /**
     * Wait for the {@link CompletionStage} within {@link async} method.
     * 
     * The {@link async} method will be suspended until {@link CompletionStage}
     * returns or throws the result.
     */
    public @suspendable static <T> T await(CompletionStage<T> future) throws CancellationException, InvalidCallContextException {
        return AsyncMethodExecutor.await(future);
    }
    
    public static boolean interrupted() throws InvalidCallContextException {
        // Implementation is used only in @suspendable methods
        // @async methods get this call replaced with optimized 
        // version that invokes instance method on generated class
        return InternalCallContext.interrupted(true);
    }

    public static <T, R extends CompletionStage<T>> R async(T value) {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

    public static <T> YieldReply<T> yield(T readyValue) throws InvalidCallContextException {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

    public static <T> YieldReply<T> yield(CompletionStage<T> pendingValue) throws CancellationException, InvalidCallContextException {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

    public static <T> YieldReply<T> yield(Sequence<? extends CompletionStage<T>> values) throws CancellationException, InvalidCallContextException {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

    public static <T> AsyncGenerator<T> yield() {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

    public static <E1 extends Throwable> void throwing(Class<E1> e1) throws E1 {}
    public static <E1 extends Throwable, 
                   E2 extends Throwable> void throwing(Class<E1> e1, Class<E2> e2) throws E1, E2 {}
    public static <E1 extends Throwable, 
                   E2 extends Throwable,
                   E3 extends Throwable> void throwing(Class<E1> e1, Class<E2> e2, Class<E3> e3) throws E1, E2, E3 {}
    public static <E1 extends Throwable, 
                   E2 extends Throwable,
                   E3 extends Throwable,
                   E4 extends Throwable> void throwing(Class<E1> e1, Class<E2> e2, Class<E3> e3, Class<E4> e4) throws E1, E2, E3, E4 {}
    public static <E1 extends Throwable,
                   E2 extends Throwable,
                   E3 extends Throwable,
                   E4 extends Throwable,
                   E5 extends Throwable> void throwing(Class<E1> e1, Class<E2> e2, Class<E3> e3, Class<E4> e4, Class<E5> e5) throws E1, E2, E3, E4, E5 {}
    
    public static <T> SuspendableFunction<CompletionStage<T>, T> awaitValue() {
        return new SuspendableFunction<CompletionStage<T>, T>() {
            @Override
            public T apply(CompletionStage<T> future) {
                return AsyncMethodExecutor.await(future);
            }
        };
    }
}
