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
package net.tascalate.async.spring.webservlet;

import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import net.tascalate.async.Scheduler;
import net.tascalate.async.resolver.scoped.SchedulerScope;
import net.tascalate.async.spring.DefaultAsyncAwaitContextualizer;

@Lazy
@DefaultAsyncAwaitContextualizer
@Component("<<default-async-await-contextualizer>>")
@ConditionalOnWebApplication(type = Type.SERVLET)
public class AsyncAwaitContextualizer implements Function<Runnable, Runnable> {

    @Override
    public Runnable apply(Runnable rawCode) {
        Scheduler scheduler = SchedulerScope.DEFAULTS.currentScheduler();
        Runnable code = () -> SchedulerScope.DEFAULTS.runWith(scheduler, rawCode);
        
        ContextVar<?>.Snapshot currentRequestAttributes = REQUEST_ATTRIBUTES.snapshot();
        ContextVar<?>.Snapshot currentLocaleContext = LOCALE_CONTEXT.snapshot();
        
        if (currentRequestAttributes.empty() && currentLocaleContext.empty()) {
            SpringSecurityContextualizer.INSTANCE.contextualize(code);
        } 
        
        return SpringSecurityContextualizer.INSTANCE.contextualize(
            new Runnable() {
                @Override
                public void run() {
                    try (ContextVar<?>.Modification changeRequestAttributes = currentRequestAttributes.apply();
                         ContextVar<?>.Modification changeLocaleContext = currentLocaleContext.apply()) {
                        code.run();
                    }
                }
            }
        ); 
    }
    
    private static final ContextVar<RequestAttributes> REQUEST_ATTRIBUTES = new ContextVar<>(
        RequestContextHolder::getRequestAttributes, 
        RequestContextHolder::setRequestAttributes, 
        RequestContextHolder::resetRequestAttributes
    );
    
    private static final ContextVar<LocaleContext> LOCALE_CONTEXT = new ContextVar<>(
        LocaleContextHolder::getLocaleContext, 
        LocaleContextHolder::setLocaleContext, 
        LocaleContextHolder::resetLocaleContext
    );

}
