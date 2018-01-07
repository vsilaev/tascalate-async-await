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

import net.tascalate.async.api.Scheduler;
import net.tascalate.async.api.suspendable;
import net.tascalate.concurrent.CompletablePromise;
import net.tascalate.concurrent.CompletableTask;
import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.PromiseOrigin;

abstract public class AsyncTask<T> extends AsyncMethod {
    public final Promise<T> promise;
    
    protected AsyncTask(Scheduler scheduler) {
        super(scheduler);
        @SuppressWarnings("unchecked")
        CompletableFuture<T> future = (CompletableFuture<T>)this.future; 
        this.promise = scheduler.interruptible() ?
            // For interruptible Scheduler use AbstractCompletableTask
            CompletableTask
                .asyncOn(scheduler)
                .dependent()
                .thenCombine(future, (a, b) -> b, PromiseOrigin.PARAM_ONLY)
            :
            // For non-interruptible use regular wrapper    
            new CompletablePromise<>(future);
    }
    
    @Override
    protected final @suspendable void internalRun() {
        try {
            doRun();
            // ensure that promise is resolved
            complete(null);
        } catch (Throwable ex) {
            future.completeExceptionally(ex);
        }
    }
    
    abstract protected @suspendable void doRun() throws Throwable;

    protected Promise<T> complete(final T value) {
        @SuppressWarnings("unchecked")
        CompletableFuture<T> future = (CompletableFuture<T>)this.future; 
        future.complete(value);
        return promise;
    }
  
}
