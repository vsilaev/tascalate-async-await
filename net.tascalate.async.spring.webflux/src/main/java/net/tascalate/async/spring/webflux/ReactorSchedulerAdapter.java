/**
 * Copyright 2015-2025 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.spring.webflux;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import net.tascalate.async.Scheduler;
import reactor.core.publisher.Mono;

public class ReactorSchedulerAdapter implements Scheduler {
    private final reactor.core.scheduler.Scheduler delegate;
    private final Function<? super Runnable, ? extends Runnable> contextualizer;
    
    public ReactorSchedulerAdapter(reactor.core.scheduler.Scheduler delegate) {
        this(delegate, Function.identity());
    }
    
    public ReactorSchedulerAdapter(reactor.core.scheduler.Scheduler delegate, Function<? super Runnable, ? extends Runnable> contextualizer) {
        this.delegate = delegate;
        this.contextualizer = contextualizer;
    }
    
    @Override
    public Set<Characteristics> characteristics() {
        return EnumSet.of(Scheduler.Characteristics.INTERRUPTIBLE);
    }
    
    @Override
    public Runnable contextualize(Runnable resumeContinuation) {
        return contextualizer.apply(resumeContinuation);
    }
    
    @Override
    public CompletionStage<?> schedule(Runnable runnable) {
        return Mono.just(runnable)
                   .subscribeOn(delegate)
                   .map(r -> {
                       r.run();
                       return null;
                   }).toFuture();
    }
}
