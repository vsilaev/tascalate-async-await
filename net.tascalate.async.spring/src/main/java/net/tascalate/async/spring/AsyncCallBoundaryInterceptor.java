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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import net.tascalate.async.AsyncGenerator;

@Aspect
class AsyncCallBoundaryInterceptor {

    AsyncCallBoundaryInterceptor() {
    }

    @Pointcut("execution(java.util.concurrent.CompletionStage+ *.*(..)) && @within(asyncCallBoundary) && !@annotation(net.tascalate.async.spring.AsyncCallBoundary)")
    void asyncTasksMethodsWithBoundary(AsyncCallBoundary asyncCallBoundary) {}
    
    @Pointcut("execution(net.tascalate.async.AsyncGenerator+ *.*(..)) && @within(asyncCallBoundary) && !@annotation(net.tascalate.async.spring.AsyncCallBoundary)")
    void asyncGeneratorMethodsWithBoundary(AsyncCallBoundary asyncCallBoundary) {}
    
    
    @Around("@annotation(asyncCallBoundary)")
    Object invokeAnyAsyncMethodWithExplicitBoundary(ProceedingJoinPoint joinPoint, AsyncCallBoundary asyncCallBoundary) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> returnType = signature.getReturnType();
        if (CompletionStage.class.isAssignableFrom(returnType)) {
            return invokeAsyncTask(joinPoint, asyncCallBoundary);
        } else if (AsyncGenerator.class.isAssignableFrom(returnType)) {
            return invokeAsyncGenerator(joinPoint, asyncCallBoundary);
        } else {
            throw new IllegalStateException(AsyncCallBoundary.class.getName() + " annotation is not supported for methods with return type " + returnType);
        }
    }
    
    @Around("asyncTasksMethodsWithBoundary(asyncCallBoundary)")
    CompletionStage<?> invokeAsyncTask(ProceedingJoinPoint joinPoint, AsyncCallBoundary asyncCallBoundary) throws Throwable {
        switch (asyncCallBoundary.value()) {
            case CREATE_NEW:
                return AsyncExecutionScope.instance().withNewFrame(frame -> {
                    CompletionStage<?> result = (CompletionStage<?>)joinPoint.proceed();
                    if (null == result) {
                        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                        throw new IllegalStateException("Method with async call boundary returned null result " + signature);
                    } else {
                        result.whenComplete((r, e) -> destroyFrame(frame));
                        return result;
                    }
                });
            case JOIN_OR_CREATE:
                Map<String, AsyncExecutionScope.ScopedObject> frame = AsyncExecutionScope.instance().createOrGetFrame();
                CompletionStage<?> result = (CompletionStage<?>)joinPoint.proceed();
                if (null == result) {
                    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                    throw new IllegalStateException("Method with async call boundary returned null result " + signature);
                } else {
                    if (null != frame) {
                        result.whenComplete((r, e) -> destroyFrame(frame));
                    }
                    return result;
                }
            case JOIN_REQUIRED:
                if (AsyncExecutionScope.instance().hasFrame()) {
                    return (CompletionStage<?>)joinPoint.proceed();
                } else {
                    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                    throw new IllegalStateException("No async call boundary exists when invoking method " + signature);
                }
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        throw new IllegalArgumentException("Unknown async call boundary kind for method " + signature + ": " + asyncCallBoundary);
    }
    
    @Around("asyncGeneratorMethodsWithBoundary(asyncCallBoundary)")
    AsyncGenerator<?> invokeAsyncGenerator(ProceedingJoinPoint joinPoint, AsyncCallBoundary asyncCallBoundary) throws Throwable {
        switch (asyncCallBoundary.value()) {
            case CREATE_NEW:
                return AsyncExecutionScope.instance().withNewFrame(frame -> {
                    AsyncGenerator<?> result = (AsyncGenerator<?>)joinPoint.proceed();
                    if (null == result) {
                        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                        throw new IllegalStateException("Method with async call boundary returned null result " + signature);
                    } else {
                        result.onCompletion(e -> destroyFrame(frame));
                        return result;
                    }
                });
            case JOIN_OR_CREATE:
                Map<String, AsyncExecutionScope.ScopedObject> frame = AsyncExecutionScope.instance().createOrGetFrame();
                AsyncGenerator<?> result = (AsyncGenerator<?>)joinPoint.proceed();
                if (null == result) {
                    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                    throw new IllegalStateException("Method with async call boundary returned null result " + signature);
                } else {
                    if (null != frame) {
                        result.onCompletion(e -> destroyFrame(frame));
                    }
                    return result;
                }
            case JOIN_REQUIRED:
                if (AsyncExecutionScope.instance().hasFrame()) {
                    return (AsyncGenerator<?>)joinPoint.proceed();
                } else {
                    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                    throw new IllegalStateException("No async call boundary exists when invoking method " + signature);
                }
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        throw new IllegalArgumentException("Unknown async call boundary kind for method " + signature + ": " + asyncCallBoundary);
    }
    
    void destroyFrame(Map<String, AsyncExecutionScope.ScopedObject> frame) {
        Map<String, AsyncExecutionScope.ScopedObject> copy = new HashMap<>(frame);
        frame.clear();
        for (AsyncExecutionScope.ScopedObject scopedObject : copy.values()) {
            try {
                scopedObject.destroy();
            } finally {
                
            }
        }
    }
}
