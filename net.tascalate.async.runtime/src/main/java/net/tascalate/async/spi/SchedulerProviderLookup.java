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
package net.tascalate.async.spi;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.tascalate.async.Scheduler;
import net.tascalate.async.SchedulerProvider;
import net.tascalate.async.util.Cache;
import net.tascalate.async.util.ReferenceType;

public class SchedulerProviderLookup {
    
    abstract public static class Accessor {
        abstract protected boolean isVisibleTo(Class<?> subClass);
        
        final protected static boolean isVisibleTo(Class<?> subClass, Member member) {
            Class<?> declaringClass = member.getDeclaringClass();
            if (!declaringClass.isAssignableFrom(subClass)) {
                return false;
            }
            int modifiers = member.getModifiers();
            if (0 != (modifiers & (Modifier.PUBLIC | Modifier.PROTECTED))) {
                return true;
            } else {
                if (0 != (modifiers & Modifier.PRIVATE)) {
                    // TODO: check nest relations
                    // subClass.isNestmateOf(declaringClass.getNestHost());
                    return subClass == declaringClass;
                } else {
                    return subClass.getPackage().equals(declaringClass.getPackage());
                }
            }
            
        }
    }
    
    abstract public static class InstanceAccessor extends Accessor {
        abstract protected Object doRead(Object target) throws Throwable;
        final public Scheduler read(Object target) {
            try {
                return (Scheduler)doRead(target);
            } catch (Error | RuntimeException ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new RuntimeException(ex);
            }
        }        
    }
    
    abstract public static class ClassAccessor extends Accessor {
        abstract protected Object doRead() throws Throwable;
        final public Scheduler read() {
            try {
                return (Scheduler)doRead();
            } catch (Error | RuntimeException ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new RuntimeException(ex);
            }
        }        
    }
    
    static final class ReadClassField extends ClassAccessor {
        final private Field field;
        final private MethodHandle getter;
        
        ReadClassField(Field field, MethodHandles.Lookup ownerClassLookup) throws IllegalAccessException {
            this.field = field;
            this.getter = ownerClassLookup.unreflectGetter(field);
        }
        
        @Override
        protected final boolean isVisibleTo(Class<?> subClass) {
            return isVisibleTo(subClass, field);
        }
        
        @Override
        protected final Object doRead() throws Throwable {
            return getter.invoke();
        }
        
        @Override
        public String toString() {
            return "FIELD: {" + field.toString() + "}";
        }
    }
    
    static final class ReadInstanceField extends InstanceAccessor {
        final private Field field;
        final private MethodHandle getter;
        
        ReadInstanceField(Field field, MethodHandles.Lookup ownerClassLookup) throws IllegalAccessException {
            this.field = field;
            this.getter = ownerClassLookup.unreflectGetter(field);
        }
        
        @Override
        protected final boolean isVisibleTo(Class<?> subClass) {
            return isVisibleTo(subClass, field);
        }
        
        @Override
        protected final Object doRead(Object target) throws Throwable {
            return getter.invoke(target);
        }
        
        @Override
        public String toString() {
            return "FIELD: {" + field.toString() + "}";
        }
    }
    
    static final class InvokeClassGetter extends ClassAccessor {
        final private Method method;
        final private MethodHandle getter;
        
        InvokeClassGetter(Method method, MethodHandles.Lookup ownerClassLookup) throws IllegalAccessException {
            this.method = method;
            this.getter = ownerClassLookup.unreflect(method);
        }
        
        @Override
        protected final boolean isVisibleTo(Class<?> subClass) {
            return isVisibleTo(subClass, method);
        }
        
        @Override
        protected final Object doRead() throws Throwable {
            return getter.invoke();
        }
        
        @Override
        public String toString() {
            return "METHOD: {" + method.toString() + "}";
        }
    }
    
    static final class InvokeInstanceGetter extends InstanceAccessor {
        final private Method method;
        final private MethodHandle getter;
        
        InvokeInstanceGetter(Method method, MethodHandles.Lookup ownerClassLookup) throws IllegalAccessException {
            this.method = method;
            this.getter = ownerClassLookup.unreflect(method);
        }
        
        @Override
        protected final boolean isVisibleTo(Class<?> subClass) {
            return isVisibleTo(subClass, method);
        }
        
        @Override
        protected final Object doRead(Object target) throws Throwable {
            return getter.invoke(target);
        }
        
        @Override
        public String toString() {
            return "METHOD: {" + method.toString() + "}";
        }        
    }
    
    static final Accessor NO_ACCESSOR = new Accessor() {
        protected boolean isVisibleTo(Class<?> subClass) {
            return false;
        }
    };
    
