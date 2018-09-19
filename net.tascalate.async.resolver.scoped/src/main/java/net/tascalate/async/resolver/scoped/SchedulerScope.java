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
package net.tascalate.async.resolver.scoped;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

import net.tascalate.async.Scheduler;

public enum SchedulerScope {
    DEFAULTS, DEFAULTS_OVERRIDE, PROVIDER_OVERRIDE;
    
    final ThreadLocal<Scheduler> currentExecutor = new ThreadLocal<>();
    
    public void runWith(Scheduler ctxExecutor, Runnable code) {
        supplyWith(ctxExecutor, () -> {
            code.run();
            return null;
        });
    }
    
    public <V> V supplyWith(Scheduler ctxExecutor, Supplier<V> code) {
        try {
            return callWith(ctxExecutor, code::get);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Unexpceted checked exception thrown", ex);
        }
    }
    
    
    public <V> V callWith(Scheduler ctxExecutor, Callable<V> code) throws Exception {
        Scheduler previous = currentExecutor.get();
        currentExecutor.set(ctxExecutor);
        try {
            return code.call();
        } finally {
            if (null == previous) {
                currentExecutor.remove();
            } else {
                currentExecutor.set(previous);
            }
        }
    }
}
