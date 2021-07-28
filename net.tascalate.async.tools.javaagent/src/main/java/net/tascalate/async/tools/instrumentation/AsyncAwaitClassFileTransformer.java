/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
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

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.javaflow.spi.ClasspathResourceLoader;
import org.apache.commons.javaflow.spi.ExtendedClasspathResourceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tascalate.async.tools.core.AsyncAwaitClassFileGenerator;
import net.tascalate.instrument.emitter.spi.ClassEmitter;
import net.tascalate.instrument.emitter.spi.ClassEmitters;
import net.tascalate.instrument.emitter.spi.PortableClassFileTransformer;

class AsyncAwaitClassFileTransformer extends PortableClassFileTransformer {

    private static final Logger log = LoggerFactory.getLogger(AsyncAwaitClassFileTransformer.class);

    private final ClassFileTransformer postProcessor;

    AsyncAwaitClassFileTransformer(ClassFileTransformer postProcessor) {
        this.postProcessor = postProcessor;
    }

    @Override
    public byte[] transform(ClassEmitters.Factory emitters,
                            Object                module,
                            ClassLoader           originalClassLoader, 
                            String                className, 
                            Class<?>              classBeingRedefined,
                            ProtectionDomain      protectionDomain, 
                            byte[]                classfileBuffer) throws IllegalClassFormatException {

        try {
            if (skipClassByName(className)) {
                return null;
            }

            ClassLoader classLoader = getSafeClassLoader(originalClassLoader);

            AsyncAwaitClassFileGenerator generator = new AsyncAwaitClassFileGenerator(
                new ClasspathResourceLoader(classLoader)
            );
            
            byte[] transformed = generator.transform(classfileBuffer);
            if (null == transformed) {
                return postProcess(module, classLoader, className, classBeingRedefined, protectionDomain, classfileBuffer);
            }

            Map<String, byte[]> extraClasses = generator.getGeneratedClasses();
            generator.reset();

            // Define new classes and then redefine inner classes
            byte[] finalResult = postProcess(module, classLoader, className, classBeingRedefined, protectionDomain, transformed);
            Map<String, byte[]> inMemoryResources = renameInMemoryResources(extraClasses);
            inMemoryResources.put(className + ".class", finalResult);

            ExtendedClasspathResourceLoader.runWithInMemoryResources(new Runnable() {
                @Override
                public void run() {
                    defineGeneratedClasses(emitters, module, classLoader, protectionDomain, extraClasses);
                }
            }, inMemoryResources);
            return finalResult;
        } catch (Error | RuntimeException ex) {
            System.err.println("--->");
            ex.printStackTrace(System.err);
            throw ex;
        }
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

    protected void defineGeneratedClasses(ClassEmitters.Factory emitters,
                                          Object                module,
                                          ClassLoader           classLoader, 
                                          ProtectionDomain      protectionDomain, 
                                          Map<String, byte[]>   generatedClasses) {
        for (Map.Entry<String, byte[]> e : generatedClasses.entrySet()) {
            byte[] bytes;
            try {
                log.info("TRANSOFRMING: " + e.getKey());
                bytes = transform(emitters, module, classLoader, e.getKey(), null, protectionDomain, e.getValue());
                e.setValue(bytes);
                log.info("TRANSOFRMED: " + e.getKey());
            } catch (final IllegalClassFormatException ex) {
                log.error("Unable to generate class bytecode", ex);
                throw new RuntimeException(ex);
            } catch (Error | RuntimeException ex) {
                log.error("Unable to generate class bytecode", ex);
                throw ex;
            }
            if (bytes == null) {
                continue;
            }
            try {
                ClassEmitter emitter = emitters.create(ClassEmitters.packageNameOf(bytes));
                @SuppressWarnings("unused")
                Class<?> ignore = emitter.defineClass(bytes, protectionDomain);
                log.info("DEFINED: " + e.getKey());
            } catch (Throwable ex) {
                log.error("Unable to define generated class", ex);
                throw new RuntimeException(ex);
            }
        }
    }

    static boolean skipClassByName(String className) {
        return null != className && (
               className.startsWith("java/")       || 
               className.startsWith("javax/")      || 
               className.startsWith("sun/")        || 
               className.startsWith("com/sun/")    || 
               className.startsWith("oracle/")     || 
               className.startsWith("com/oracle/") || 
               className.startsWith("ibm/")        || 
               className.startsWith("com/ibm/")
               );
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
