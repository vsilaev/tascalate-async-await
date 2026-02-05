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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import net.tascalate.async.Scheduler;
import net.tascalate.async.spring.DefaultAsyncAwaitScheduler;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component("<<async-await-flux-web-filter>>")
@ConditionalOnWebApplication(type = Type.REACTIVE)
class AsyncAwaitWebFilter implements WebFilter, 
                                     Ordered,
                                     InitializingBean, 
                                     DisposableBean {
    
    private final Scheduler scheduler;
    
    AsyncAwaitWebFilter(@DefaultAsyncAwaitScheduler Scheduler scheduler) {
        this.scheduler = scheduler;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        AtomicReference<WebFluxData> previous = new AtomicReference<>();
        return chain.filter(exchange)
                    /*
                    .doFirst() as an alternative 
                    */
                    .doOnSubscribe(subscription -> {
                        CoreSubscriber<?> actual = (CoreSubscriber<?>)Scannable.from(subscription).scan(Scannable.Attr.ACTUAL);
                        previous.set(WebFluxData.update(actual.currentContext(), exchange, scheduler));
                    })
                    .doFinally(signal -> {
                        WebFluxData.restore(previous.get());
                    });
    }


    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1000;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Schedulers.onScheduleHook(SCHEDULE_HOOK_NAME, SCHEDULE_HOOK);
    }

    @Override
    public void destroy() throws Exception {
        Schedulers.resetOnScheduleHook(SCHEDULE_HOOK_NAME);
    }
    
    private static final String SCHEDULE_HOOK_NAME = AsyncAwaitWebFilter.class.getPackage().getName() + ".<<async-await-webflux-contextualizer>>";
    private static final Function<Runnable, Runnable> SCHEDULE_HOOK = AsyncAwaitContextualizer::contextualize;
}
