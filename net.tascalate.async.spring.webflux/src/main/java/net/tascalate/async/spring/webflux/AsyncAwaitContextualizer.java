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

import java.util.Set;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import net.tascalate.async.spring.AsyncAwaitContextItem;
import net.tascalate.async.spring.AsyncExecutionScope;
import net.tascalate.async.spring.DefaultAsyncAwaitContextualizer;

@Lazy
@DefaultAsyncAwaitContextualizer
@Component("<<default-async-await-contextualizer>>")
@ConditionalOnWebApplication(type = Type.REACTIVE)
class AsyncAwaitContextualizer implements Function<Runnable, Runnable> {

    @Override
    public Runnable apply(Runnable code) {
        return contextualize(code);
    }
    
    static Runnable contextualize(Runnable rawCode) {
        return WebFluxData.contextualize(
            AsyncExecutionScope.instance().contextualize(rawCode)
        );
    }
    
    static Function<Runnable, Runnable> propagate(Set<AsyncAwaitContextItem> items) {
        if (null == items || items.isEmpty()) {
            return Function.identity();
        }
        return rawCode -> {
            Runnable code;
            if (items.contains(AsyncAwaitContextItem.ASYNC_SCOPE)) {
                code = AsyncExecutionScope.instance().contextualize(rawCode);
            } else {
                code = rawCode;
            }

            boolean needScheduler = items.contains(AsyncAwaitContextItem.SCHEDULER);
            boolean needExchange  = items.contains(AsyncAwaitContextItem.REQUEST);
            boolean needContext   = items.contains(AsyncAwaitContextItem.SECURITY_CONTEXT) || items.contains(AsyncAwaitContextItem.MISC_CONTEXT);
            return WebFluxData.contextualize(code, needScheduler, needExchange, needContext);
        };
    }

}
