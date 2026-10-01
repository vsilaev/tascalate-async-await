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

import io.smallrye.mutiny.subscription.MultiSubscriber;
import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.spi.Memoization;

class MutinyCompatibilityBridge {
    
    @FunctionalInterface
    interface MultiSubscriberFactory {
        MultiSubscriber<?> create(AsyncGenerator.Sink<?> sink, long batchSize);
    }
    
    private static final Function<Class<?>, MethodHandle> REQUEST_MAP = Memoization.weakKeysSoftValues(MutinyCompatibilityBridge::buildSubscriptionRequesterFactory);
    private static final Function<Class<?>, MethodHandle> CANCEL_MAP = Memoization.weakKeysSoftValues(MutinyCompatibilityBridge::buildSubscriptionCancelerFactory);
    
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    
    private static final MultiSubscriberFactory MULTI_SUBSCRIBER_FACTORY;
    
    enum Variant { REACTIVE_STREAMS, FLOW }

    static {
        Variant variant = null;
        // MultiSubscriber extends org.reactivestreams.Subscriber  → RS variant
        // MultiSubscriber extends java.util.concurrent.Flow.Subscriber → Flow variant
        for (Class<?> iface : MultiSubscriber.class.getInterfaces()) {
            String name = iface.getName();
            if ("org.reactivestreams.Subscriber".equals(name)) {
                variant = Variant.REACTIVE_STREAMS;
                break;
            }
            if ("java.util.concurrent.Flow$Subscriber".equals(name)) {
                variant = Variant.FLOW;
                break;
            }
        }
        
        
        ClassLoader classLoader = MutinyCompatibilityBridge.class.getClassLoader();
        
        if (null == variant) {
            // Fallback: probe classpath
            if (classExists("org.reactivestreams.Subscription", classLoader)) {
                variant = Variant.REACTIVE_STREAMS;
            } else if (classExists("java.util.concurrent.Flow$Subscription", classLoader)) {
                variant = Variant.FLOW;
            } else {
                throw new ExceptionInInitializerError("Unnable to detect Mutiny library variant");
            }
        }
        
        String className;
        switch (variant) {
            case REACTIVE_STREAMS:
                className = "net.tascalate.async.mutiny.MutinyLegacyMultiSubscriber";
                break;
            case FLOW:
                className = "net.tascalate.async.mutiny.MutinyModernMultiSubscriber";
                break;
            default:
                throw new IllegalStateException(
                    "Cannot detect Mutiny variant (Reactive Streams vs Flow)");
        }

        try {
            Class<?> clazz = Class.forName(className, true, classLoader);

            MethodHandle ctor = LOOKUP.findConstructor(
                clazz, MethodType.methodType(void.class, AsyncGenerator.Sink.class, long.class)
            );

            MULTI_SUBSCRIBER_FACTORY = buildLambdaFactory(ctor);

        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("Subscriber class not found on classpath: " + className);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError("Cannot resolve constructor for " + className);
        }
    }
    
    @SuppressWarnings("unchecked")
    public static <T> MultiSubscriber<T> createMultiSubsciber(AsyncGenerator.Sink<T> sink, long batchSize) {
        return (MultiSubscriber<T>)MULTI_SUBSCRIBER_FACTORY.create(sink, batchSize);
    }
    
    private MutinyCompatibilityBridge() {}

    static LongConsumer getSubscriptionRequester(Object subscription) {
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
    
    static MethodHandle buildSubscriptionRequesterFactory(Class<?> clazz) {
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

    static Runnable getSubscriptionCanceler(Object subscription) {
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
    
    static MethodHandle buildSubscriptionCancelerFactory(Class<?> clazz) {
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
    
    private static MultiSubscriberFactory buildLambdaFactory(MethodHandle ctor) throws Throwable {
        MethodType samType = MethodType.methodType(
                MultiSubscriber.class, 
                AsyncGenerator.Sink.class, 
                long.class);

        CallSite site = LambdaMetafactory.metafactory(
            LOOKUP,
            "create",                                       // SAM method name
            MethodType.methodType(MultiSubscriberFactory.class), // Factory type
            samType,                                        // Erased SAM signature
            ctor,                                           // Constructor handle
            samType                                         // Instantiated signature (same as erased here)
        );

        return (MultiSubscriberFactory) site.getTarget().invoke();
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

    private static boolean classExists(String name, ClassLoader classLoader) {
        try {
            Class.forName(name, false, classLoader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

}