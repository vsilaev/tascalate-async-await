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
package net.tascalate.async.tools.core;

import static net.tascalate.async.tools.core.BytecodeIntrospection.isAsyncMethod;
import static net.tascalate.async.tools.core.BytecodeIntrospection.methodsOf;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.javaflow.spi.ResourceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tascalate.asmx.ClassReader;
import net.tascalate.asmx.ClassVisitor;
import net.tascalate.asmx.ClassWriter;
import net.tascalate.asmx.Type;
import net.tascalate.asmx.plus.ClassHierarchy;
import net.tascalate.asmx.plus.OfflineClassWriter;
import net.tascalate.asmx.tree.ClassNode;
import net.tascalate.asmx.tree.MethodNode;
import net.tascalate.asmx.util.CheckClassAdapter;
import net.tascalate.asmx.util.TraceClassVisitor;

public class AsyncAwaitClassFileGenerator {

    private final static Logger log = LoggerFactory.getLogger(AsyncAwaitClassFileGenerator.class);
    
    private final static Type COMPLETION_STAGE_TYPE   = Type.getObjectType("java/util/concurrent/CompletionStage");
    private final static Type COMPLETABLE_FUTURE_TYPE = Type.getObjectType("java/util/concurrent/CompletableFuture");
    private final static Type ASYNC_RESULT_TYPE       = Type.getObjectType("net/tascalate/async/AsyncResult");
    private final static Type TASCALATE_PROMISE_TYPE  = Type.getObjectType("net/tascalate/concurrent/Promise");
    private final static Type ASYNC_GENERATOR_TYPE    = Type.getObjectType("net/tascalate/async/AsyncGenerator");
    
    private static final Set<Type> ASYNC_TASK_RETURN_TYPES = 
        Stream.of(COMPLETION_STAGE_TYPE, 
                  COMPLETABLE_FUTURE_TYPE,
                  ASYNC_RESULT_TYPE,
                  TASCALATE_PROMISE_TYPE,
                  Type.VOID_TYPE)
               .collect(Collectors.toSet());
    
    // New generated classes
    private final List<ClassNode> newClasses = new ArrayList<ClassNode>();

    // Original method's "method name + method desc" -> Access method's
    // MethodNode
    private final Map<String, MethodNode> accessMethods = new HashMap<>();
    private final Map<String, ClassNode> superclasses = new HashMap<>();
    private final ClassHierarchy classHierarchy;
    private final boolean verify;
    private final boolean trace;
    
    public AsyncAwaitClassFileGenerator(ResourceLoader resourceLoader) {
        this(resourceLoader, true, false);
    }
    
    public AsyncAwaitClassFileGenerator(ResourceLoader resourceLoader, boolean verify, boolean trace) {
        this.classHierarchy = new ClassHierarchy(new AsmxResourceLoader(resourceLoader));
        this.verify = verify;
        this.trace = trace;
    }
    
    public byte[] transform(byte[] classfileBuffer) {
        // Read
        ClassReader classReader = new ClassReader(classfileBuffer);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.SKIP_FRAMES);

        // Transform
        if (!transform(classNode)) {
            // no modification, delegate further
            return null;
        }

        if (log.isDebugEnabled()) {
            // Print transformed class
            log.debug("Transformed class:\n\n" + BytecodeTraceUtil.toString(classNode) + "\n\n");
            
            // Print generated classes
            for (ClassNode newClass : newClasses) {
                log.debug("Generated class:\n\n" + BytecodeTraceUtil.toString(newClass) + "\n\n");
            }
        }

        // Write
        byte[] generatedClassBytes;
        {
            ClassWriter cw = new OfflineClassWriter(classHierarchy, ClassWriter.COMPUTE_FRAMES);
            classNode.accept(decorate(cw));
            generatedClassBytes = cw.toByteArray();
        }
        return generatedClassBytes;
    }

    public Map<String, byte[]> getGeneratedClasses() {
        Map<String, byte[]> result = new HashMap<String, byte[]>();
        for (ClassNode classNode : newClasses) {
            ClassWriter cw = new OfflineClassWriter(classHierarchy, ClassWriter.COMPUTE_FRAMES);
            classNode.accept(decorate(cw));
            result.put(classNode.name, cw.toByteArray());
        }
        return result;
    }

    public void reset() {
        accessMethods.clear();
        newClasses.clear();
    }
    
    protected ClassVisitor decorate(ClassVisitor classVisitor) {
        ClassVisitor result = classVisitor;
        if (verify) {
            result = new CheckClassAdapter(result, true);
        }
        if (trace) {
            result = new TraceClassVisitor(result, new PrintWriter(System.out));
        }
        return result;
    }

    protected boolean transform(ClassNode classNode) {
        boolean transformed = false;
        
        AbstractAsyncMethodTransformer.Helper helper = new AbstractAsyncMethodTransformer.Helper() {
            @Override
            public ClassNode resolveClass(String cn) {
                return superclasses.computeIfAbsent(cn, className -> {
                    try (InputStream in = classHierarchy.loader().getResourceAsStream(className + ".class")) {
                        ClassReader classReader = new ClassReader(in);
                        ClassNode classNode = new ClassNode();
                        classReader.accept(classNode, ClassReader.SKIP_FRAMES);
                        return classNode;
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }            
            
            @Override
            public boolean isSubClass(String maybeSubclass, String gmaybeParentClass) {
                return classHierarchy.isSubClass(maybeSubclass, gmaybeParentClass);
            }
        };
        
        for (MethodNode methodNode : new ArrayList<MethodNode>(methodsOf(classNode))) {
            if (isAsyncMethod(methodNode)) {
                Type returnType = Type.getReturnType(methodNode.desc);
                AbstractAsyncMethodTransformer transformer = null;
                if (ASYNC_TASK_RETURN_TYPES.contains(returnType)) {
                    transformer = new AsyncTaskMethodTransformer(classNode, methodNode, accessMethods, helper);
                } else if (ASYNC_GENERATOR_TYPE.equals(returnType)) {
                    transformer = new AsyncGeneratorMethodTransformer(classNode, methodNode, accessMethods, helper);
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
