/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.tools.core;

import static net.tascalate.async.tools.core.BytecodeIntrospection.isAsyncMethod;
import static net.tascalate.async.tools.core.BytecodeIntrospection.methodsOf;

import java.lang.instrument.IllegalClassFormatException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.javaflow.spi.ResourceLoader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class AsyncAwaitClassFileGenerator {

    private final static Log log = LogFactory.getLog(AsyncAwaitClassFileGenerator.class);
    
    private final static Type COMPLETION_STAGE_TYPE   = Type.getObjectType("java/util/concurrent/CompletionStage");
    private final static Type COMPLETABLE_FUTURE_TYPE = Type.getObjectType("java/util/concurrent/CompletableFuture");
    private final static Type ASYNC_VALUE_TYPE        = Type.getObjectType("net/tascalate/async/AsyncValue");
    private final static Type TASCALATE_PROMISE_TYPE  = Type.getObjectType("net/tascalate/concurrent/Promise");
    private final static Type ASYNC_GENERATOR_TYPE    = Type.getObjectType("net/tascalate/async/AsyncGenerator");
    
    private static final Set<Type> ASYNC_TASK_RETURN_TYPES = 
        Stream.of(COMPLETION_STAGE_TYPE, 
                  COMPLETABLE_FUTURE_TYPE,
                  ASYNC_VALUE_TYPE,
                  TASCALATE_PROMISE_TYPE,
                  Type.VOID_TYPE)
               .collect(Collectors.toSet());
    
    // New generated classes
    private final List<ClassNode> newClasses = new ArrayList<ClassNode>();

    // Original method's "method name + method desc" -> Access method's
    // MethodNode
    private final Map<String, MethodNode> accessMethods = new HashMap<String, MethodNode>();
    private final ResourceLoader resourceLoader;
    
    public AsyncAwaitClassFileGenerator(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
    
    public byte[] transform(byte[] classfileBuffer) throws IllegalClassFormatException {
        // Read
        ClassReader classReader = new ClassReader(classfileBuffer);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES);

        // Transform
        if (!transform(classNode)) {
            // no modification, delegate further
            return null;
        }

        // Print transformed class
        log.debug("Transformed class:\n\n" + BytecodeTraceUtil.toString(classNode) + "\n\n");

        // Print generated classes
        for (ClassNode newClass : newClasses) {
            log.debug("Generated class:\n\n" + BytecodeTraceUtil.toString(newClass) + "\n\n");
        }

        // Write
        byte[] generatedClassBytes;
        {
            ClassWriter cw = new ComputeClassWriter(0, resourceLoader);
            classNode.accept(cw);
            generatedClassBytes = cw.toByteArray();
        }
        return generatedClassBytes;
    }

    public Map<String, byte[]> getGeneratedClasses() {
        Map<String, byte[]> result = new HashMap<String, byte[]>();
        for (ClassNode classNode : newClasses) {
            ClassWriter cw = new ComputeClassWriter(ClassWriter.COMPUTE_FRAMES, resourceLoader);
            classNode.accept(cw);
            result.put(classNode.name, cw.toByteArray());
        }
        return result;
    }

    public void reset() {
        accessMethods.clear();
        newClasses.clear();
    }

    protected boolean transform(ClassNode classNode) {
        boolean transformed = false;
        
        for (MethodNode methodNode : new ArrayList<MethodNode>(methodsOf(classNode))) {
            if (isAsyncMethod(methodNode)) {
                Type returnType = Type.getReturnType(methodNode.desc);
                AbstractAsyncMethodTransformer transformer = null;
                if (ASYNC_TASK_RETURN_TYPES.contains(returnType)) {
                    transformer = new AsyncTaskMethodTransformer(classNode, methodNode, accessMethods);
                } else if (ASYNC_GENERATOR_TYPE.equals(returnType)) {
                    transformer = new AsyncGeneratorMethodTransformer(classNode, methodNode, accessMethods);
                } else {
                    // throw ex?
                }
                if (null != transformer) {
                    ClassNode newClass = transformer.transform();
                    if (null != newClass) {
                        newClasses.add(newClass);
                        transformed = true;
                    }
                }
            }
        }
        return transformed;
    }
}
