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

import net.tascalate.concurrent.CompletablePromise;

class ResultPromise<T> extends CompletablePromise<T> {
    private final AsyncMethod asyncMethod;
	
    ResultPromise(AsyncMethod asyncMethod) {
        this(new CompletableFuture<T>(), asyncMethod);
    }
	

    ResultPromise(CompletableFuture<T> delegate, AsyncMethod asyncMethod) {
        super(delegate);
        this.asyncMethod = asyncMethod;
    }
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean doCancel = mayInterruptIfRunning || !asyncMethod.isRunning();
        if (!doCancel) {
            return false;
        }
        if (super.cancel(mayInterruptIfRunning)) {
            asyncMethod.cancelAwaitIfNecessary();
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected <U> ResultPromise<U> wrap(CompletionStage<U> original) {
        return new ResultPromise<>((CompletableFuture<U>)original, asyncMethod);
    }
    
    void internalCompleWithResult(T result) {
        onSuccess(result);
    }
    
    void internalCompleWithFailure(Throwable exception) {
        onFailure(exception);
    }

}
