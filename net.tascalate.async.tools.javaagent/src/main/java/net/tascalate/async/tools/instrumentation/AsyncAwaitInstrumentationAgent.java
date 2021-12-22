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

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.javaflow.instrumentation.JavaFlowClassTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tascalate.instrument.emitter.spi.ClassEmitters;

public class AsyncAwaitInstrumentationAgent extends AbstractInstrumentationAgent {
    private static final Logger log = LoggerFactory.getLogger(AsyncAwaitClassFileTransformer.class);
    
    private ClassFileTransformer continuationsTransformer = new JavaFlowClassTransformer();
    
    /**
     * JVM hook to statically load the javaagent at startup.
     * 
     * After the Java Virtual Machine (JVM) has initialized, the premain method
     * will be called. Then the real application main method will be called.
     * 
     * @param args arguments supplied to the agent
     * @param instrumentation {@link Instrumentation} object passed by JVM
     * @throws Exception thrown when agent is unable to start
     */
    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        AsyncAwaitInstrumentationAgent agent = new AsyncAwaitInstrumentationAgent();
        agent.install(args, instrumentation);
        System.setProperty(JavaFlowClassTransformer.class.getName(), "true");
        System.setProperty(AsyncAwaitInstrumentationAgent.class.getName(), "true");
        
        int jdkVersion = getJdkVersion(); 
        if (jdkVersion < 9) {
            if (log.isDebugEnabled()) {
                log.debug("JDK verion " + jdkVersion + " is less than 9, no lambda instrumentation patch required.");
            }
            return;
        }
        
        // Need to patch lambda metafactory to support agent instrumentation for lambda classes
        // in JDK 9+
        try {
            if (!RuntimeBytecodeInjector.isInjectionApplied()) {
                log.debug("Applying lambda post-processing injection...");
                instrumentation.redefineClasses(RuntimeBytecodeInjector.modifyLambdaMetafactory());
                log.debug("Lambda post-processing injection is applied.");
            } else {
                log.warn("Lambda post-processing injection was already applied, probably by some other agent.");
            }
            RuntimeBytecodeInjector.installTransformer(new RuntimeBytecodeInjector.LambdaClassTransformer() {
                @Override
                public byte[] transform(Class<?> lambdaOwningClass, byte[] lambdaClassBytes) throws Throwable {
                    return agent.transformLambdaClass(lambdaOwningClass, lambdaClassBytes);
                }
            });
        } catch (Throwable ex) {
            log.warn("Unable to apply lambda instrumentation patch (unsupported JVM)", ex);
        }
    }

    /**
     * JVM hook to dynamically load javaagent at runtime.
     * 
     * The agent class may have an agentmain method for use when the agent is
     * started after VM startup.
     * 
     * @param args arguments supplied to the agent
     * @param instrumentation {@link Instrumentation} object passed by JVM
     * @throws Exception thrown when agent is unable to start
     */
    public static void agentmain(String args, Instrumentation instrumentation) throws Exception {
        Set<String> ownPackages = new HashSet<>(BASE_OWN_PACKAGES);
        ownPackages.add("net.tascalate.async.tools.");
        
        new AsyncAwaitInstrumentationAgent().attach(args, instrumentation, ownPackages);
        
        System.setProperty(JavaFlowClassTransformer.class.getName(), "true");
        System.setProperty(AsyncAwaitInstrumentationAgent.class.getName(), "true");
    }

    @Override
    protected ClassFileTransformer createRetransformableTransformer(String args, Instrumentation instrumentation) {
        return new AsyncAwaitClassFileTransformer(continuationsTransformer, instrumentation);
    }
    
    byte[] transformLambdaClass(Class<?> lambdaOwningClass, byte[] input) throws Throwable {
        try {
            String className = null;            
            if (log.isDebugEnabled()) {
                className = ClassEmitters.classNameOf(input);
                log.debug("Start transforming lambda class " + className + ", defined in " + lambdaOwningClass.getName());
            }
            byte[] output = continuationsTransformer.transform(lambdaOwningClass.getClassLoader(), 
                                                               null, null, // Neither name, nor class
                                                               lambdaOwningClass.getProtectionDomain(), 
                                                               input);
            if (log.isDebugEnabled()) {
                log.debug("Done transforming lambda class " + className + 
                           ", defined in " + lambdaOwningClass.getName() + 
                           ", modified = " + (input != output && output != null));
            }                
            return output;
        } catch (Throwable ex) {
            log.error("Error transforming lambda class defined in " + lambdaOwningClass.getName(), ex);
            throw ex;
        }
        
    }
    
    private static int getJdkVersion() {
        String version = System.getProperty("java.version");
        
        if (version.startsWith("1.")) {
            version = version.substring(2, version.indexOf('.', 2));
        } else {
            int dot = version.indexOf(".");
            if (dot > 0) { 
                version = version.substring(0, dot); 
            }
        } 
        int javaVersion = Integer.parseInt(version);
        return javaVersion;
    }

}
