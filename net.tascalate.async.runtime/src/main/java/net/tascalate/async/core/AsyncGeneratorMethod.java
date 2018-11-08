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
package net.tascalate.async.core;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.Scheduler;
import net.tascalate.async.Sequence;
import net.tascalate.async.YieldReply;
import net.tascalate.async.suspendable;

abstract public class AsyncGeneratorMethod<T> extends AbstractAsyncMethod {
    public final LazyGenerator<T> generator;
    
    protected AsyncGeneratorMethod(Scheduler scheduler) {
        super(scheduler);
        this.generator = new LazyGenerator<>(this);
    }
    
    @Override
    protected final @suspendable void internalRun() {
        boolean success = false;
        try {
    	    generator.begin();
    	    doRun();
    	    success = true;
        } catch (Throwable ex) {
            generator.end(ex);
        } finally {
            if (success) {
                generator.end(null);
            }
        }
    }
    
    abstract protected @suspendable void doRun() throws Throwable;
    
    final boolean checkDone() {
        if (future.isDone()) {
            // If we have synchronous error in generator method
            // (as opposed to asynchronous that is managed by consumerLock
            if (!future.isCancelled() && future.isCompletedExceptionally()) {
                try {
                    future.join();
                } catch (final CancellationException ex) {
                    // Should not happen -- completed exceptionally already checked
                    throw new IllegalStateException(ex);
                } catch (final CompletionException ex) {
                    Exceptions.sneakyThrow(Exceptions.unrollCompletionException(ex));
                }
            }
            return true;
        } else {
            return false;
        }
    }

    protected final AsyncGenerator<T> yield() {
        return generator;
    }
    
    protected @suspendable final YieldReply<T> yield(T readyValue) {
        return generator.produce(AsyncGenerator.from(readyValue));
    }

    protected @suspendable final YieldReply<T> yield(CompletionStage<T> pendingValue) {
        return generator.produce(Sequence.of(pendingValue));
    }

    protected @suspendable final YieldReply<T> yield(Sequence<? extends CompletionStage<T>> values) {
        return generator.produce(values);
    }
    
    final protected String toString(String className, String methodSignature) {
        return 
            toString("<generated-async-generator>", className, methodSignature) +
            String.format("[lazy-generator=%s]", generator);
    }
}
