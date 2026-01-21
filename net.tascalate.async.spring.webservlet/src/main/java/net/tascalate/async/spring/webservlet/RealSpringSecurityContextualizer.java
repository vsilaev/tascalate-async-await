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

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

class RealSpringSecurityContextualizer extends SpringSecurityContextualizer {
    
    private RealSpringSecurityContextualizer() {
        
    }

    @Override 
    Runnable contextualize(Runnable code) {
        SecurityContextHolderStrategy strategy = SecurityContextHolder.getContextHolderStrategy();
        if (null != strategy && null != SET_SECURITY_CONTEXT_HOLDER_STRATEGY) {
            DelegatingSecurityContextExecutor executor = new DelegatingSecurityContextExecutor(RUN_IN_PLACE);
            SET_SECURITY_CONTEXT_HOLDER_STRATEGY.accept(executor, strategy);
            return () -> executor.execute(code);
        } else {
            SecurityContext ctx = SecurityContextHolder.getContext();
            if (null != ctx) {
                return DelegatingSecurityContextRunnable.create(code, ctx);
            } else {
                return code;
            }
        }
    }
    
    private static final Executor RUN_IN_PLACE = Runnable::run;
    private static final BiConsumer<DelegatingSecurityContextExecutor, SecurityContextHolderStrategy> SET_SECURITY_CONTEXT_HOLDER_STRATEGY = 
            createOptionalSetter(DelegatingSecurityContextExecutor.class, "setSecurityContextHolderStrategy", SecurityContextHolderStrategy.class);
    
    private static <T, V> BiConsumer<T, V> createOptionalSetter(Class<T> beanClass, String setterName, Class<V> propertyType) {
        
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType setterType = MethodType.methodType(void.class, propertyType);
        MethodType samMethodType = MethodType.methodType(void.class, Object.class, Object.class);
        
        try {
            MethodHandle setterHandle = lookup.findVirtual(beanClass, setterName, setterType);
            MethodType implMethodType = setterHandle.type();

            CallSite site = LambdaMetafactory.metafactory(
                lookup,                                     // Caller lookup object
                "accept",                                   // Name of the SAM (Single Abstract Method)
                MethodType.methodType(BiConsumer.class),    // Invoked type
                samMethodType,                              // SAM signature after type erasure
                setterHandle,                               // Implementation method handle
                implMethodType                              // Actual implementation signature
            );

            try {
                BiConsumer<T, V> setter = (BiConsumer<T, V>) site.getTarget().invokeExact();
                return setter;
            } catch (Throwable ex) {
                throw new RuntimeException(ex);
            }
        } catch (NoSuchMethodException ex) {
            return null;
        } catch (IllegalAccessException | LambdaConversionException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    static final SpringSecurityContextualizer INSTANCE = new RealSpringSecurityContextualizer();

}