    enum Kind {
        INSATNCE {
            @Override
            boolean accept(Member m) { return !isStatic(m); }
            
            @Override
            Accessor from(Field f, MethodHandles.Lookup ownerClassLookup) throws IllegalAccessException { 
                return new ReadInstanceField(f, ownerClassLookup); 
            }
            
            @Override
            Accessor from(Method m, MethodHandles.Lookup ownerClassLookup) throws IllegalAccessException { 
                return new InvokeInstanceGetter(m, ownerClassLookup); 
            }            
        }, 
        CLASS {
            @Override
            boolean accept(Member m) { return isStatic(m); }
            
            @Override
            Accessor from(Field f, MethodHandles.Lookup ownerClassLookup) throws IllegalAccessException { 
                return new ReadClassField(f, ownerClassLookup); 
            }
            
            @Override
            Accessor from(Method m, MethodHandles.Lookup ownerClassLookup) throws IllegalAccessException { 
                return new InvokeClassGetter(m, ownerClassLookup); 
            }
        };
        
        abstract boolean accept(Member m);
        abstract Accessor from(Field f, MethodHandles.Lookup ownerClassLookup) throws IllegalAccessException;
        abstract Accessor from(Method m, MethodHandles.Lookup ownerClassLookup) throws IllegalAccessException;
        
        
        protected static boolean isStatic(Member target) {
            return (target.getModifiers() & Modifier.STATIC) != 0;
        }
    }
    
    private final Cache<Class<?>, Accessor> instanceAccessorsCache = new Cache<>(ReferenceType.WEAK, ReferenceType.SOFT);
    private final Cache<Class<?>, Accessor> classAccessorsCache = new Cache<>(ReferenceType.WEAK, ReferenceType.SOFT);
    
    private final boolean inspectSuperclasses;
    private final boolean inspectInterfaces;
    private final boolean checkVisibility;
    private final boolean superClassPriority;
    
    public SchedulerProviderLookup(boolean inspectSuperclasses, boolean inspectInterfaces, boolean checkVisibility, boolean superClassPriority) {
        this.inspectSuperclasses = inspectSuperclasses;
        this.inspectInterfaces   = inspectInterfaces;
        this.checkVisibility     = checkVisibility;   
        this.superClassPriority  = superClassPriority;
    }
    
    public InstanceAccessor getInstanceAccessor(MethodHandles.Lookup ownerClassLookup) {
        Accessor result = getAccessor(ownerClassLookup, ownerClassLookup.lookupClass(), instanceAccessorsCache, Kind.INSATNCE, new HashSet<>());
        return (InstanceAccessor)result;
    }
    
    public ClassAccessor getClassAccessor(MethodHandles.Lookup ownerClassLookup) {
        Accessor result = getAccessor(ownerClassLookup, ownerClassLookup.lookupClass(), classAccessorsCache, Kind.CLASS, new HashSet<>());
        return (ClassAccessor)result;
    }

    
    protected Accessor getAccessor(MethodHandles.Lookup ownerClassLookup, Class<?> targetClass, Cache<Class<?>, Accessor> cache, Kind kind, Set<Class<?>> visitedInterfaces) {
        Accessor result = cache.get(
            targetClass,
            c -> {
                Accessor a = findAccessor(ownerClassLookup, c, cache, kind, visitedInterfaces);
                return a != null ? a : NO_ACCESSOR;
            }
        );
        return result == NO_ACCESSOR ? null : result;
    }
    
    protected Accessor findAccessor(MethodHandles.Lookup ownerClassLookup, Class<?> targetClass, Cache<Class<?>, Accessor> cache, Kind kind, Set<Class<?>> visitedInterfaces) {
        Accessor accessor = findDeclaredAccessor(ownerClassLookup, targetClass, kind);
        if (null != accessor) {
            return accessor;
        }

        if (inspectSuperclasses) {
            Class<?> superClass = targetClass.getSuperclass();
            if (null != superClass && Object.class != superClass) {
                accessor = getAccessor(ownerClassLookup, superClass, cache, kind, visitedInterfaces);
                /*
                if (null != accessor && checkVisibility && !accessor.isVisibleTo(targetClass)) {
                    accessor = null;
                }
                */
            }
        }

        List<Accessor> superAccessors = new ArrayList<>();
        if (accessor != null) {
            if (superClassPriority) {
                return accessor;
            } else {
                superAccessors.add(accessor);
            }
        } 
        
        if (inspectInterfaces) {
            Stream<Accessor> accessorsByInterfaces = Stream.of(targetClass.getInterfaces())
                .filter(i -> !visitedInterfaces.contains(i))
                .peek(i -> visitedInterfaces.add(i))
                .map(i -> getAccessor(ownerClassLookup, i, cache, kind, visitedInterfaces))
                .filter(Objects::nonNull);
            
            /*
            if (checkVisibility) {
                accessorsByInterfaces = accessorsByInterfaces.filter(a -> a.isVisibleTo(targetClass));
            }
            */
            
            List<Accessor> accessors = accessorsByInterfaces
                //.limit(2)
                .collect(Collectors.toList());
            superAccessors.addAll(accessors);
        }
        
        
        switch (superAccessors.size()) {
            case 0: return null;
            case 1: return superAccessors.get(0);
            default:
                throw new IllegalStateException(
                    "Ambiguity: class " + targetClass.getName() + " has more than one " +
                    SchedulerProvider.class.getSimpleName() + " defined by inherited accessors:\n" +
                    superAccessors.toString()
                );                    
        }
    }
    
