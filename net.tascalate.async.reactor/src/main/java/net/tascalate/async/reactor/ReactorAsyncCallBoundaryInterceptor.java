/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.reactor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import net.tascalate.async.spring.scope.AbstractAsyncCallBoundaryInterceptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Aspect
public class ReactorAsyncCallBoundaryInterceptor extends AbstractAsyncCallBoundaryInterceptor<Mono<?>, Flux<?>> {
    
    @SuppressWarnings("unchecked")
    private static final Class<? extends Mono<?>> MONO = (Class<? extends Mono<?>>)(Object)Mono.class;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Flux<?>> FLUX = (Class<? extends Flux<?>>)(Object)Flux.class;

    public ReactorAsyncCallBoundaryInterceptor() {
        super(MONO, FLUX, new ReactorAsyncFinalizer());
    }

    @Pointcut("execution(reactor.core.publisher.Mono+ *.*(..))")
    void anyMonoMethod() {}

    @Pointcut("execution(reactor.core.publisher.Flux+ *.*(..))")
    void anyFluxMethod() {}

    @Pointcut("@within(net.tascalate.async.spring.scope.AsyncCallBoundary) || @annotation(net.tascalate.async.spring.scope.AsyncCallBoundary)")
    void hasBoundaryAnnotation() {}

    @Around("anyMonoMethod() && hasBoundaryAnnotation()")
    public Object doInvokeMonoTask(ProceedingJoinPoint joinPoint) throws Throwable {
        return invokeAsyncSingle(joinPoint);
    }

    @Around("anyFluxMethod() && hasBoundaryAnnotation()")
    public Object doInvokeFluxGenerator(ProceedingJoinPoint joinPoint) throws Throwable {
        return invokeAsyncMultiple(joinPoint);
    }
}
