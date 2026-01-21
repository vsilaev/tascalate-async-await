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

import org.springframework.web.server.ServerWebExchange;

import net.tascalate.async.Scheduler;
import reactor.util.context.Context;

class WebFluxData {
    
    private static final ThreadLocal<WebFluxData> CURRENT_DATA_HOLDER = new ThreadLocal<>();
    
    private static final WebFluxData EMPTY = new WebFluxData(new ReadOnlyContext(Context.empty()), null, null);
    
    private final Context context;
    private final ServerWebExchange serverWebExchange;
    private final Scheduler asyncAwaitScheduler;
    
    private WebFluxData(Context context, ServerWebExchange serverWebExchange, Scheduler asyncAwaitScheduler) {
        this.context = new ReadOnlyContext(null == context ? Context.empty() : context);
        this.serverWebExchange = serverWebExchange;
        this.asyncAwaitScheduler = asyncAwaitScheduler;
    }
    
    Context context() {
        return context;
    }
    
    ServerWebExchange serverWebExchange() {
        return serverWebExchange;
    }
    
    Scheduler asyncAwaitScheduler() {
        return asyncAwaitScheduler;
    }
    
    static WebFluxData get() {
        return CURRENT_DATA_HOLDER.get();
    }
    
    static WebFluxData safeGet() {
        WebFluxData result = get();
        return null == result ? EMPTY : result;
    }
    
    static WebFluxData update(Context context, ServerWebExchange serverWebExchange, Scheduler asyncAwaitScheduler) {
        return update(new WebFluxData(context, serverWebExchange, asyncAwaitScheduler));
    }
    
    static WebFluxData update(WebFluxData webFluxDataHolder) {
        WebFluxData result = CURRENT_DATA_HOLDER.get();
        CURRENT_DATA_HOLDER.set(webFluxDataHolder);
        return result;
    }
    
    static void restore(WebFluxData previous) {
        if (null != previous) {
            CURRENT_DATA_HOLDER.set(previous);
        } else {
            CURRENT_DATA_HOLDER.remove();
        }
    }
}
