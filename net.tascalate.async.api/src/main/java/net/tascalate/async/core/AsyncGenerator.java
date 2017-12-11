/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.async.api.Generator;
import net.tascalate.concurrent.Promise;

abstract public class AsyncGenerator<T> extends AsyncMethodBody {
    public final LazyGenerator<T> generator;
    public final Promise<T> promise;
    
    protected AsyncGenerator(ContextualExecutor contextualExecutor) {
        super(contextualExecutor);
        this.promise   = new ResultPromise<>(this);
        this.generator = new LazyGenerator<>((ResultPromise<T>)promise);
    }
    
    @Override
    protected final @continuable void internalRun() {
    	generator.begin();
    	boolean success = false;
    	try {
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
    
    abstract protected @continuable void doRun() throws Throwable;

    protected @continuable static <T, V> T $$await$$(CompletionStage<T> future, AsyncGenerator<V> self) {
    	return AsyncMethodExecutor.await(future);
    }
    
    protected static <T> Generator<T> $$yield$$(AsyncGenerator<T> self) {
        return self.generator;
    }
    
    protected @continuable static <T> Object $$yield$$(T readyValue, AsyncGenerator<T> self) {
        return self.generator.produce(readyValue);
    }

    protected @continuable static <T> Object $$yield$$(CompletionStage<T> pendingValue, AsyncGenerator<T> self) {
        return self.generator.produce(pendingValue);
    }

    protected @continuable static <T> Object $$yield$$(Generator<T> values, AsyncGenerator<T> self) {
        return self.generator.produce(values);
    }
    
    @Override
    protected void cancelAwaitIfNecessary(CompletableFuture<?> terminateMethod, CompletionStage<?> originalAwait) {
        if (promise.isCancelled()) {
            super.cancelAwaitIfNecessary(terminateMethod, originalAwait);
        }
    }

}
