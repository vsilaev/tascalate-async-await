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
package net.tascalate.async.mutiny;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongConsumer;

import net.tascalate.async.spi.Memoization;

class SubscriptionBridgeCache {
    private static final Function<Class<?>, MethodHandle> REQUEST_MAP = Memoization.weakKeysSoftValues(SubscriptionBridgeCache::buildRequesterFactory);
    private static final Function<Class<?>, MethodHandle> CANCEL_MAP = Memoization.weakKeysSoftValues(SubscriptionBridgeCache::buildCancelerFactory);
    
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    
    private SubscriptionBridgeCache() {}

    static LongConsumer getRequester(Object subscription) {
        if (subscription == null) return demand -> {};
        
        MethodHandle factory = REQUEST_MAP.apply(subscription.getClass()); 
        try {
            return ((LongConsumer) factory.invoke(subscription));
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new RuntimeException("Failed invoking request construction via LambdaMetafactory", t);
        }
    }
    
    static MethodHandle buildRequesterFactory(Class<?> clazz) {
        try {
            Method method = findPublicMethod(clazz, new HashSet<>(), "request", long.class);
            MethodHandle target = LOOKUP.unreflect(method);
            
            // Map it dynamically to a java.util.function.LongConsumer, binding the runtime instance on invocation
            CallSite site = LambdaMetafactory.metafactory(
                LOOKUP,
                "accept",
                MethodType.methodType(LongConsumer.class, clazz), // Takes instance, returns functional interface
                MethodType.methodType(void.class, long.class),    // Erasure of LongConsumer
                target,                                           // Concrete target method
                MethodType.methodType(void.class, long.class)     // Specialized type
            );
            
            // Create a factory handle that takes the subscription and binds it
            return site.getTarget();
        } catch (LambdaConversionException | ReflectiveOperationException ex) {
            throw new RuntimeException("Failed building request lambda proxy for " + clazz.getName(), ex);
        }
    }

    static Runnable getCanceler(Object subscription) {
        if (subscription == null) return () -> {};
        
        MethodHandle factory = CANCEL_MAP.apply(subscription.getClass());
        try {
            return ((Runnable) factory.invoke(subscription));
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable t) {
            throw new RuntimeException("Failed invoking canceler construction via LambdaMetafactory", t);
        }
    }
    
    static MethodHandle buildCancelerFactory(Class<?> clazz) {
        try {
            Method method =  findPublicMethod(clazz, new HashSet<>(), "cancel");
            MethodHandle target = LOOKUP.unreflect(method);
            
            // Map it dynamically to a java.lang.Runnable
            CallSite site = LambdaMetafactory.metafactory(
                LOOKUP,
                "run",
                MethodType.methodType(Runnable.class, clazz),
                MethodType.methodType(void.class),
                target,
                MethodType.methodType(void.class)
            );
            
            return site.getTarget();
        } catch (LambdaConversionException | ReflectiveOperationException ex) {
            throw new RuntimeException("Failed building cancel lambda proxy for " + clazz.getName(), ex);
        }
    }
    
    private static Method findPublicMethod(Class<?> clazz, Set<Class<?>> visited, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        int depth = visited.size();
        if (clazz == null || !visited.add(clazz)) {
            return null; // Already visited or top of hierarchy
        }

        // If this class/interface is public, try to find the method here
        if (java.lang.reflect.Modifier.isPublic(clazz.getModifiers())) {
            try {
                return clazz.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                // Not found here, continue searching
            }
        }

        // Search superclass
        Method method = findPublicMethod(clazz.getSuperclass(), visited, name, paramTypes);
        if (method != null) {
            return method;
        }

        // Search interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            method = findPublicMethod(iface, visited, name, paramTypes);
            if (method != null) {
                return method;
            }
        }

        if (depth == 0) {
            return clazz.getMethod(name, paramTypes);
        } else {
            return null;
        }
    }

}