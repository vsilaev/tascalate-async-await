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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import java.util.function.Function;

final class SchedulerExecutorAdapter implements Scheduler {
    private final Executor executor;
    private final Function<? super Runnable, ? extends Runnable> contextualizer;
    
    static final Scheduler SAME_THREAD_SCHEDULER = new SchedulerExecutorAdapter(Runnable::run);
    
    SchedulerExecutorAdapter(Executor executor) {
        this(executor, null);
    }
    
    SchedulerExecutorAdapter(Executor executor, Function<? super Runnable, ? extends Runnable> contextualizer) {
        this.executor = executor;
        this.contextualizer = contextualizer;
    }
    
    @Override
    public CompletionStage<?> schedule(Runnable command) {
        CompletableFuture<?> result = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                command.run();
                result.complete(null);
            } catch (final Throwable ex) {
                result.completeExceptionally(ex);
            }
        });
        return result;
    }
    
    @Override
    public Runnable contextualize(Runnable resumeContinuation) {
        return contextualizer == null ? resumeContinuation : contextualizer.apply(resumeContinuation);
    } 

}