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

import org.springframework.core.NamedThreadLocal;

import net.tascalate.async.spi.ThreadVar;
import reactor.util.context.Context;

abstract class WebFluxDataHolder {
    
    protected final ThreadLocal<WebFluxData> threadLocal = new NamedThreadLocal<>("WebFluxData");
    
    abstract WebFluxData current();
    abstract Runnable contextualize(Runnable code);
    abstract Runnable contextualize(Runnable code, boolean needScheduler, boolean needExchange, boolean needContext);
    
    final WebFluxData update(WebFluxData webFluxData) {
        WebFluxData result = threadLocal.get();
        threadLocal.set(webFluxData);
        return result;
    }
    
    final void restore(WebFluxData previous) {
        if (null != previous) {
            threadLocal.set(previous);
        } else {
            threadLocal.remove();
        }
    }
    
    static final class Legacy extends WebFluxDataHolder {
        
        @Override
        WebFluxData current() {
            return threadLocal.get();
        }

        @Override
        Runnable contextualize(Runnable code) {
            WebFluxData current = threadLocal.get();
            if (null == current) {
                return code;
            } 
            
            return () -> {
                WebFluxData previous = update(current);
                try {
                    code.run();
                } finally {
                    restore(previous);
                }
            };   
        }

        @Override
        Runnable contextualize(Runnable code, boolean needScheduler, boolean needExchange, boolean needContext) {
            if (!(needScheduler || needExchange || needContext)) {
                // Nothing required
                return code;
            } else {
                WebFluxData current = threadLocal.get();
                if (null == current) {
                    return code;
                }

                WebFluxData modified = new WebFluxData(
                    needContext   ? current.context() : Context.empty(),
                    needExchange  ? current.serverWebExchange() : null,
                    needScheduler ? current.asyncAwaitScheduler() : null
                ); 
                return () -> {
                    WebFluxData previous = update(modified);
                    try {
                        code.run();
                    } finally {
                        restore(previous);
                    }                
                };
            }
        }
    }
    
    static final class Modern extends WebFluxDataHolder {
        
        // In Java 25+ ThreadVar is backed by ScopedValue
        private final ThreadVar<WebFluxData> threadVar;
        
        Modern(WebFluxData sentinel) {
            threadVar = new ThreadVar<>("WebFluxData", sentinel);
        }
        
        @Override
        WebFluxData current() {
            WebFluxData result = threadVar.value();
            if (null == result) {
                // If this is initial hand-over from WebFlux filter chain 
                result = threadLocal.get();
            }
            return result;
        }

        @Override
        Runnable contextualize(Runnable code) {
            WebFluxData current = current();
            if (null == current) {
                return code;
            } 
            
            return () -> threadVar.runWith(current, code);   
        }

        @Override
        Runnable contextualize(Runnable code, boolean needScheduler, boolean needExchange, boolean needContext) {
            if (!(needScheduler || needExchange || needContext)) {
                // Nothing required
                // For ScopedValue should be bound to EMPTY ???
                return code;
            } else {
                WebFluxData current = current();
                if (null == current) {
                    return code;
                }

                WebFluxData modified = new WebFluxData(
                    needContext   ? current.context() : Context.empty(),
                    needExchange  ? current.serverWebExchange() : null,
                    needScheduler ? current.asyncAwaitScheduler() : null
                ); 
                return () -> threadVar.runWith(modified, code);
            }
        }
    }
    
    static WebFluxDataHolder newInstance(WebFluxData sentinel) {
        String versionProperty = System.getProperty("java.specification.version");
        int version;
        if (versionProperty.startsWith("1.")) {
            version = Integer.parseInt(versionProperty.substring(2));
        } else {
            version = Integer.parseInt(versionProperty);
        }
        return version >= 25 ? new Modern(sentinel) : new Legacy();
    }
}
