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

import java.util.Set;
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
import net.tascalate.async.spring.AsyncAwaitContextItem;
import net.tascalate.async.spring.AsyncExecutionScope;
import net.tascalate.async.spring.DefaultAsyncAwaitContextualizer;

@Lazy
@DefaultAsyncAwaitContextualizer
@Component("<<default-async-await-contextualizer>>")
@ConditionalOnWebApplication(type = Type.SERVLET)
class AsyncAwaitContextualizer implements Function<Runnable, Runnable> {

    @Override
    public Runnable apply(Runnable code) {
        Runnable xcode = code;

        xcode = AsyncExecutionScope.instance().contextualize(xcode);
        
        Scheduler scheduler = SchedulerScope.DEFAULTS.currentScheduler();
        if (null != scheduler) {
            Runnable delegate = xcode;
            xcode = () -> SchedulerScope.DEFAULTS.runWith(scheduler, delegate);
        }
        
        xcode = propagate(xcode, REQUEST_ATTRIBUTES, LOCALE_CONTEXT);
        xcode = SpringSecurityContextualizer.INSTANCE.contextualize(xcode);
        
        return xcode;
    }
    
    static Function<Runnable, Runnable> propagate(Set<AsyncAwaitContextItem> items) {
        if (null == items || items.isEmpty()) {
            return Function.identity();
        }
        return code -> {
            Runnable xcode = code;
            if (items.contains(AsyncAwaitContextItem.ASYNC_SCOPE)) {
                xcode = AsyncExecutionScope.instance().contextualize(xcode);
            }
            
            if (items.contains(AsyncAwaitContextItem.SCHEDULER)) {
                Scheduler scheduler = SchedulerScope.DEFAULTS.currentScheduler();
                if (null != scheduler) {
                    Runnable delegate = xcode;
                    xcode = () -> SchedulerScope.DEFAULTS.runWith(scheduler, delegate);
                }
            }
            
            xcode = propagate(xcode, items.contains(AsyncAwaitContextItem.REQUEST)      ? REQUEST_ATTRIBUTES : null,
                                     items.contains(AsyncAwaitContextItem.MISC_CONTEXT) ? LOCALE_CONTEXT : null);
            
            if (items.contains(AsyncAwaitContextItem.SECURITY_CONTEXT)) {
                xcode = SpringSecurityContextualizer.INSTANCE.contextualize(xcode);
            }
            
            return xcode;
        };
    }
    
    private static Runnable propagate(Runnable code, ContextVar<?> ctxVarA, ContextVar<?> ctxVarB) {
        if (null == ctxVarA) {
            return propagate(code, ctxVarB);
        } else if (null == ctxVarB) {
            return propagate(code, ctxVarA);
        }
        ContextVar<?>.Snapshot snapshotA = ctxVarA.snapshot();
        if (snapshotA.empty()) {
            return propagate(code, ctxVarB);
        } else {
            ContextVar<?>.Snapshot snapshotB = ctxVarB.snapshot();
            if (snapshotB.empty()) {
                return () -> {
                    try (ContextVar<?>.Modification modification = snapshotA.apply()) {
                        code.run();
                    }
                };
            } else {
                return () -> {
                    try (ContextVar<?>.Modification modificationA = snapshotA.apply();
                         ContextVar<?>.Modification modificationB = snapshotB.apply()) {
                        code.run();
                    }                
                };
            }
        }
    }
    
    private static Runnable propagate(Runnable code, ContextVar<?> ctxVar) {
        ContextVar<?>.Snapshot snapshot = ctxVar == null ? ContextVar.EMPTY_SNAPSHOT : ctxVar.snapshot();
        if (snapshot.empty()) {
            return code;
        } else {
            return () -> {
                try (ContextVar<?>.Modification modification = snapshot.apply()) {
                    code.run();
                }
            };
        }
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
