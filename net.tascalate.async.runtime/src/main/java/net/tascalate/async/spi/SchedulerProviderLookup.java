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
package net.tascalate.async.spi;

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

public class SchedulerProviderLookup {
    
    abstract public static class Accessor {
        abstract protected boolean isVisibleTo(Class<?> subClass);
        
        final protected boolean isVisibleTo(Class<?> subClass, Member member) {
            Class<?> declaringClass = member.getDeclaringClass();
            if (!declaringClass.isAssignableFrom(subClass)) {
                return false;
            }
            int modifiers = member.getModifiers();
            if (0 != (modifiers & (Modifier.PUBLIC | Modifier.PROTECTED))) {
                return true;
            } else {
                if (0 != (modifiers & Modifier.PRIVATE)) {
                    return subClass == declaringClass;
                } else {
                    return subClass.getPackage().equals(declaringClass.getPackage());
                }
            }
            
        }
    }
    
    abstract public static class InstanceAccessor extends Accessor {
        abstract protected Object doRead(Object target) throws ReflectiveOperationException;
        final public Scheduler read(Object target) {
            try {
                return (Scheduler)doRead(target);
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
        }        
    }
    
    abstract public static class ClassAccessor extends Accessor {
        abstract protected Object doRead() throws ReflectiveOperationException;
        final public Scheduler read() {
            try {
                return (Scheduler)doRead();
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
        }        
    }
    
    static final class ReadClassField extends ClassAccessor {
        final private Field field;
        
        ReadClassField(Field field) {
            this.field = field;
        }
        
        @Override
        protected final boolean isVisibleTo(Class<?> subClass) {
            return isVisibleTo(subClass, field);
        }
        
        @Override
        protected final Object doRead() throws ReflectiveOperationException {
            return field.get(null);
        }
        
        @Override
        public String toString() {
            return "FIELD: {" + field.toString() + "}";
        }
    }
    
    static final class ReadInstanceField extends InstanceAccessor {
        final private Field field;
        
        ReadInstanceField(Field field) {
            this.field = field;
        }
        
        @Override
        protected final boolean isVisibleTo(Class<?> subClass) {
            return isVisibleTo(subClass, field);
        }
        
        @Override
        protected final Object doRead(Object target) throws ReflectiveOperationException {
            return field.get(target);
        }
        
        @Override
        public String toString() {
            return "FIELD: {" + field.toString() + "}";
        }
    }
    
    static final class InvokeClassGetter extends ClassAccessor {
        final private Method method;
        
        InvokeClassGetter(Method method) {
            this.method = method;
        }
        
        @Override
        protected final boolean isVisibleTo(Class<?> subClass) {
            return isVisibleTo(subClass, method);
        }
        
        @Override
        protected final Object doRead() throws ReflectiveOperationException {
            return method.invoke(null);
        }
        
        @Override
        public String toString() {
            return "METHOD: {" + method.toString() + "}";
        }
    }
    
    static final class InvokeInstanceGetter extends InstanceAccessor {
        final private Method method;
        
        InvokeInstanceGetter(Method method) {
            this.method = method;
        }
        
        @Override
        protected final boolean isVisibleTo(Class<?> subClass) {
            return isVisibleTo(subClass, method);
        }
        
        @Override
        protected final Object doRead(Object target) throws ReflectiveOperationException {
            return method.invoke(target);
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
            Accessor from(Field f) { return new ReadInstanceField(f); }
            
            @Override
            Accessor from(Method m) { return new InvokeInstanceGetter(m); }            
        }, 
        CLASS {
            @Override
            boolean accept(Member m) { return isStatic(m); }
            
            @Override
            Accessor from(Field f) { return new ReadClassField(f); }
            
            @Override
            Accessor from(Method m) { return new InvokeClassGetter(m); }
        };
        
        abstract boolean accept(Member m);
        abstract Accessor from(Field f);
        abstract Accessor from(Method m);
        
        
        protected static boolean isStatic(Member target) {
            return (target.getModifiers() & Modifier.STATIC) != 0;
        }
    }
    
    private final Cache<Class<?>, Accessor> instanceAccessorsCache = new Cache<>();
    private final Cache<Class<?>, Accessor> classAccessorsCache = new Cache<>();
    
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
    
    public InstanceAccessor getInstanceAccessor(Class<?> targetClass) {
        Accessor result = getAccessor(targetClass, instanceAccessorsCache, Kind.INSATNCE, new HashSet<>());
        return (InstanceAccessor)result;
    }
    
    public ClassAccessor getClassAccessor(Class<?> targetClass) {
        Accessor result = getAccessor(targetClass, classAccessorsCache, Kind.CLASS, new HashSet<>());
        return (ClassAccessor)result;
    }

    
    protected Accessor getAccessor(Class<?> targetClass, Cache<Class<?>, Accessor> cache, Kind kind, Set<Class<?>> visitedInterfaces) {
        Accessor result = cache.get(
            targetClass,
            c -> {
                Accessor a = findAccessor(c, cache, kind, visitedInterfaces);
                return a != null ? a : NO_ACCESSOR;
            }
        );
        return result == NO_ACCESSOR ? null : result;
    }
    
    protected Accessor findAccessor(Class<?> targetClass, Cache<Class<?>, Accessor> cache, Kind kind, Set<Class<?>> visitedInterfaces) {
        Accessor accessor = findDeclaredAccessor(targetClass, kind);
        if (null != accessor) {
            return accessor;
        }

        if (inspectSuperclasses) {
            Class<?> superClass = targetClass.getSuperclass();
            if (null != superClass && Object.class != superClass) {
                accessor = getAccessor(superClass, cache, kind, visitedInterfaces);
                if (null != accessor && checkVisibility && !accessor.isVisibleTo(targetClass)) {
                    accessor = null;
                }
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
                .map(i -> getAccessor(i, cache, kind, visitedInterfaces))
                .filter(Objects::nonNull);
            
            if (checkVisibility) {
                accessorsByInterfaces = accessorsByInterfaces.filter(a -> a.isVisibleTo(targetClass));
            }
            
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
    
    protected Accessor findDeclaredAccessor(Class<?> targetClass, Kind kind) {
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
        } else if (fSize == 1) {
            return kind.from(ensureAccessible(ownProviderFields.get(0)));
        } else if (mSize == 1) {
            return kind.from(ensureAccessible(ownProviderMethods.get(0)));
        } else {
            return null;
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
