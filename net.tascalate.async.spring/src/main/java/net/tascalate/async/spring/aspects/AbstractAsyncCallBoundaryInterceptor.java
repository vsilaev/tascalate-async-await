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
package net.tascalate.async.spring.aspects;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import net.tascalate.async.spi.AsyncFinalizer;
import net.tascalate.async.spring.AsyncCallBoundary;
import net.tascalate.async.spring.AsyncExecutionScopeAccess;

public abstract class AbstractAsyncCallBoundaryInterceptor<U, M> {
    
    protected final Class<? extends U> singleAsyncResultType;
    protected final Class<? extends M> multipleAsyncResultsType;
    protected final AsyncFinalizer<U, M> asyncFinalizer;
    
    protected AbstractAsyncCallBoundaryInterceptor(Class<? extends U> singleAsyncResultType, 
                                                   Class<? extends M> multipleAsyncResultsType,
                                                   AsyncFinalizer<U, M> asyncFinalizer) {
        
        this.singleAsyncResultType = singleAsyncResultType;
        this.multipleAsyncResultsType = multipleAsyncResultsType;
        this.asyncFinalizer = asyncFinalizer;
    }
    
    protected U invokeAsyncSingle(ProceedingJoinPoint joinPoint) throws Throwable {
        return invokeAsyncSingle(joinPoint, resolveBoundary(joinPoint));
    }
    
    protected U invokeAsyncSingle(ProceedingJoinPoint joinPoint, AsyncCallBoundary asyncCallBoundary) throws Throwable {
        AsyncCallBoundary.Propagation propagation = asyncCallBoundary.value();
        switch (propagation) {
            case REQUIRES_NEW:
            case REQUIRED:                
            case NESTED:    
                boolean createNewFrame  = propagation == AsyncCallBoundary.Propagation.REQUIRES_NEW;
                boolean inheritOldFrame = propagation == AsyncCallBoundary.Propagation.NESTED;
                return AsyncExecutionScopeAccess.withFrameDestructor(createNewFrame, inheritOldFrame, newFrameDestructor -> {
                    U result = nonNullResult(singleAsyncResultType, joinPoint);
                    if (null != newFrameDestructor) {
                        return asyncFinalizer.finalizeSingle(result, true, newFrameDestructor);
                    }
                    return result;
                });
            case SUPPORTS:
                return nonNullResult(singleAsyncResultType, joinPoint);
            case NOT_SUPPORTED:
                return AsyncExecutionScopeAccess.withoutFrame(__ -> nonNullResult(singleAsyncResultType, joinPoint));                
            case MANDATORY:
                return AsyncExecutionScopeAccess.hasFrame() ? 
                       nonNullResult(singleAsyncResultType, joinPoint) : noAsyncCallBoundary(joinPoint);
            case NEVER:
                return !AsyncExecutionScopeAccess.hasFrame() ?
                        nonNullResult(singleAsyncResultType, joinPoint) : hasAsyncCallBoundary(joinPoint); 
        }
        return unknownAsyncCallBoundaryPropagation(joinPoint, asyncCallBoundary);
    }
    
    protected M invokeAsyncMultiple(ProceedingJoinPoint joinPoint) throws Throwable {
        return invokeAsyncMultiple(joinPoint, resolveBoundary(joinPoint));
    }
    
    protected M invokeAsyncMultiple(ProceedingJoinPoint joinPoint, AsyncCallBoundary asyncCallBoundary) throws Throwable {
        AsyncCallBoundary.Propagation propagation = asyncCallBoundary.value();
        switch (propagation) {
            case REQUIRES_NEW:
            case REQUIRED:               
            case NESTED:    
                boolean createNewFrame  = propagation == AsyncCallBoundary.Propagation.REQUIRES_NEW;
                boolean inheritOldFrame = propagation == AsyncCallBoundary.Propagation.NESTED;
                return AsyncExecutionScopeAccess.withFrameDestructor(createNewFrame, inheritOldFrame, newFrameDestructor -> {
                    M result = nonNullResult(multipleAsyncResultsType, joinPoint);
                    if (null != newFrameDestructor) {
                        boolean cancellationIsError = !asyncCallBoundary.ignoreGeneratorEarlyExit();
                        return asyncFinalizer.finalizeMultiple(result, cancellationIsError, newFrameDestructor);
                    }
                    return result;
                });
            case SUPPORTS:
                return nonNullResult(multipleAsyncResultsType, joinPoint);
            case NOT_SUPPORTED:
                return AsyncExecutionScopeAccess.withoutFrame(__ -> nonNullResult(multipleAsyncResultsType, joinPoint));
            case MANDATORY:
                return AsyncExecutionScopeAccess.hasFrame() ? 
                       nonNullResult(multipleAsyncResultsType, joinPoint) : noAsyncCallBoundary(joinPoint);
            case NEVER:
                return !AsyncExecutionScopeAccess.hasFrame() ?
                        nonNullResult(multipleAsyncResultsType, joinPoint) : hasAsyncCallBoundary(joinPoint); 
        }
        return unknownAsyncCallBoundaryPropagation(joinPoint, asyncCallBoundary);
    }
    
    protected static AsyncCallBoundary resolveBoundary(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // Check method first, fallback to class
        AsyncCallBoundary boundary = method.getAnnotation(AsyncCallBoundary.class);
        if (boundary == null) {
            Class<?> clazz = method.getDeclaringClass(); 
            return clazz.getAnnotation(AsyncCallBoundary.class);
        } else {
            return boundary;
        }
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
