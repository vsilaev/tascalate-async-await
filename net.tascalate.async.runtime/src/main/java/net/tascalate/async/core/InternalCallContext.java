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

import org.apache.commons.javaflow.core.StackRecorder;

import net.tascalate.async.InvalidCallContextException;
import net.tascalate.async.Scheduler;

public class InternalCallContext {
    private InternalCallContext() {}
    
    public static Scheduler scheduler(boolean asyncCallMustBeAvailable) {
        AbstractAsyncMethod asyncMethod = asyncMethod(asyncCallMustBeAvailable);
        return asyncMethod != null ? asyncMethod.scheduler() : null;
    }
    
    public static boolean interrupted(boolean asyncCallMustBeAvailable) {
        AbstractAsyncMethod asyncMethod = asyncMethod(asyncCallMustBeAvailable);
        return asyncMethod != null && asyncMethod.interrupted();
    }
    
    static AbstractAsyncMethod asyncMethod() {
        return asyncMethod(true);
    }
    
    private static AbstractAsyncMethod asyncMethod(boolean mustBeAvailable) {
        StackRecorder stackRecorder = StackRecorder.get();
        if (null == stackRecorder && mustBeAvailable) {
            throw new InvalidCallContextException(
                "Continuation was continued incorrectly - are your classes instrumented for javaflow?"
            );
        }
        Runnable result = stackRecorder.getRunnable();
        if (result instanceof AbstractAsyncMethod) {
            return (AbstractAsyncMethod)result;
        } else if (mustBeAvailable) {
            throw new InvalidCallContextException(
                "Current runnable is not " + AbstractAsyncMethod.class.getName() + " - are your classes instrumented for javaflow?"
            );
        } else {
            return null;
        }
    }
}
