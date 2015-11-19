package com.farata.lang.async.instrumentation;

import java.lang.instrument.Instrumentation;

import org.apache.commons.javaflow.instrumentation.JavaFlowClassTransformer;

public class AsyncAwaitInstrumentationAgent {
	/**
	 * JVM hook to statically load the javaagent at startup.
	 * 
	 * After the Java Virtual Machine (JVM) has initialized, the premain method
	 * will be called. Then the real application main method will be called.
	 * 
	 * @param args
	 * @param inst
	 * @throws Exception
	 */
	public static void premain(final String args, final Instrumentation instrumentation) throws Exception {
		setupInstrumentation(instrumentation);
	}

	/**
	 * JVM hook to dynamically load javaagent at runtime.
	 * 
	 * The agent class may have an agentmain method for use when the agent is
	 * started after VM startup.
	 * 
	 * @param args
	 * @param inst
	 * @throws Exception
	 */
	public static void agentmain(final String args, final Instrumentation instrumentation) throws Exception {
		setupInstrumentation(instrumentation);
	}

	private static void setupInstrumentation(final Instrumentation instrumentation) {
		instrumentation.addTransformer(
			new AsyncAwaitClassFileTransformer(new JavaFlowClassTransformer()),	true
		);
	}
}
