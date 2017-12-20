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

import java.util.concurrent.Executor;
import java.util.function.Function;

public interface Scheduler extends Executor {
    
    default boolean interruptible() {
        return false;
    }
    
    default Runnable contextualize(Runnable resumeContinuation) {
        return resumeContinuation;
    }
    
    public static Scheduler from(Executor executor) {
        return executor::execute;
    }
    
    public static Scheduler from(Executor executor, boolean interruptible) {
    	return from(executor, interruptible, Function.identity());
    }
    
    public static Scheduler from(Executor executor, Function<? super Runnable, ? extends Runnable> contextualizer) {
    	return from(executor, true, contextualizer);
    }
    
    public static Scheduler from(Executor executor, boolean interruptible, Function<? super Runnable, ? extends Runnable> contextualizer) {
        return new Scheduler() {
        	
        	@Override
        	public boolean interruptible() {
                return interruptible;
            }
			
        	@Override
        	public Runnable contextualize(Runnable resumeContinuation) {
        		return contextualizer == null ? resumeContinuation : contextualizer.apply(resumeContinuation);
        	}
        	
			@Override
			public void execute(Runnable command) {
				executor.execute(command);
			}
		};
    }    
    
    public static Scheduler sameThreadContextless() {
        return PackagePrivate.SAME_THREAD_SCHEDULER;
    }
}
