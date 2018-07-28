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
package net.tascalate.async.xpi;

import java.util.EnumSet;
import java.util.Set;

import java.util.concurrent.Executor;

import java.util.function.Function;

import net.tascalate.async.scheduler.AbstractExecutorScheduler;

import net.tascalate.concurrent.CompletableTask;
import net.tascalate.concurrent.Promise;

public class TaskScheduler extends AbstractExecutorScheduler<Executor> {

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
        super(executor, ensureInterruptibleCharacteristic(characteristics), contextualizer); 
    }
    
    @Override
    public Promise<?> schedule(Runnable command) {
        return CompletableTask.runAsync(command, executor);
    }
    
    private static Set<Characteristics> ensureInterruptibleCharacteristic(Set<Characteristics> characteristics) {
        if (null != characteristics && characteristics.contains(Characteristics.INTERRUPTIBLE)) {
            return characteristics;
        }
        throw new IllegalArgumentException("Characteristics must contains " + Characteristics.INTERRUPTIBLE);
    }
}
