package net.tascalate.async.core;

import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.stream.StreamSupport;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.async.spi.ContextualExecutorResolver;

class ContextualExecutorResolvers {
    private ContextualExecutorResolvers() {}
    
    static ContextualExecutor currentContextualExecutor(Object owner, Class<?> ownerDeclaringClass) {
        ClassLoader serviceClassLoader = getServiceClassLoader(owner != null ? owner.getClass() : ownerDeclaringClass);
        ServiceLoader<ContextualExecutorResolver> serviceLoader = getServiceLoader(serviceClassLoader);

        return StreamSupport.stream(serviceLoader.spliterator(), false)
            .sorted()
            .map(l -> l.resolve(owner, ownerDeclaringClass))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(ContextualExecutor.sameThreadContextless())
        ;
    }
    
    private static ClassLoader getServiceClassLoader(Class<?> ownerClassLoaderSource) {
        if (null == ownerClassLoaderSource) {
            ownerClassLoaderSource = ContextualExecutorResolvers.class;     
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
        return null != result ? result : classLoaderOfClass(ContextualExecutorResolvers.class);
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

}
