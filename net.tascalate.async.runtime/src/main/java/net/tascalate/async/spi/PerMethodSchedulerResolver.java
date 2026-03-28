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

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import net.tascalate.async.Scheduler;
import net.tascalate.async.util.Cache;
import net.tascalate.async.util.ReferenceType;

public abstract class PerMethodSchedulerResolver<C, M> implements SchedulerResolver {
    
    protected abstract static class ClassLookup<T> {
        abstract protected T resolve(MethodHandles.Lookup ownerClassLookup, Class<?> targetClass);
        abstract protected T empty();
        abstract protected boolean isEmpty(T target);
        abstract protected boolean acceptSuperClass(MethodHandles.Lookup ownerClassLookup, Class<?> superClassOrInterface);
    }
    
    protected abstract static class MethodLookup<T> {
        abstract protected T resolve(MethodHandles.Lookup ownerClassLookup, Class<?> targetClass, Method method);
        abstract protected T empty();
        abstract protected boolean isEmpty(T target);
        abstract protected boolean acceptSuperClass(MethodHandles.Lookup ownerClassLookup, Class<?> superClassOrInterface, MethodDefinition methodDef);
    }

    
    static final class Pair<C, M> {
        final C classAttributes;
        final M methodAttributes;
        
        Pair(C classAttributes, M methodAttributes) {
            this.classAttributes = classAttributes;
            this.methodAttributes = methodAttributes;
        }
    }
    
    static final class PerClassAttributes<C, M> {
        final C classAttributes;
        final Cache<MethodDefinition, M> methodAttributes = new Cache<>(ReferenceType.WEAK, ReferenceType.SOFT);
        
        PerClassAttributes(C classAttributes) {
            this.classAttributes = classAttributes;
        }
    }

    private final Cache<Class<?>, PerClassAttributes<C, M>> perClassAttributes = new Cache<>(ReferenceType.WEAK, ReferenceType.SOFT);
    
    protected final ClassLookup<C> lookupByClass;
    protected final MethodLookup<M> lookupByMethod;
    
    protected PerMethodSchedulerResolver(ClassLookup<C> lookupByClass, MethodLookup<M> lookupByMethod) {
        this.lookupByClass = lookupByClass;
        this.lookupByMethod = lookupByMethod;
    }
    
    abstract protected Scheduler createClassScheduler(C attributes, Object owner, MethodHandles.Lookup ownerClassLookup);
    
    abstract protected Scheduler createMethodScheduler(M attributes, Object owner, MethodHandles.Lookup ownerClassLookup, MethodDefinition methodDef);

    @Override
    public Scheduler resolve(Object owner, MethodHandles.Lookup ownerClassLookup, MethodDefinition methodDef) {
        if (null == methodDef) {
            return null;
        }
        
        Class<?> lookupClass = ownerClassLookup.lookupClass();
        Class<?> startingClass;
        if (null != owner && lookupClass.isAssignableFrom(owner.getClass())) {
            startingClass = owner.getClass();
        } else {
            startingClass = lookupClass;
        }
        
        Pair<C, M> pair = findAttributes(ownerClassLookup, startingClass, methodDef);
        if (lookupByMethod.isEmpty(pair.methodAttributes)) {
            if (lookupByClass.isEmpty(pair.classAttributes)) {
                return null;
            } else {
                return createClassScheduler(pair.classAttributes, owner, ownerClassLookup);
            }
        } else {
            return createMethodScheduler(pair.methodAttributes, owner, ownerClassLookup, methodDef);
        }
    }
    
    protected Pair<C, M> findAttributes(MethodHandles.Lookup ownerClassLookup, Class<?> targetClass, MethodDefinition methodDef) {
        PerClassAttributes<C, M> perClass = perClassAttributes.get(targetClass, cls -> {
            C classAttributes = findClassAttributes(ownerClassLookup, cls, new HashSet<>());
            return new PerClassAttributes<>(classAttributes);
        });
        return new Pair<>(
            perClass.classAttributes,
            perClass.methodAttributes.get(methodDef, md -> findMethodAttributes(ownerClassLookup, targetClass, md, new HashSet<>()))
        );
    }
    
