/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.core;

import java.util.Comparator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import net.tascalate.async.Scheduler;
import net.tascalate.async.spi.SchedulerResolver;
import net.tascalate.async.util.Cache;

class SchedulerResolvers {
    private SchedulerResolvers() {}
    
    static Scheduler currentScheduler(Object owner, Class<?> ownerDeclaringClass) {
        ClassLoader serviceClassLoader = getServiceClassLoader(owner != null ? owner.getClass() : ownerDeclaringClass);
        ServiceLoader<SchedulerResolver> serviceLoader = getServiceLoader(serviceClassLoader);

        return StreamSupport.stream(serviceLoader.spliterator(), false)
            .sorted(SCHEDULER_RESOLVER_BY_PRIORITY)
            .map(l -> l.resolve(owner, ownerDeclaringClass))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(Scheduler.sameThreadContextless())
        ;
    }
    
    private static ClassLoader getServiceClassLoader(Class<?> ownerClassLoaderSource) {
        if (null == ownerClassLoaderSource) {
            ownerClassLoaderSource = SchedulerResolvers.class;     
        }
        ClassLoader ownerClassLoader   = classLoaderOfClass(ownerClassLoaderSource);
        ClassLoader contextClassLoader = classLoaderOfContext();
        return isParent(ownerClassLoader, contextClassLoader) ? 
            contextClassLoader : ownerClassLoader;
    }
    
    private static ServiceLoader<SchedulerResolver> getServiceLoader(ClassLoader classLoader) {
        return SERVICE_LOADER_BY_CLASS_LOADER.get(
            classLoader, 
            cl -> ServiceLoader.load(SchedulerResolver.class, cl)
        );
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
        return null != result ? result : classLoaderOfClass(SchedulerResolvers.class);
    }
    
    private static ClassLoader classLoaderOfClass(Class<?> clazz) {
        ClassLoader result = clazz.getClassLoader();
        if (null == result) {
            result = ClassLoader.getSystemClassLoader();
        }
        return result;
    }
    
    private static final Comparator<SchedulerResolver> SCHEDULER_RESOLVER_BY_PRIORITY = 
        Comparator.comparing(SchedulerResolver::priority).reversed();
    private static final Cache<ClassLoader, ServiceLoader<SchedulerResolver>> SERVICE_LOADER_BY_CLASS_LOADER = 
        new Cache<>();

}
