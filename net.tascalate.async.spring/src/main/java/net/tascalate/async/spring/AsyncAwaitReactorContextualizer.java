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
package net.tascalate.async.spring;

import java.util.Optional;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.stereotype.Component;

import net.tascalate.async.Scheduler;
import net.tascalate.async.scheduler.ContextualizerOwner;
import reactor.core.scheduler.Schedulers;

@Component
@ConditionalOnWebApplication(type = Type.ANY)
@ConditionalOnClass({Schedulers.class})
@ConditionalOnProperty(name = "async-await.async-await-reactor-contextualizer.enable", havingValue = "true", matchIfMissing = true)
class AsyncAwaitReactorContextualizer extends AbstractSmartLifecycle {
    
    private final Scheduler scheduler;
    private final Optional<Function<? super Runnable, ? extends Runnable>> contextualizer;
    
    AsyncAwaitReactorContextualizer(@DefaultAsyncAwaitScheduler Scheduler scheduler,
                                    @DefaultAsyncAwaitContextualizer Optional<Function<? super Runnable, ? extends Runnable>> contextualizer) {
        this.scheduler = scheduler;
        this.contextualizer = contextualizer;
    }
    
    @Override
    public void start() {
        Function<? super Runnable, ? extends Runnable> contextualizer = null;
        if (scheduler instanceof ContextualizerOwner) {
            contextualizer = ((ContextualizerOwner)scheduler).contextualizer();
        }
        if (null == contextualizer) {
            contextualizer = this.contextualizer.orElse(null);
        }
        if (null != contextualizer) {
            @SuppressWarnings("unchecked")
            Function<Runnable, Runnable> elevated = (Function<Runnable, Runnable>) contextualizer;
            Schedulers.onScheduleHook(SCHEDULE_HOOK_NAME, elevated);
        }
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        Schedulers.resetOnScheduleHook(SCHEDULE_HOOK_NAME);        
    }
    
    private static final String SCHEDULE_HOOK_NAME = AsyncAwaitReactorContextualizer.class.getPackage().getName() + ".<<async-await-webflux-contextualizer>>";
}
