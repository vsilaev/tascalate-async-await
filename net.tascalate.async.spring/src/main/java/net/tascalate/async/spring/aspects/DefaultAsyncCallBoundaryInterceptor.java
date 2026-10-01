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
package net.tascalate.async.spring.aspects;

import java.util.concurrent.CompletionStage;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.spi.DefaultAsyncFinalizer;

@Aspect
public class DefaultAsyncCallBoundaryInterceptor extends AbstractAsyncCallBoundaryInterceptor<CompletionStage<?>, AsyncGenerator<?>> {

    @SuppressWarnings("unchecked")
    private static final Class<? extends CompletionStage<?>> COMPLETION_STAGE = (Class<? extends CompletionStage<?>>)(Object)CompletionStage.class;
    @SuppressWarnings("unchecked")
    private static final Class<? extends AsyncGenerator<?>> ASYNC_GENERATOR = (Class<? extends AsyncGenerator<?>>)(Object)AsyncGenerator.class;
    
    public DefaultAsyncCallBoundaryInterceptor() {
        super(COMPLETION_STAGE, ASYNC_GENERATOR, DefaultAsyncFinalizer.instance());
    }

    @Pointcut("execution(java.util.concurrent.CompletionStage+ *.*(..))")
    void anyCompletionStageMethod() {}

    @Pointcut("execution(net.tascalate.async.AsyncGenerator+ *.*(..))")
    void anyAsyncGeneratorMethod() {}

    @Pointcut("@within(net.tascalate.async.spring.AsyncCallBoundary) || @annotation(net.tascalate.async.spring.AsyncCallBoundary)")
    void hasBoundaryAnnotation() {}

    @Around("anyCompletionStageMethod() && hasBoundaryAnnotation()")
    public Object doInvokeAsyncTask(ProceedingJoinPoint joinPoint) throws Throwable {
        return invokeAsyncSingle(joinPoint);
    }

    @Around("anyAsyncGeneratorMethod() && hasBoundaryAnnotation()")
    public Object doInvokeAsyncGenerator(ProceedingJoinPoint joinPoint) throws Throwable {
        return invokeAsyncMultiple(joinPoint);
    }
}
