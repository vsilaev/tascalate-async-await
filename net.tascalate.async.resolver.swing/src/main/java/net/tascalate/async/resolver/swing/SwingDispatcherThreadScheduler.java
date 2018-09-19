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
package net.tascalate.async.resolver.swing;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.swing.SwingUtilities;

import net.tascalate.async.core.ResultPromise;
import net.tascalate.async.scheduler.AbstractScheduler;

public class SwingDispatcherThreadScheduler extends AbstractScheduler {

    public SwingDispatcherThreadScheduler() {
        this(null, null);
    }
    
    public SwingDispatcherThreadScheduler(Set<Characteristics> characteristics) {
        this(characteristics, null);
    }
    
    public SwingDispatcherThreadScheduler(Function<? super Runnable, ? extends Runnable> contextualizer) {
        this(null, contextualizer);
    }
    
    public SwingDispatcherThreadScheduler(Set<Characteristics> characteristics, Function<? super Runnable, ? extends Runnable> contextualizer) {
        super(ensureNonInterruptibleCharacteristic(characteristics), contextualizer);
    }

    @Override
    public CompletionStage<?> schedule(Runnable command) {
        if (SwingUtilities.isEventDispatchThread()) {
            command.run();
            return CompletableFuture.completedFuture(null);
        } else {
            SchedulePromise<?> result = new SchedulePromise<>();
            Runnable wrapper = new Runnable() {
                @Override
                public void run() {
                    try {
                        command.run();
                        result.success(null);
                    } catch (final Throwable ex) {
                        result.failure(ex);
                    }
                }
            };
            SwingUtilities.invokeLater(wrapper);
            return result;
        }
    }

    private static Set<Characteristics> ensureNonInterruptibleCharacteristic(Set<Characteristics> characteristics) {
        if (null == characteristics || !characteristics.contains(Characteristics.INTERRUPTIBLE)) {
            return characteristics;
        }
        throw new IllegalArgumentException("Characteristics must contains " + Characteristics.INTERRUPTIBLE);
    }
    
    static class SchedulePromise<T> extends ResultPromise<T> {
        SchedulePromise() {}
        
        final boolean success(T value) {
            return super.internalSuccess(value);
        }
        
        final boolean failure(Throwable exception) {
            return super.internalFailure(exception);
        }
        
        @Override
        protected final boolean internalSuccess(T value) {
            throw new UnsupportedOperationException("SchedulePromise may not be completed explicitly");
        }
        
        @Override
        protected final boolean internalFailure(Throwable exception) {
            throw new UnsupportedOperationException("SchedulePromise may not be completed explicitly");
        }
    }
}
