/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.stream.StreamSupport;

public final class ContextualExecutors {
    private ContextualExecutors() {
        
    }
    
    public static void scopedRun(ContextualExecutor ctxExecutor, Runnable code) {
        ContextualExecutor previous = CURRENT_EXECUTOR.get();
        CURRENT_EXECUTOR.set(ctxExecutor);
        try {
            code.run();
        } finally {
            if (null == previous) {
                CURRENT_EXECUTOR.remove();
            } else {
                CURRENT_EXECUTOR.set(previous);
            }
        }
    }
    
    public static ContextualExecutor current(Object owner) {
        ClassLoader contextClassLoader = null;
        ClassLoader serviceClassLoader = null;
        ServiceLoader<ContextualExecutorResolver> serviceLoader = null;
        
        // First try to resolve by instance
        if (null != owner) {
            ClassLoader ownerClassLoader = classLoaderOfClass(owner.getClass());
            contextClassLoader = classLoaderOfContext();
            serviceClassLoader = isParent(ownerClassLoader, contextClassLoader) ? 
                contextClassLoader : ownerClassLoader;
            serviceLoader = getServiceLoader(serviceClassLoader);
            Optional<ContextualExecutor> resolved = StreamSupport
                .stream(serviceLoader.spliterator(), false)
                .map(r -> r.resolveByOwner(owner))
                .filter(Objects::nonNull)
                .findAny();
            if (resolved.isPresent()) {
                return resolved.get();
            }
        }
        
        // Then by thread local
        ContextualExecutor current = CURRENT_EXECUTOR.get();
        if (null != current) {
            return current;
        }
        
        // Finally by context
        if (serviceClassLoader != contextClassLoader || serviceLoader == null) {
            // Need to either get or renew serviceLoader
            if (null == contextClassLoader) {
                serviceClassLoader = classLoaderOfContext();
            } else {
                serviceClassLoader = contextClassLoader;
            }
            serviceLoader = getServiceLoader(serviceClassLoader);
        }
        
        return StreamSupport
            .stream(serviceLoader.spliterator(), false)
            .map(r -> r.resolveByContext())
            .filter(Objects::nonNull)
            .findAny()
            .orElse(SAME_THREAD_EXECUTOR);
    }
    
    private static ServiceLoader<ContextualExecutorResolver> getServiceLoader(ClassLoader classLoader) {
        synchronized (SERVICE_LOADER_BY_CLASS_LOADER) {
            return SERVICE_LOADER_BY_CLASS_LOADER.computeIfAbsent(
                classLoader, 
                cl -> ServiceLoader.load(ContextualExecutorResolver.class, cl)
            );
        }
    }
    
    private static boolean isParent(ClassLoader parent, ClassLoader child) {
        for (ClassLoader cl = child; cl != null; cl = cl.getParent()) {
            if (cl == parent) {
                return true;
            }
        }
        return false;
    }
    
    private static ClassLoader classLoaderOfContext() {
        ClassLoader result = Thread.currentThread().getContextClassLoader();
        return null != result ? result : classLoaderOfClass(ContextualExecutors.class);
    }
    
    private static ClassLoader classLoaderOfClass(Class<?> clazz) {
        ClassLoader result = clazz.getClassLoader();
        if (null == result) {
            result = ClassLoader.getSystemClassLoader();
        }
        return result;
    }
    private
    static final Map<ClassLoader, ServiceLoader<ContextualExecutorResolver>> SERVICE_LOADER_BY_CLASS_LOADER = new WeakHashMap<>();
    private 
    static final ThreadLocal<ContextualExecutor> CURRENT_EXECUTOR = new ThreadLocal<>();
    static final ContextualExecutor SAME_THREAD_EXECUTOR = ContextualExecutor.from(Runnable::run);
    
}
