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
package net.tascalate.async.xpi;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import net.tascalate.async.api.Scheduler;
import net.tascalate.concurrent.CompletableTask;

public class TaskScheduler implements Scheduler {
    
    private final Executor executor;
    private final Set<Characteristics> characteristics;
    private final Function<? super Runnable, ? extends Runnable> contextualizer;

    public TaskScheduler(Executor executor) {
        this(executor, EnumSet.of(Characteristics.INTERRUPTIBLE));
    }
    
    public TaskScheduler(Executor executor, Set<Characteristics> characteristics) {
        this(executor, characteristics, null);
    }
    
    public TaskScheduler(Executor executor, Function<? super Runnable, ? extends Runnable> contextualizer) {
        this(executor, EnumSet.of(Characteristics.INTERRUPTIBLE), contextualizer);
    }
    
    public TaskScheduler(Executor executor, Set<Characteristics> characteristics, Function<? super Runnable, ? extends Runnable> contextualizer) {
        this.characteristics = characteristics != null ? 
            Collections.unmodifiableSet(ensureInterruptibleCharacteristic(characteristics)) 
            : 
            Collections.emptySet();
        this.contextualizer  = contextualizer;
        this.executor = executor;
    }
    
    @Override
    public CompletionStage<?> schedule(Runnable command) {
        return CompletableTask.runAsync(command, executor);
    }
    
    @Override
    public Set<Characteristics> characteristics() {
        return characteristics;
    }
    
    @Override
    public Runnable contextualize(Runnable resumeContinuation) {
        return contextualizer == null ? resumeContinuation : contextualizer.apply(resumeContinuation);
    }     
    
    private static Set<Characteristics> ensureInterruptibleCharacteristic(Set<Characteristics> characteristics) {
        if (null == characteristics || characteristics.isEmpty()) {
            return EnumSet.of(Characteristics.INTERRUPTIBLE);
        }
        Set<Characteristics> result = EnumSet.copyOf(characteristics);
        result.add(Characteristics.INTERRUPTIBLE);
        return result;
    }
}
