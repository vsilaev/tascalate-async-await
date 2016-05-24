package com.farata.lang.async.instrumentation;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.javaflow.spi.ExtendedClasspathResourceLoader;
import org.apache.commons.javaflow.spi.ClasspathResourceLoader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
				log.debug("TRANSOFRMING: " + e.getKey());
				bytes = this.transform(classLoader, e.getKey(), null, protectionDomain, e.getValue());
				log.debug("TRANSOFRMED: " + e.getKey());
			} catch (final IllegalClassFormatException ex) {
				log.error(ex);
				throw new RuntimeException(ex);
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
