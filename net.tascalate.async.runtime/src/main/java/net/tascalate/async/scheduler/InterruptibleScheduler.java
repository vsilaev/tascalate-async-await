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
package net.tascalate.async.scheduler;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

public class InterruptibleScheduler extends AbstractExecutorScheduler<ExecutorService> {
    
    public InterruptibleScheduler(ExecutorService executor) {
        this(executor, EnumSet.of(Characteristics.INTERRUPTIBLE), null);
    }
    
    public InterruptibleScheduler(ExecutorService executor, Set<Characteristics> characteristics) {
        this(executor, characteristics, null);
    }

    public InterruptibleScheduler(ExecutorService executor, Function<? super Runnable, ? extends Runnable> contextualizer) {
        this(executor, EnumSet.of(Characteristics.INTERRUPTIBLE), contextualizer);
    }   
    
    public InterruptibleScheduler(ExecutorService executor, Set<Characteristics> characteristics, Function<? super Runnable, ? extends Runnable> contextualizer) {
        super(executor, ensureInterruptibleCharacteristic(characteristics), contextualizer);
    }
    
    @Override
    public CompletableFuture<?> schedule(Runnable command) {
        Future<?>[] delegate = {null};
        SchedulePromise<?> result = new SchedulePromise<Void>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (super.cancel(mayInterruptIfRunning)) {
                    delegate[0].cancel(mayInterruptIfRunning);
                    return true;
                } else {
                    return false;
                }
            }
        };
        Runnable wrapped = new Runnable() {
            @Override
            public void run() {
                try {
                    command.run();
                    result.internalSuccess(null);
                } catch (final Throwable ex) {
                    result.internalFailure(ex);
                    throw ex;
                }
            }
        };
        delegate[0] = executor.submit(wrapped);
        return result;
    }
    
    private static Set<Characteristics> ensureInterruptibleCharacteristic(Set<Characteristics> characteristics) {
        if (null != characteristics && characteristics.contains(Characteristics.INTERRUPTIBLE)) {
            return characteristics;
        }
        throw new IllegalArgumentException("Characteristics must contains " + Characteristics.INTERRUPTIBLE);
    }
}