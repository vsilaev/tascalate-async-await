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

public class AsyncAwaitInstrumentationAgent extends AbstractInstrumentationAgent {
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
        new AsyncAwaitInstrumentationAgent().install(args, instrumentation);
        System.setProperty(JavaFlowClassTransformer.class.getName(), "true");
        System.setProperty(AsyncAwaitInstrumentationAgent.class.getName(), "true");
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
        return new AsyncAwaitClassFileTransformer(new JavaFlowClassTransformer(), instrumentation);
    }
}
