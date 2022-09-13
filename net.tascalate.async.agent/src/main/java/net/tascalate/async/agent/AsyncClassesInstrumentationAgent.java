/**
 * ﻿Copyright 2015-2022 Valery Silaev (http://vsilaev.com)
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
import java.lang.instrument.Instrumentation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.javaflow.spi.InstrumentationUtils;

import net.tascalate.instrument.agent.AbstractLambdaAwareInstrumentationAgent;

public class AsyncClassesInstrumentationAgent extends AbstractLambdaAwareInstrumentationAgent {

    private final ClassFileTransformer continuationsTransformer = new ContinuableClassBytecodeTransformer();
    
    
    protected AsyncClassesInstrumentationAgent(String arguments, Instrumentation instrumentation) {
        super(arguments, instrumentation);
    }

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
        AsyncClassesInstrumentationAgent agent = new AsyncClassesInstrumentationAgent(args, instrumentation);
        agent.attachDefaultLambdaInstrumentationHook();
        agent.install();
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
        AsyncClassesInstrumentationAgent agent = new AsyncClassesInstrumentationAgent(args, instrumentation);
        agent.attachDefaultLambdaInstrumentationHook();
        Set<String> nonRetransformPackages = new HashSet<String>(BASE_OWN_PACKAGES);
        nonRetransformPackages.addAll(
            InstrumentationUtils.packagePrefixesOf(InstrumentationUtils.class)
        );
        nonRetransformPackages.addAll(Dependencies.PACKAGES);
        agent.attach(nonRetransformPackages);
    }
    
    @Override
    protected Collection<ClassFileTransformer> createTransformers(boolean canRetransform) {
        if (canRetransform) {
            ClassFileTransformer transformer = new AsyncClassBytecodeTransformer(continuationsTransformer, instrumentation);
            return Collections.singleton(transformer);
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    protected String readLambdaClassName(byte[] bytes) {
        return InstrumentationUtils.readClassName(bytes);
    }

    void attachDefaultLambdaInstrumentationHook() throws Exception {
        attachLambdaInstrumentationHook(createLambdaClassTransformer(continuationsTransformer));  
    }
}
