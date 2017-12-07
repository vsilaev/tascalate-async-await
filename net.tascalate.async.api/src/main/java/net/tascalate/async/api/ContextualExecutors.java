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

public final class ContextualExecutors {
    private ContextualExecutors() {
        
    }
    
    public static void scopedRun(ContextualExecutor ctxExecutor, Runnable code) {
        ContextualExecutor previous = CURRENT_EXECUTOR.get();
        CURRENT_EXECUTOR.set(ctxExecutor);
        try {
            code.run();
        } finally {
            if (null == previous) {
                CURRENT_EXECUTOR.remove();
            } else {
                CURRENT_EXECUTOR.set(previous);
            }
        }
    }
    
    public static ContextualExecutor current(Object owner) {
        ContextualExecutor current = CURRENT_EXECUTOR.get();
        return null != current ? current : SAME_THREAD_EXECUTOR;
    }
    
    private 
    static final ThreadLocal<ContextualExecutor> CURRENT_EXECUTOR = new ThreadLocal<>();
    static final ContextualExecutor SAME_THREAD_EXECUTOR = ContextualExecutor.from(Runnable::run);
    
}
