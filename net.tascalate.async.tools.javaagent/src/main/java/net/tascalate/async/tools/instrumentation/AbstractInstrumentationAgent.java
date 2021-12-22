/**
 * ﻿Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.tools.instrumentation;

import static org.apache.commons.javaflow.spi.InstrumentationUtils.isClassLoaderParent;
import static org.apache.commons.javaflow.spi.InstrumentationUtils.packageNameOf;
import static org.apache.commons.javaflow.spi.InstrumentationUtils.packagePrefixesOf;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.javaflow.spi.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class AbstractInstrumentationAgent {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    protected AbstractInstrumentationAgent() {
    }
    
    protected void install(String args, Instrumentation instrumentation) throws Exception {
        Collection<ClassFileTransformer> oneTimeTransformers = createOneTimeTransformers(args, instrumentation);
        Collection<ClassFileTransformer> retransformableTransformers = createRetransformableTransformers(args, instrumentation);
        joinTransformers(oneTimeTransformers, retransformableTransformers)
            .forEach(t -> instrumentation.addTransformer(t, retransformableTransformers.contains(t)));
        joinTransformers(oneTimeTransformers, retransformableTransformers)
            .map(o -> o.getClass().getName())
            .forEach(p -> System.setProperty(p, "true"));
    }
    
    private static Stream<ClassFileTransformer> joinTransformers(Collection<ClassFileTransformer> oneTimeTransformers, Collection<ClassFileTransformer> retransformableTransformers) {
        return Stream.of(oneTimeTransformers, retransformableTransformers)
                     .filter(Objects::nonNull)
                     .flatMap(Collection::stream)
                     .distinct();
    }
    
    protected void attach(String args, Instrumentation instrumentation, Set<String> ownPackages) throws Exception {
        log.info("Installing agent...");

        // Collect classes before ever adding transformer!
        Set<String> extendedOwnPackages = null == ownPackages ? new HashSet<>() : new HashSet<>(ownPackages);
        extendedOwnPackages.add(packageNameOf(getClass()) + '.');

        Collection<ClassFileTransformer> oneTimeTransformers = createOneTimeTransformers(args, instrumentation);
        Collection<ClassFileTransformer> retransformableTransformers = createRetransformableTransformers(args, instrumentation);
        joinTransformers(oneTimeTransformers, retransformableTransformers).forEach(t -> {
            instrumentation.addTransformer(t, retransformableTransformers.contains(t));
            extendedOwnPackages.add(packageNameOf(t.getClass()) + '.');
        });
        
        if (isSkipRetransformOptionSet(args)) {
            if (log.isInfoEnabled()) {
                log.info("skipping re-transforming classes according to Java Agent argumentds supplied: " + args);
            }
        } else if (!instrumentation.isRetransformClassesSupported()) {
            log.info("JVM does not support re-transform, skipping re-transforming classes");
        } else if (retransformableTransformers.isEmpty()) {
            log.info("No retransformable transformers registered, skipping re-transforming classes");
        } else {
            retransformClasses(instrumentation, extendedOwnPackages);
        }
        joinTransformers(oneTimeTransformers, retransformableTransformers)
            .map(o -> o.getClass().getName())
            .forEach(p -> System.setProperty(p, "true"));
        log.info("Agent was installed dynamically");
    }
    
    protected void retransformClasses(Instrumentation instrumentation, Set<String> ownPackages) {
        log.info("Re-transforming existing classes...");
        
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        for (Class<?> clazz : instrumentation.getAllLoadedClasses()) {
            String className = clazz.getName();
            if (instrumentation.isModifiableClass(clazz)) {
                if (isClassLoaderParent(systemClassLoader, clazz.getClassLoader())) {
                    if (log.isTraceEnabled()) {
                        log.trace("Skip re-transforming boot or extension/platform class: " + className);
                    }
                    continue;
                }
                
                boolean isOwnClass = false;
                for (String ownPackage : ownPackages) {
                    if (className.startsWith(ownPackage)) {
                        isOwnClass = true;
                        break;
                    }
                }
                
                if (isOwnClass) {
                    if (log.isDebugEnabled()) {
                        log.debug("Skip re-transforming class (agent class): " + className);
                    }
                    continue;
                }
                
                if (log.isDebugEnabled()) {
                    log.debug("Re-transforming class: " + className);
                }
                try {
                    instrumentation.retransformClasses(clazz);
                } catch (Throwable e) {
                    log.error("Error re-transofrming class "+ className, e);
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Non-modifiable class (re-transforming skipped): " + className);
                }                    
            }
        }
        log.info("Existing classes was re-transormed");
    }
    
    protected boolean isSkipRetransformOptionSet(String args) {
        return "skip-retransform".equals(args);
    }
    
    protected Collection<ClassFileTransformer> createOneTimeTransformers(String args, Instrumentation instrumentation) {
        return Collections.emptySet();
    }    
    
    protected Collection<ClassFileTransformer> createRetransformableTransformers(String args, Instrumentation instrumentation) {
        return Collections.singleton(createRetransformableTransformer(args, instrumentation));
    }
    
    protected abstract ClassFileTransformer createRetransformableTransformer(String args, Instrumentation instrumentation);
    
    protected static final Collection<String> BASE_OWN_PACKAGES = Collections.unmodifiableSet(
        packagePrefixesOf(
            Logger.class, 
            ClasspathResourceLoader.class, 
            AbstractInstrumentationAgent.class
        )
    );
}