    protected C findClassAttributes(MethodHandles.Lookup ownerClassLookup, Class<?> targetClass, Set<Class<?>> visitedClasses) {
        C classAttributes = lookupByClass.resolve(ownerClassLookup, targetClass);
        if (null != classAttributes) {
            return classAttributes;
        } else {
            return
            Stream.concat(Stream.of(targetClass.getSuperclass()), Stream.of(targetClass.getInterfaces()))
                  .filter(Objects::nonNull)
                  .filter(c -> !visitedClasses.contains(c))
                  .filter(c -> lookupByClass.acceptSuperClass(ownerClassLookup, c))
                  .map(c -> findClassAttributes(ownerClassLookup, c,  visitedClasses))
                  .filter(Objects::nonNull)
                  .findFirst()
                  .orElseGet(lookupByClass::empty);            
        }
    }
    
    protected M findMethodAttributes(MethodHandles.Lookup ownerClassLookup, Class<?> targetClass, MethodDefinition methodDef, Set<Class<?>> visitedClasses) {
        try {
            Method m = targetClass.getDeclaredMethod(methodDef.getName(), methodDef.getArgumentTypes());
            if (!isVisibleTo(ownerClassLookup.lookupClass(), m)) {
                return null;
            } else {
                M methodAttrs = lookupByMethod.resolve(ownerClassLookup, targetClass, m);
                if (null != methodAttrs) {
                    return methodAttrs;
                }
                visitedClasses.add(targetClass);
                return
                Stream.concat(Stream.of(targetClass.getSuperclass()), Stream.of(targetClass.getInterfaces()))
                      .filter(Objects::nonNull)
                      .filter(c -> !visitedClasses.contains(c))
                      .filter(c -> lookupByMethod.acceptSuperClass(ownerClassLookup, c, methodDef))
                      .map(c -> findMethodAttributes(ownerClassLookup, c, methodDef, visitedClasses))
                      .filter(Objects::nonNull)
                      .findFirst()
                      .orElseGet(lookupByMethod::empty);
                
            }
        } catch (NoSuchMethodException | SecurityException e) {
            return lookupByMethod.empty();
        }
        
    }
    
    protected static boolean isVisibleTo(Class<?> subClass, Member member) {
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
    
    public static class ClassLookupByAnnotation<A extends Annotation> extends ClassLookup<Optional<A>> {
        protected final Class<A> annotationClass;
        protected ClassLookupByAnnotation(Class<A> annotationClass) {
            this.annotationClass = annotationClass;
        }
        
        @Override
        protected Optional<A> empty() {
            return Optional.empty();
        }

        @Override
        protected boolean isEmpty(Optional<A> target) {
            return !target.isPresent();
        }

        @Override
        protected boolean acceptSuperClass(MethodHandles.Lookup ownerClassLookup, Class<?> superClassOrInterface) {
            return true;
        }

        @Override
        protected Optional<A> resolve(MethodHandles.Lookup ownerClassLookup, Class<?> targetClass) {
            return Optional.ofNullable(null == targetClass ? null : targetClass.getAnnotation(annotationClass));
        }
    }
    
    public static class MethodLookupByAnnotation<A extends Annotation> extends MethodLookup<Optional<A>> {
        protected final Class<A> annotationClass;
        protected MethodLookupByAnnotation(Class<A> annotationClass) {
            this.annotationClass = annotationClass;
        }
        
        @Override
        protected Optional<A> empty() {
            return Optional.empty();
        }

        @Override
        protected boolean isEmpty(Optional<A> target) {
            return !target.isPresent();
        }

        @Override
        protected boolean acceptSuperClass(MethodHandles.Lookup ownerClassLookup, Class<?> superClassOrInterface, MethodDefinition methodDef) {
            return true;
        }

        @Override
        protected Optional<A> resolve(MethodHandles.Lookup ownerClassLookup, Class<?> targetClass, Method method) {
            return Optional.ofNullable(null == method ? null : method.getAnnotation(annotationClass));
        }
    }
    
    public abstract static class ByAnnotations<CA extends Annotation, MA extends Annotation> extends PerMethodSchedulerResolver<Optional<CA>, Optional<MA>> {

        protected ByAnnotations(ClassLookup<Optional<CA>> lookupByClass, MethodLookup<Optional<MA>> lookupByMethod) {
            super(lookupByClass, lookupByMethod);
        }
        
    }
    
    public abstract static class BySingleAnnotation<A extends Annotation> extends ByAnnotations<A, A> {

        protected BySingleAnnotation(ClassLookup<Optional<A>> lookupByClass, MethodLookup<Optional<A>> lookupByMethod) {
            super(lookupByClass, lookupByMethod);
        }
    }
}
