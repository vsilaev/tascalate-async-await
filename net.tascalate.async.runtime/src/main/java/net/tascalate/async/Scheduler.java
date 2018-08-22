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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import net.tascalate.async.scheduler.InterruptibleScheduler;
import net.tascalate.async.scheduler.SimpleScheduler;

public interface Scheduler {
    
    public enum Characteristics {
        INTERRUPTIBLE;
    }
    
    default Set<Characteristics> characteristics() {
        return Collections.emptySet();
    }
    
    default Runnable contextualize(Runnable resumeContinuation) {
        return resumeContinuation;
    }
    
    abstract public CompletionStage<?> schedule(Runnable runnable);
    
    public static Scheduler sameThreadContextless() {
        return SimpleScheduler.SAME_THREAD_SCHEDULER;
    }
    
    public static Scheduler nonInterruptible(Executor executor) {
        return new SimpleScheduler(executor);
    }
    
    public static Scheduler nonInterruptible(Executor executor, Function<? super Runnable, ? extends Runnable> contextualizer) {
        return new SimpleScheduler(executor, contextualizer);
    }
    
    public static Scheduler interruptible(ExecutorService executor) {
        return new InterruptibleScheduler(executor);
    }
    
    public static Scheduler interruptible(ExecutorService executor, Function<? super Runnable, ? extends Runnable> contextualizer) {
        return new InterruptibleScheduler(executor, contextualizer);
    }
}
