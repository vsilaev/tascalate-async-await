package net.tascalate.async.spi;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.tascalate.async.api.ContextualExecutor;
import net.tascalate.async.api.ContextualExecutorProvider;

public class ContextualExecutorProviderLookup {
    
    abstract static class Accessor {
        abstract protected ContextualExecutor doRead(Object target) throws ReflectiveOperationException;
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
        
        final public ContextualExecutor read(Object target) {
            try {
                return doRead(target);
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
    
    static class ReadClassField extends Accessor {
        final private Field field;
        
        ReadClassField(Field field) {
            this.field = field;
        }
        
        @Override
        protected boolean isVisibleTo(Class<?> subClass) {
            return isVisibleTo(subClass, field);
        }
        
        @Override
        protected ContextualExecutor doRead(Object target) throws ReflectiveOperationException {
            return (ContextualExecutor)field.get(null);
        }
    }
    
    static class ReadInstanceField extends Accessor {
        final private Field field;
        
        ReadInstanceField(Field field) {
            this.field = field;
        }
        
        @Override
        protected boolean isVisibleTo(Class<?> subClass) {
            return isVisibleTo(subClass, field);
        }
        
        @Override
        protected ContextualExecutor doRead(Object target) throws ReflectiveOperationException {
            return (ContextualExecutor)field.get(target);
        }
    }

    
    static class InvokeInstanceGetter extends Accessor {
        final private Method method;
        
        InvokeInstanceGetter(Method method) {
            this.method = method;
        }
        
        @Override
        protected boolean isVisibleTo(Class<?> subClass) {
            return isVisibleTo(subClass, method);
        }
        
        @Override
        protected ContextualExecutor doRead(Object target) throws ReflectiveOperationException {
            return (ContextualExecutor)method.invoke(target);
        }
    }
    
    static class InvokeClassGetter extends Accessor {
        final private Method method;
        
        InvokeClassGetter(Method method) {
            this.method = method;
        }
        
        @Override
        protected boolean isVisibleTo(Class<?> subClass) {
            return isVisibleTo(subClass, method);
        }
        
        @Override
        protected ContextualExecutor doRead(Object target) throws ReflectiveOperationException {
            return (ContextualExecutor)method.invoke(null);
        }
    }
    
    private final Map<Class<?>, Accessor> perClassCache = new WeakHashMap<>();
    
    private final boolean inspectSuperclasses;
    private final boolean inspectInterfaces;
    private final boolean checkVisibility;
    private final boolean superClassPriority;
    
    public ContextualExecutorProviderLookup(boolean inspectSuperclasses, boolean inspectInterfaces, boolean checkVisibility, boolean superClassPriority) {
        this.inspectSuperclasses = inspectSuperclasses;
        this.inspectInterfaces   = inspectInterfaces;
        this.checkVisibility     = checkVisibility;   
        this.superClassPriority  = superClassPriority;
    }
    
    Accessor getAccessor(Class<?> targetClass) {
        Accessor accessor = getDeclaredAccessor(targetClass);
        if (null != accessor) {
            return accessor;
        }

        if (inspectSuperclasses) {
            Class<?> superClass = targetClass.getSuperclass();
            if (null != superClass && Object.class != superClass) {
                accessor = getAccessor(superClass);
                if (null != accessor && checkVisibility && !accessor.isVisibleTo(targetClass)) {
                    accessor = null;
                }
            }
        }

        List<Accessor> superAccessors;
        if (accessor != null) {
            if (superClassPriority) {
                return accessor;
            } else {
                superAccessors = new ArrayList<>();
                superAccessors.add(accessor);
            }
        }
        
        if (inspectInterfaces) {
            Map<Class<?>, Object> visitedInterfaces = new WeakHashMap<>();
            Stream<Accessor> accessorsByInterfaces = Stream.of(targetClass.getInterfaces())
                .filter(i -> !visitedInterfaces.containsKey(i))
                .peek(i -> visitedInterfaces.put(i, Object.class))
                .map(i -> getAccessor(targetClass))
                .filter(Objects::nonNull);
            
            if (checkVisibility) {
                accessorsByInterfaces = accessorsByInterfaces.filter(a -> a.isVisibleTo(targetClass));
            }
            
            List<Accessor> accessors = accessorsByInterfaces
                //.limit(2)
                .collect(Collectors.toList());
            
            switch (accessors.size()) {
                case 0: return null;
                case 1: return accessors.get(0);
                default:
                    throw new IllegalStateException(
                        "Ambiguity: class " + targetClass.getName() + " has more than one " +
                        ContextualExecutorProvider.class.getSimpleName() + " defined by inheritance"
                    );                    
            }
        }
        return accessor;
    }
    
    Accessor getDeclaredAccessor(Class<?> targetClass) {
        List<Field> ownProviderFields = Stream.of(targetClass.getDeclaredFields())
            .filter(ContextualExecutorProviderLookup::isContextualExecutorSubtype)
            .filter(ContextualExecutorProviderLookup::isAnnotatedAsProvider)
            //.limit(2)
            .collect(Collectors.toList());
        
        if (ownProviderFields.size() > 1) {
            throw new IllegalStateException(
                "Ambiguity: class " + targetClass.getName() + " declares more than one " +
                ContextualExecutorProvider.class.getSimpleName() + " fields, " +
                "at least the following: " + ownProviderFields
            );
        }
        
        List<Method> ownProviderMethods = Stream.of(targetClass.getDeclaredMethods())
            .filter(ContextualExecutorProviderLookup::hasNoParameters)
            .filter(ContextualExecutorProviderLookup::isNotSynthetic)            
            .filter(ContextualExecutorProviderLookup::isContextualExecutorSubtype)
            .filter(ContextualExecutorProviderLookup::isAnnotatedAsProvider)
            //.limit(2)
            .collect(Collectors.toList());
        
        int fSize = ownProviderFields.size();
        int mSize = ownProviderMethods.size(); 
        if (fSize + mSize > 1) {
            throw new IllegalStateException(
                "Ambiguity: class " + targetClass.getName() + " declares more than one " +
                ContextualExecutorProvider.class.getSimpleName() + " getters and/or fields, " +
                "at least the following causes the problem:\n " + 
                "-- fields: "+ ownProviderFields + "\n" +
                "-- getters: " + ownProviderMethods
            );
        } else if (fSize == 1) {
            Field field = ensureAccessible(ownProviderFields.get(0));
            return isStatic(field) ? new ReadClassField(field) : new ReadInstanceField(field);
        } else if (mSize == 1) {
            Method method = ensureAccessible(ownProviderMethods.get(0));
            return isStatic(method) ? new InvokeClassGetter(method) : new InvokeInstanceGetter(method);
        } else {
            return null;
        }
        
    }
    
    
    static boolean isNotSynthetic(Method method) {
        return !(method.isBridge() || method.isSynthetic()); 
    }
    
    static boolean isContextualExecutorSubtype(Method method) {
        return ContextualExecutor.class.isAssignableFrom( method.getReturnType() ); 
    }
    
    static boolean hasNoParameters(Method method) {
        return method.getParameterCount() == 0; 
    }
    
    static boolean isContextualExecutorSubtype(Field field) {
        return ContextualExecutor.class.isAssignableFrom( field.getType() ); 
    }
    
    static boolean isAnnotatedAsProvider(AnnotatedElement member) {
        return member.getAnnotation(ContextualExecutorProvider.class) != null; 
    }
    
    private static <M extends Member> boolean isStatic(M target) {
        return (target.getModifiers() & Modifier.STATIC) != 0;
    }
    
    private static <M extends Member> M ensureAccessible(M target) {
        if ((target.getModifiers() & Modifier.PUBLIC) == 0) {
            AccessibleObject ao = (AccessibleObject)target;
            ao.setAccessible(true);
        }
        return target;
    }

}
