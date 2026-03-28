package net.tascalate.async.spring.scheduler;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.springframework.context.ApplicationContext;

import net.tascalate.async.Scheduler;

import net.tascalate.async.spi.Memoization;
import net.tascalate.async.spi.MethodDefinition;
import net.tascalate.async.spi.PerMethodSchedulerResolver;

public class AsyncOnSchedulerResolver extends PerMethodSchedulerResolver.BySingleAnnotation<AsyncOn> {
    
    public static class AsyncOnClassLookup extends ClassLookupByAnnotation<AsyncOn> {
        protected AsyncOnClassLookup() {
            super(AsyncOn.class);
        }
    }
    
    public static class AsyncOnMethodLookup extends MethodLookupByAnnotation<AsyncOn> {
        protected AsyncOnMethodLookup() {
            super(AsyncOn.class);
        }
    }
    
    protected final Function<Class<? extends Annotation>, Scheduler> schedulerBeanByQualifier;
    
    public AsyncOnSchedulerResolver(ApplicationContext ctx) {
        this(ctx, new AsyncOnClassLookup(), new AsyncOnMethodLookup());
    }
    
    protected AsyncOnSchedulerResolver(ApplicationContext ctx, ClassLookupByAnnotation<AsyncOn> classLookup, MethodLookupByAnnotation<AsyncOn> methodLookup) {
        super(classLookup, methodLookup);
        schedulerBeanByQualifier = Memoization.weakHard(qualifier -> getQualifiedBean(ctx, Scheduler.class, qualifier));
    }

    @Override
    public int priority() {
        return 600;
    }

    @Override
    protected Scheduler createClassScheduler(Optional<AsyncOn> annotation, Object owner, MethodHandles.Lookup declaringClassLookup) {
        return createScheduler(annotation.get().value(), owner, declaringClassLookup);
    }

    @Override
    protected Scheduler createMethodScheduler(Optional<AsyncOn> annotation, Object owner, MethodHandles.Lookup declaringClassLookup, MethodDefinition methodDef) {
        return createScheduler(annotation.get().value(), owner, declaringClassLookup);
    }

    protected Scheduler createScheduler(Class<? extends Annotation> qualifier, Object owner, MethodHandles.Lookup declaringClassLookup) {
        return schedulerBeanByQualifier.apply(qualifier);
    }
    
    public static <T, A extends Annotation> T getQualifiedBean(ApplicationContext ctx, Class<? extends T> type, Class<? extends A> qualifier, Object... args) {
        Objects.requireNonNull(ctx, "ApplicationContext");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(qualifier, "qualifier");

        // find candidate bean names for the requested type
        String[] candidatesByType = ctx.getBeanNamesForType(type);
        String[] candidatesByQualifier = ctx.getBeanNamesForAnnotation(qualifier);

        Set<String> matchedNamesX = new HashSet<>(Arrays.asList(candidatesByQualifier));
        matchedNamesX.retainAll(Arrays.asList(candidatesByType));

        List<String> matchedNames = new ArrayList<>(matchedNamesX);
        
        if (matchedNames.isEmpty()) {
            throw new NoSuchElementException("No bean found for type " + type + " and qualifier " + qualifier);
        }
        
        if (matchedNames.size() > 1) {
            throw new IllegalStateException("Multiple beans found for type " + type + " and qualifier " + qualifier + ": " + matchedNames);
        }
        
        String beanName = matchedNames.get(0);
        @SuppressWarnings("unchecked")
        T result = (args == null || args.length == 0) ? 
                   ctx.getBean(beanName, type)
                   :
                   (T)ctx.getBean(beanName, type, args);
        
        return result;
    }
}
