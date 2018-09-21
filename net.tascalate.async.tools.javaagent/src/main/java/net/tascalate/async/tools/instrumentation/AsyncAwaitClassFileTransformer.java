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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.javaflow.spi.ClasspathResourceLoader;
import org.apache.commons.javaflow.spi.ExtendedClasspathResourceLoader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.tascalate.async.tools.core.AsyncAwaitClassFileGenerator;

class AsyncAwaitClassFileTransformer implements ClassFileTransformer {

    private static final Log log = LogFactory.getLog(AsyncAwaitClassFileTransformer.class);

    private final ClassFileTransformer postProcessor;

    AsyncAwaitClassFileTransformer(ClassFileTransformer postProcessor) {
        this.postProcessor = postProcessor;
    }

    public byte[] transform(ClassLoader      originalClassLoader, 
                            String           className, 
                            Class<?>         classBeingRedefined,
                            ProtectionDomain protectionDomain, 
                            byte[]           classfileBuffer) throws IllegalClassFormatException {

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
                return postProcess(classLoader, className, classBeingRedefined, protectionDomain, classfileBuffer);
            }

            Map<String, byte[]> extraClasses = generator.getGeneratedClasses();
            generator.reset();

            // Define new classes and then redefine inner classes
            byte[] finalResult = postProcess(classLoader, className, classBeingRedefined, protectionDomain, transformed);
            Map<String, byte[]> inMemoryResources = renameInMemoryResources(extraClasses);
            inMemoryResources.put(className + ".class", finalResult);

            ExtendedClasspathResourceLoader.runWithInMemoryResources(new Runnable() {
                @Override
                public void run() {
                    defineGeneratedClasses(classLoader, protectionDomain, extraClasses);
                }
            }, inMemoryResources);
            return finalResult;
        } catch (Error | RuntimeException ex) {
            System.err.println("--->");
            ex.printStackTrace(System.err);
            throw ex;
        }
    }

    protected byte[] postProcess(ClassLoader      classLoader, 
                                 String           className,
                                 Class<?>         classBeingRedefined, 
                                 ProtectionDomain protectionDomain, 
                                 byte[]           classfileBuffer)
            throws IllegalClassFormatException {

        if (null == classfileBuffer) {
            return null;
        }
        // Apply continuable annotations
        return postProcessor.transform(classLoader, className, classBeingRedefined, protectionDomain, classfileBuffer);
    }

    protected void defineGeneratedClasses(ClassLoader         classLoader, 
                                          ProtectionDomain    protectionDomain, 
                                          Map<String, byte[]> generatedClasses) {
        for (Map.Entry<String, byte[]> e : generatedClasses.entrySet()) {
            byte[] bytes;
            try {
                log.info("TRANSOFRMING: " + e.getKey());
                bytes = transform(classLoader, e.getKey(), null, protectionDomain, e.getValue());
                e.setValue(bytes);
                log.info("TRANSOFRMED: " + e.getKey());
            } catch (final IllegalClassFormatException ex) {
                log.error(ex);
                throw new RuntimeException(ex);
            } catch (Error | RuntimeException ex) {
                log.error(ex);
                throw ex;
            }
            if (bytes == null) {
                continue;
            }
            try {
                @SuppressWarnings("unused")
                Class<?> ignore = (Class<?>)DEFINE_CLASS.invokeExact(
                    classLoader, (String) null, bytes, 0, bytes.length, protectionDomain
                );
                log.info("DEFINED: " + e.getKey());
            } catch (Throwable ex) {
                log.error(ex);
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

    private static final MethodHandle DEFINE_CLASS;
    static {
        try {
            Method m = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class
            );
            m.setAccessible(true);
            DEFINE_CLASS = MethodHandles.lookup().unreflect(m);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

}
