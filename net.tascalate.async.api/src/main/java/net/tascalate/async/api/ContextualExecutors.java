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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class ContextualExecutors {
    private ContextualExecutors() {
        
    }
    
    public static void runWith(ContextualExecutor ctxExecutor, Runnable code) {
        supplyWith(ctxExecutor, () -> {
            code.run();
            return null;
        });
    }
    
    public static <V> V supplyWith(ContextualExecutor ctxExecutor, Supplier<V> code) {
    	try {
            return callWith(ctxExecutor, code::get);
    	} catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Unexpceted checked exception thrown", ex);
        }
    }
    
    
    public static <V> V callWith(ContextualExecutor ctxExecutor, Callable<V> code) throws Exception {
        ContextualExecutor previous = CURRENT_EXECUTOR.get();
        CURRENT_EXECUTOR.set(ctxExecutor);
        try {
            return code.call();
        } finally {
            if (null == previous) {
                CURRENT_EXECUTOR.remove();
            } else {
                CURRENT_EXECUTOR.set(previous);
            }
        }
    }

    public static ContextualExecutor current(Object owner, Class<?> ownerClass) {
    	// Small optimization
    	if (null == owner) {
    	    ContextualExecutor threadLocal = CURRENT_EXECUTOR.get();
    	    if (null != threadLocal) {
    	        return threadLocal;
    	    }
    	}
    	
        ClassLoader serviceClassLoader = getServiceClassLoader(owner != null ? owner.getClass() : ownerClass);
        ServiceLoader<ContextualExecutorResolver> serviceLoader = getServiceLoader(serviceClassLoader);
        
        List<Supplier<? extends Stream<ContextualExecutor>>> resolvers =
            new ArrayList<>(3);
        
        // First try to resolve by instance
        if (null != owner) {
            resolvers.add(() -> 
                StreamSupport
                    .stream(serviceLoader.spliterator(), false)
                    .map(r -> r.resolveByOwner(owner))
            ); 
        }

        // Then by thread local        
        resolvers.add(CURRENT_EXECUTOR_SUPPLIER);
        
        resolvers.add(() -> 
            StreamSupport
                .stream(serviceLoader.spliterator(), false)
                .map(ContextualExecutorResolver::resolveByContext)
        );             

        
        return resolvers
            .stream()
            .flatMap(s -> s.get())
            .filter(Objects::nonNull)
            .findAny()
            .orElse(ContextualExecutor.sameThreadContextless());
    }
    
    private static ClassLoader getServiceClassLoader(Class<?> ownerClassLoaderSource) {
        if (null == ownerClassLoaderSource) {
            ownerClassLoaderSource = ContextualExecutors.class;    	
        }
        ClassLoader ownerClassLoader   = classLoaderOfClass(ownerClassLoaderSource);
        ClassLoader contextClassLoader = classLoaderOfContext();
        return isParent(ownerClassLoader, contextClassLoader) ? 
            contextClassLoader : ownerClassLoader;
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
    
    private 
    static final Supplier<? extends Stream<ContextualExecutor>> CURRENT_EXECUTOR_SUPPLIER = () -> {
    	ContextualExecutor current = CURRENT_EXECUTOR.get();
    	return null == current ? Stream.empty() : Stream.of(current);
    };
    
    static final ContextualExecutor SAME_THREAD_EXECUTOR = ContextualExecutor.from(Runnable::run);
    
}
