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

public class AsyncAwaitClassFileTransformer implements ClassFileTransformer {
	
	final private static Log log = LogFactory.getLog(AsyncAwaitClassFileTransformer.class);
	
	final private ClassFileTransformer postProcessor;
	
	public AsyncAwaitClassFileTransformer(final ClassFileTransformer postProcessor) {
		this.postProcessor = postProcessor;
	}
	
	public byte[] transform(ClassLoader originalClassLoader, final String className, 
			                final Class<?> classBeingRedefined, 
			                final ProtectionDomain protectionDomain, 
			                final byte[] classfileBuffer) throws IllegalClassFormatException {
	    try{		
    		final ClassLoader classLoader = getSafeClassLoader(originalClassLoader);
    				
    		final AsyncAwaitClassFileGenerator generator = new AsyncAwaitClassFileGenerator();
    		final byte[] transformed = generator.transform(className, classfileBuffer, new ClasspathResourceLoader(classLoader));
    		if (null == transformed) {
    			return postProcess(classLoader, className, classBeingRedefined, protectionDomain, classfileBuffer);	
    		}
    		
    		final Map<String, byte[]> extraClasses = generator.getGeneratedClasses(new ClasspathResourceLoader(classLoader));
    		generator.reset(); 
    		
    		// Define new classes and then redefine inner classes
    		final byte[] finalResult = postProcess(classLoader, className, classBeingRedefined, protectionDomain, transformed);
    		final Map<String, byte[]> inMemoryResources = renameInMemoryResources(extraClasses);
    		inMemoryResources.put(className + ".class", finalResult);
    		
    		ExtendedClasspathResourceLoader.runWithInMemoryResources(
    			new Runnable() {
    				@Override
    				public void run() {
    					defineGeneratedClasses(classLoader, protectionDomain, extraClasses);
    				}
    			}, 
    			inMemoryResources
    		);
    		return finalResult;
        } catch (Error | RuntimeException ex) {
            System.err.println("--->");
            ex.printStackTrace(System.err);
            throw ex;
        }
	}
	
	protected byte[] postProcess(final ClassLoader classLoader, final String className, 
			                     final Class<?> classBeingRedefined, 
			                     final ProtectionDomain protectionDomain, 
			                     final byte[] classfileBuffer) throws IllegalClassFormatException {
		
		if (null == classfileBuffer) {
			return null;
		}
		// Apply continuable annotations
		return postProcessor.transform(
			classLoader, className, classBeingRedefined, protectionDomain, classfileBuffer
		);
	}
	
	protected void defineGeneratedClasses(final ClassLoader classLoader, 
	                                      final ProtectionDomain protectionDomain, 
	                                      final Map<String, byte[]> generatedClasses) {
		
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
				final Class<?> ignore = (Class<?>)DEFINE_CLASS.invokeExact(
					classLoader, (String)null, bytes, 0, bytes.length, protectionDomain
				);
				log.info("DEFINED: " + e.getKey());
			} catch (Throwable ex) {
				log.error(ex);
				throw new RuntimeException(ex);
			}
		}
	}
	
	private static ClassLoader getSafeClassLoader(final ClassLoader classLoader) {
		return null != classLoader ? classLoader : ClassLoader.getSystemClassLoader(); 
	}

	private static Map<String, byte[]> renameInMemoryResources(final Map<String, byte[]> generatedClasses) {
		final Map<String, byte[]> resources = new HashMap<String, byte[]>();
		for (Map.Entry<String, byte[]> e : generatedClasses.entrySet()) {
			resources.put(e.getKey() + ".class", e.getValue());
		}
		return resources;
	}

	
	final private static MethodHandle DEFINE_CLASS;
	static {
		try {
			final Method m = ClassLoader.class.getDeclaredMethod(
				"defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class
			);
			m.setAccessible(true);
			DEFINE_CLASS = MethodHandles.lookup().unreflect(m);
		} catch (final NoSuchMethodException | IllegalAccessException ex) {
			throw new RuntimeException(ex);
		}
	}
	

}
