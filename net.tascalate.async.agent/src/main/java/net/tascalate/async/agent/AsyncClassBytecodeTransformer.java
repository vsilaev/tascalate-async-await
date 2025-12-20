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
package net.tascalate.async.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.javaflow.spi.ClasspathResourceLoader;
import org.apache.commons.javaflow.spi.ExtendedClasspathResourceLoader;
import org.apache.commons.javaflow.spi.InstrumentationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tascalate.async.tools.core.AsyncAwaitClassFileGenerator;
import net.tascalate.instrument.emitter.spi.ClassEmitter;
import net.tascalate.instrument.emitter.spi.PortableClassFileTransformer;

public class AsyncClassBytecodeTransformer extends PortableClassFileTransformer {

    private static final Logger log = LoggerFactory.getLogger(AsyncClassBytecodeTransformer.class);
    
    private final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
    private final ClassFileTransformer postProcessor;

    protected AsyncClassBytecodeTransformer(ClassFileTransformer postProcessor, Instrumentation instrumentation) {
        super(instrumentation);
        this.postProcessor = postProcessor;
    }

    @Override
    protected byte[] transform(ClassEmitterFactory emitterFactory,
                               Object              module,
                               ClassLoader         originalClassLoader, 
                               String              className, 
                               Class<?>            classBeingRedefined,
                               ProtectionDomain    protectionDomain, 
                               byte[]              classfileBuffer) throws IllegalClassFormatException {

        if (isSystemClassLoaderParent(originalClassLoader)) {
            if (log.isDebugEnabled()) {
                log.debug("Ignoring class defined by boot or extensions/platform class loader: " + className);
            }
            return null;
        }

        ClassLoader classLoader = getSafeClassLoader(originalClassLoader);

        AsyncAwaitClassFileGenerator generator = new AsyncAwaitClassFileGenerator(
            new ClasspathResourceLoader(classLoader)
        );
              
        byte[] finalResult;
        Map<String, byte[]> extraClasses;
        try {
            byte[] transformed = generator.transform(classfileBuffer);
            if (null == transformed) {
                return postProcess(module, classLoader, className, classBeingRedefined, protectionDomain, classfileBuffer);
            }

            extraClasses = generator.getGeneratedClasses();
            generator.reset();

            // Define new classes and then redefine inner classes
            finalResult = postProcess(module, classLoader, className, classBeingRedefined, protectionDomain, transformed);
        } catch (IllegalClassFormatException | Error | RuntimeException ex) {
            log.error("Error transforming class " + className, ex);
            throw ex;
        }
        
        if (!extraClasses.isEmpty()) {
            Map<String, byte[]> inMemoryResources = renameInMemoryResources(extraClasses);
            inMemoryResources.put(className + ".class", finalResult);

            ExtendedClasspathResourceLoader.runWithInMemoryResources(
                () -> defineGeneratedClasses(emitterFactory, module, classLoader, protectionDomain, extraClasses),
                inMemoryResources
            );
        }
        return finalResult;
    
    }

    protected byte[] postProcess(Object           module,
                                 ClassLoader      classLoader, 
                                 String           className,
                                 Class<?>         classBeingRedefined, 
                                 ProtectionDomain protectionDomain, 
                                 byte[]           classfileBuffer)
            throws IllegalClassFormatException {

        if (null == classfileBuffer) {
            return null;
        }
        // Apply continuable annotations
        return callTransformer(postProcessor, module, classLoader, className, classBeingRedefined, protectionDomain, classfileBuffer);
    }

    protected void defineGeneratedClasses(ClassEmitterFactory emitterFactory,
                                          Object              module,
                                          ClassLoader         classLoader, 
                                          ProtectionDomain    protectionDomain, 
                                          Map<String, byte[]> generatedClasses) {
        if (generatedClasses.isEmpty()) {
            return;
        }
        ClassEmitter emitter;
        try {
            emitter = emitterFactory.create(true);
        } catch (Error | RuntimeException ex) {
            log.error("Unable to create class bytecode emitter", ex);
            throw ex;            
        }
        
        // Nested via memento of the resolved emitter 
        ClassEmitterFactory nestedEmitterFactory = __ -> emitter;

        for (Map.Entry<String, byte[]> e : generatedClasses.entrySet()) {
            byte[] bytes;
            String newClassName = e.getKey();
            try {
                if (log.isDebugEnabled()) {
                    log.debug("TRANSOFRMING: " + newClassName);
                }
                bytes = transform(nestedEmitterFactory, module, classLoader, e.getKey(), null, protectionDomain, e.getValue());
                e.setValue(bytes);
                if (log.isDebugEnabled()) {
                    log.debug("TRANSOFRMED: " + newClassName);
                }
            } catch (IllegalClassFormatException ex) {
                log.error("Unable to generate class bytecode for " + newClassName, ex);
                throw new RuntimeException(ex);
            } catch (Error | RuntimeException ex) {
                log.error("Unable to generate class bytecode for " + newClassName, ex);
                throw ex;
            }
            if (bytes == null) {
                continue;
            }
            try {
                if (log.isDebugEnabled()) {
                    log.debug("DEFINING: " + newClassName);
                }
                @SuppressWarnings("unused")
                Class<?> ignore = emitter.defineClass(bytes, protectionDomain);
                if (log.isDebugEnabled()) {
                    log.debug("DEFINED: " + newClassName);
                }
            } catch (Throwable ex) {
                log.error("Unable to define generated class for " + newClassName, ex);
                throw new RuntimeException(ex);
            }
        }
    }
    
    private boolean isSystemClassLoaderParent(ClassLoader maybeParent) {
        return InstrumentationUtils.isClassLoaderParent(systemClassLoader, maybeParent);
    }

    private static ClassLoader getSafeClassLoader(ClassLoader classLoader) {
        return null != classLoader ? classLoader : ClassLoader.getSystemClassLoader();
    }

    private static Map<String, byte[]> renameInMemoryResources(Map<String, byte[]> generatedClasses) {
        Map<String, byte[]> resources = new HashMap<String, byte[]>();
        for (Map.Entry<String, byte[]> e : generatedClasses.entrySet()) {
            resources.put(e.getKey() + ".class", e.getValue());
        }
        return resources;
    }
}
