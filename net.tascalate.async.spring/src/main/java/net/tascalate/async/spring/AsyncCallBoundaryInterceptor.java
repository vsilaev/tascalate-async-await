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
        AsyncCallBoundary.Propagation propagation = asyncCallBoundary.value();
        switch (propagation) {
            case REQUIRES_NEW:
            case REQUIRED:                
            case NESTED:    
                boolean createNewFrame  = propagation == AsyncCallBoundary.Propagation.REQUIRES_NEW;
                boolean inheritOldFrame = propagation == AsyncCallBoundary.Propagation.NESTED;
                return AsyncExecutionScope.instance().withFrame(createNewFrame, inheritOldFrame, newFrame -> {
                    CompletionStage<?> result = nonNullResult(CompletionStage.class, joinPoint);
                    if (null != newFrame) {
                        result.whenComplete((r, e) -> newFrame.destroy());
                    }
                    return result;
                });
            case SUPPORTS:
                return nonNullResult(CompletionStage.class, joinPoint);
            case NOT_SUPPORTED:
                return AsyncExecutionScope.instance().withoutFrame(__ -> nonNullResult(CompletionStage.class, joinPoint));                
            case MANDATORY:
                return AsyncExecutionScope.instance().hasFrame() ? 
                       nonNullResult(CompletionStage.class, joinPoint) : noAsyncCallBoundary(joinPoint);
            case NEVER:
                return !AsyncExecutionScope.instance().hasFrame() ?
                        nonNullResult(CompletionStage.class, joinPoint) : hasAsyncCallBoundary(joinPoint); 
        }
        return unknownAsyncCallBoundaryPropagation(joinPoint, asyncCallBoundary);
    }
    
    @Around("asyncGeneratorMethodsWithBoundary(asyncCallBoundary)")
    AsyncGenerator<?> invokeAsyncGenerator(ProceedingJoinPoint joinPoint, AsyncCallBoundary asyncCallBoundary) throws Throwable {
        AsyncCallBoundary.Propagation propagation = asyncCallBoundary.value();
        switch (propagation) {
            case REQUIRES_NEW:
            case REQUIRED:               
            case NESTED:    
                boolean createNewFrame  = propagation == AsyncCallBoundary.Propagation.REQUIRES_NEW;
                boolean inheritOldFrame = propagation == AsyncCallBoundary.Propagation.NESTED;
                return AsyncExecutionScope.instance().withFrame(createNewFrame, inheritOldFrame, newFrame -> {
                    AsyncGenerator<?> result = nonNullResult(AsyncGenerator.class, joinPoint);
                    if (null != newFrame) {
                        result.onCompletion(e -> newFrame.destroy());
                    }
                    return result;
                });
            case SUPPORTS:
                return nonNullResult(AsyncGenerator.class, joinPoint);
            case NOT_SUPPORTED:
                return AsyncExecutionScope.instance().withoutFrame(__ -> nonNullResult(AsyncGenerator.class, joinPoint));
            case MANDATORY:
                return AsyncExecutionScope.instance().hasFrame() ? 
                       nonNullResult(AsyncGenerator.class, joinPoint) : noAsyncCallBoundary(joinPoint);
            case NEVER:
                return !AsyncExecutionScope.instance().hasFrame() ?
                        nonNullResult(AsyncGenerator.class, joinPoint) : hasAsyncCallBoundary(joinPoint); 
        }
        return unknownAsyncCallBoundaryPropagation(joinPoint, asyncCallBoundary);
    }
    
    private static <T> T nonNullResult(Class<T> resultType, ProceedingJoinPoint joinPoint) throws Throwable {
        T result = resultType.cast(joinPoint.proceed());
        if (null != result) {
            return result;
        } else {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            throw new IllegalStateException("Method with async call boundary returned null result " + signature);
        }
    }

    private static <T> T noAsyncCallBoundary(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        throw new IllegalStateException("No async call boundary exists when invoking method " + signature);
    }
    
    private static <T> T hasAsyncCallBoundary(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        throw new IllegalStateException("Async call boundary exists (IT SHOULD NOT) when invoking method " + signature);
    }
    
    private static <T> T unknownAsyncCallBoundaryPropagation(ProceedingJoinPoint joinPoint, AsyncCallBoundary asyncCallBoundary) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        throw new IllegalArgumentException("Unknown async call boundary propagation for method " + signature + ": " + asyncCallBoundary);
    }
}