    protected Accessor findDeclaredAccessor(MethodHandles.Lookup ownerClassLookup, Class<?> targetClass, Kind kind) {
        List<Field> ownProviderFields = Stream.of(targetClass.getDeclaredFields())
            .filter(SchedulerProviderLookup::isSchedulerProviderField)
            .filter(kind::accept)
            //.limit(2)
            .collect(Collectors.toList());
        
        if (ownProviderFields.size() > 1) {
            throw new IllegalStateException(
                "Ambiguity: class " + targetClass.getName() + " declares more than one " +
                SchedulerProvider.class.getSimpleName() + " fields, " +
                "at least the following: " + ownProviderFields
            );
        }
        
        List<Method> ownProviderMethods = Stream.of(targetClass.getDeclaredMethods())
            .filter(SchedulerProviderLookup::isSchedulerProviderGetter)
            .filter(kind::accept)
            //.limit(2)
            .collect(Collectors.toList());
        
        int fSize = ownProviderFields.size();
        int mSize = ownProviderMethods.size(); 
        if (fSize + mSize > 1) {
            throw new IllegalStateException(
                "Ambiguity: class " + targetClass.getName() + " declares more than one " +
                SchedulerProvider.class.getSimpleName() + " getters and/or fields, " +
                "at least the following causes the problem:\n " + 
                "-- fields: "+ ownProviderFields + "\n" +
                "-- getters: " + ownProviderMethods
            );
        }
        try {
            if (fSize == 1) {
                Field f = ownProviderFields.get(0);
                if (!checkVisibility) {
                    return kind.from(ensureAccessible(f), ownerClassLookup);
                } else if (Accessor.isVisibleTo(ownerClassLookup.lookupClass(), f)) {
                    return kind.from(f, ownerClassLookup);
                } else {
                    return null;
                }
            } else if (mSize == 1) {
                Method m = ownProviderMethods.get(0);
                if (!checkVisibility) {
                    return kind.from(ensureAccessible(m), ownerClassLookup);
                } else if (Accessor.isVisibleTo(ownerClassLookup.lookupClass(), m)) {
                    return kind.from(m, ownerClassLookup);
                } else {
                    return null;
                }                
            } else {
                return null;
            }
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    static boolean isSchedulerProviderField(Field f) {
        return
          isSchedulerSubtype(f) &&
          isAnnotatedAsProvider(f);        
    }
    
    static boolean isSchedulerProviderGetter(Method m) {
        return
          hasNoParameters(m) && 
          isNotSynthetic(m) &&            
          isSchedulerSubtype(m) &&
          isAnnotatedAsProvider(m);        
    }
    
    
    private static boolean isNotSynthetic(Method method) {
        return !(method.isBridge() || method.isSynthetic()); 
    }
    
    private static boolean hasNoParameters(Method method) {
        return method.getParameterCount() == 0; 
    }
    
    private static boolean isSchedulerSubtype(Method method) {
        return Scheduler.class.isAssignableFrom( method.getReturnType() ); 
    }
    
    private static boolean isSchedulerSubtype(Field field) {
        return Scheduler.class.isAssignableFrom( field.getType() ); 
    }
    
    private static boolean isAnnotatedAsProvider(AnnotatedElement member) {
        return member.getAnnotation(SchedulerProvider.class) != null; 
    }
    
    private static <M extends Member> M ensureAccessible(M target) {
        if ((target.getModifiers() & Modifier.PUBLIC) == 0 || (target.getDeclaringClass().getModifiers() & Modifier.PUBLIC) == 0) {
            AccessibleObject ao = (AccessibleObject)target;
            ao.setAccessible(true);
        }
        return target;
    }

}
