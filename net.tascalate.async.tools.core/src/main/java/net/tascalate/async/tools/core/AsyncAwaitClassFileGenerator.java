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
package net.tascalate.async.tools.core;

import static net.tascalate.async.tools.core.BytecodeIntrospection.isAsyncMethod;
import static net.tascalate.async.tools.core.BytecodeIntrospection.methodsOf;

import java.lang.instrument.IllegalClassFormatException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    private final static Type TYPE_COMPLETION_STAGE  = Type.getObjectType("java/util/concurrent/CompletionStage");
    private final static Type TYPE_TASCALATE_PROMISE = Type.getObjectType("net/tascalate/concurrent/Promise");
    private final static Type TYPE_GENERATOR         = Type.getObjectType("net/tascalate/async/api/Generator");
    
    // New generated classes
    private final List<ClassNode> newClasses = new ArrayList<ClassNode>();

    // Original method's "method name + method desc" -> Access method's
    // MethodNode
    private final Map<String, MethodNode> accessMethods = new HashMap<String, MethodNode>();

    public byte[] transform(String className, byte[] classfileBuffer, ResourceLoader resourceLoader) throws IllegalClassFormatException {
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

    public Map<String, byte[]> getGeneratedClasses(ResourceLoader resourceLoader) {
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
                AsyncMethodTransformer transformer = null;
                if (TYPE_COMPLETION_STAGE.equals(returnType) || TYPE_TASCALATE_PROMISE.equals(returnType) || Type.VOID_TYPE.equals(returnType)) {
                    transformer = new AsyncTaskMethodTransformer(classNode, methodNode, accessMethods);
                } else if (TYPE_GENERATOR.equals(returnType)) {
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
