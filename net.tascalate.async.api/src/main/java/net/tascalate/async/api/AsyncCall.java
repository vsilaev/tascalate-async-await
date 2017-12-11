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
package net.tascalate.async.api;

import java.util.concurrent.CompletionStage;

import net.tascalate.concurrent.Promise;

/**
 * @author Valery Silaev
 * 
 */
public class AsyncCall {

    /**
     * Wait for the {@link CompletionStage} within {@link async} method.
     * 
     * The {@link async} method will be suspended until {@link CompletionStage}
     * returns or throws the result.
     */
    public static <T> T await(CompletionStage<T> condition) throws NoActiveAsyncCallException {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

    public static <T> Promise<T> asyncResult(final T value) {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

    public static <T> Object yield(T readyValue) throws NoActiveAsyncCallException {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

    public static <T> Object yield(CompletionStage<T> pendingValue) throws NoActiveAsyncCallException {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

    public static <T> Object yield(Generator<T> values) throws NoActiveAsyncCallException {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

    public static <T> Generator<T> yield() {
        throw new IllegalStateException("Method call must be replaced by bytecode enhancer");
    }

}
