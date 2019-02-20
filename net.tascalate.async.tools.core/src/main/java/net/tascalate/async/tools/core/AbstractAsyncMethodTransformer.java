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

import static net.tascalate.async.tools.core.BytecodeIntrospection.createAccessMethodName;
import static net.tascalate.async.tools.core.BytecodeIntrospection.createInnerClassName;
import static net.tascalate.async.tools.core.BytecodeIntrospection.createOuterClassMethodArgFieldName;
import static net.tascalate.async.tools.core.BytecodeIntrospection.getField;
import static net.tascalate.async.tools.core.BytecodeIntrospection.getMethod;
import static net.tascalate.async.tools.core.BytecodeIntrospection.getMethodSignature;
import static net.tascalate.async.tools.core.BytecodeIntrospection.innerClassesOf;
import static net.tascalate.async.tools.core.BytecodeIntrospection.invisibleAnnotationsOf;
import static net.tascalate.async.tools.core.BytecodeIntrospection.invisibleParameterAnnotationsOf;
import static net.tascalate.async.tools.core.BytecodeIntrospection.invisibleTypeAnnotationsOf;
import static net.tascalate.async.tools.core.BytecodeIntrospection.methodsOf;
import static net.tascalate.async.tools.core.BytecodeIntrospection.visibleAnnotationsOf;
import static net.tascalate.async.tools.core.BytecodeIntrospection.visibleParameterAnnotationsOf;
import static net.tascalate.async.tools.core.BytecodeIntrospection.visibleTypeAnnotationsOf;
import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

abstract public class AbstractAsyncMethodTransformer {
    protected final static Log log = LogFactory.getLog(AsyncAwaitClassFileGenerator.class);

    private final static String ASYNC_ANNOTATION_DESCRIPTOR = "Lnet/tascalate/async/async;";
    
    protected final static String CALL_CONTXT_NAME = "net/tascalate/async/CallContext";
    
    protected final static Type SUSPENDABLE_ANNOTATION_TYPE = Type.getObjectType("net/tascalate/async/suspendable");
    protected final static Type COMPLETION_STAGE_TYPE       = Type.getObjectType("java/util/concurrent/CompletionStage");
    protected final static Type OBJECT_TYPE                 = Type.getType(Object.class);
    protected final static Type STRING_TYPE                 = Type.getType(String.class);
    protected final static Type CLASS_TYPE                  = Type.getType(Class.class);    
    protected final static Type ASYNC_METHOD_EXECUTOR_TYPE  = Type.getObjectType("net/tascalate/async/core/AsyncMethodExecutor");
    protected final static Type TASCALATE_PROMISE_TYPE      = Type.getObjectType("net/tascalate/concurrent/Promise");
    protected final static Type TASCALATE_PROMISES_TYPE     = Type.getObjectType("net/tascalate/concurrent/Promises");
    protected final static Type ABSTRACT_ASYNC_METHOD_TYPE  = Type.getObjectType("net/tascalate/async/core/AbstractAsyncMethod");
    
    private final static Type SCHEDULER_TYPE             = Type.getObjectType("net/tascalate/async/Scheduler");
    private final static Type SCHEDULER_PROVIDER_TYPE    = Type.getObjectType("net/tascalate/async/SchedulerProvider");

    protected final ClassNode classNode;
    protected final MethodNode originalAsyncMethod;

    // Original method's "method name + method desc" -> Access method's
    // MethodNode
    protected final Map<String, MethodNode> accessMethods;
    
    protected AbstractAsyncMethodTransformer(ClassNode               classNode, 
                                             MethodNode              originalAsyncMethod,
                                             Map<String, MethodNode> accessMethods) {
        
        this.classNode = classNode;
        this.originalAsyncMethod = originalAsyncMethod;
        this.accessMethods = accessMethods;
    }

    
    abstract public ClassNode transform();
    
    public ClassNode transform(Type superClassType) {
        log.info("Transforming blocking method: " + classNode.name + "." + originalAsyncMethod.name
                + originalAsyncMethod.desc);
        //removeAsyncAnnotation(originalAsyncMethod);

        // Create InnerClassNode for anoymous class
        String asyncTaskClassName = createInnerClassName(classNode);
        innerClassesOf(classNode).add(new InnerClassNode(asyncTaskClassName, null, null, (originalAsyncMethod.access & ACC_STATIC)));

        // Create accessor methods
        createAccessMethodsForAsyncMethod();

        // Create ClassNode for anonymous class
        ClassNode asyncTaskClassNode = createAnonymousClass(asyncTaskClassName, superClassType);

        // Replace original method

        List<MethodNode> methods = methodsOf(classNode);
        methods.remove(originalAsyncMethod);
        
        createReplacementAsyncMethod(asyncTaskClassName);
        
        //System.out.println(BytecodeTraceUtil.toString(classNode));
        return asyncTaskClassNode;
    }
    
    abstract protected MethodVisitor createReplacementAsyncMethod(String asyncTaskClassName);
    abstract protected MethodVisitor addAnonymousClassRunMethod(ClassNode asyncRunnableClass, FieldNode outerClassField);

    
    protected ClassNode createAnonymousClass(String asyncTaskClassName, Type superClassType) {
        boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;

        ClassNode asyncRunnableClass = new ClassNode();

        asyncRunnableClass.visit(classNode.version, ACC_FINAL + ACC_SUPER, asyncTaskClassName, null, superClassType.getInternalName(), null);
        asyncRunnableClass.visitSource(classNode.sourceFile, null);
        asyncRunnableClass.visitOuterClass(classNode.name, originalAsyncMethod.name, originalAsyncMethod.desc);

        // SerialVersionUID
        asyncRunnableClass.visitField(
            ACC_PRIVATE + ACC_FINAL + ACC_STATIC, 
            "serialVersionUID", 
            "J", 
            null,
            new Long(1L)
        );

        // Outer class instance field
        FieldNode outerClassField;
        if (!isStatic) {
            outerClassField = (FieldNode) asyncRunnableClass.visitField(
                ACC_FINAL + ACC_PRIVATE + ACC_SYNTHETIC,
                "this$0", 
                "L" + classNode.name + ";", 
                null, 
                null
            );
        } else {
            outerClassField = null;
        }

        // Original methods arguments
        Type[] argTypes = Type.getArgumentTypes(originalAsyncMethod.desc);
        int originalArity = argTypes.length;
        {
            for (int i = 0; i < originalArity; i++) {
                String argName = createOuterClassMethodArgFieldName(i);
                String argDesc = argTypes[i].getDescriptor();
                asyncRunnableClass.visitField(ACC_PRIVATE /* + ACC_FINAL */ + ACC_SYNTHETIC, argName, argDesc, null, null);
            }
        }

        addAnonymousClassConstructor(asyncRunnableClass, superClassType, outerClassField);
        addAnonymousClassRunMethod(asyncRunnableClass, outerClassField);
        addAnonymousClassToStringMethod(asyncRunnableClass, superClassType);
        return asyncRunnableClass;
    }
    
    protected MethodVisitor addAnonymousClassConstructor(ClassNode asyncRunnableClass, Type superClassType, FieldNode outerClassField) {
        boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;
        // Original methods arguments
        Type[] originalArgTypes = Type.getArgumentTypes(originalAsyncMethod.desc);
        int originalArity = originalArgTypes.length;

        String constructorDesc = Type.getMethodDescriptor(
            Type.VOID_TYPE,
            appendArray(
                isStatic ? originalArgTypes : prependArray(originalArgTypes, Type.getObjectType(classNode.name)),
                SCHEDULER_TYPE
            )
        );

        MethodVisitor result = asyncRunnableClass.visitMethod(0, "<init>", constructorDesc, null, null);
        result.visitCode();

        if (!isStatic) {
            // Store outer class instance
            result.visitVarInsn(ALOAD, 0);
            result.visitVarInsn(ALOAD, 1);
            result.visitFieldInsn(PUTFIELD, asyncRunnableClass.name, outerClassField.name, outerClassField.desc);
        }

        // Store original method's arguments
        int paramVarIdx = isStatic ? 1 : 2;
        boolean hasDWordParam = false;
        for (int i = 0; i < originalArity; i++) {
            Type argType = originalArgTypes[i];
            result.visitVarInsn(ALOAD, 0);
            result.visitVarInsn(argType.getOpcode(ILOAD), paramVarIdx);
            paramVarIdx += argType.getSize();
            hasDWordParam |= argType.getSize() > 1; 
            result.visitFieldInsn(PUTFIELD, asyncRunnableClass.name, createOuterClassMethodArgFieldName(i), argType.getDescriptor());
        }

        // Invoke super()
        result.visitVarInsn(ALOAD, 0);
        result.visitVarInsn(ALOAD, paramVarIdx);
        result.visitMethodInsn(
            INVOKESPECIAL, superClassType.getInternalName(), 
            "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, SCHEDULER_TYPE), false
        );

        result.visitInsn(RETURN);
        result.visitMaxs(hasDWordParam ? 3 : 2 /* stack */, paramVarIdx /* locals */);
        result.visitEnd();

        return result;        
    }

    protected MethodVisitor addAnonymousClassToStringMethod(ClassNode asyncRunnableClass, Type runnableBaseClass) {
        MethodVisitor result = asyncRunnableClass.visitMethod(
            ACC_PUBLIC, "toString", Type.getMethodDescriptor(STRING_TYPE), null, null
        );
        result.visitVarInsn(ALOAD, 0);
        result.visitLdcInsn(classNode.name.replace('/', '.'));
        result.visitLdcInsn(getMethodSignature(originalAsyncMethod, true));
        result.visitMethodInsn(
            INVOKEVIRTUAL, runnableBaseClass.getInternalName(), 
            "toString", Type.getMethodDescriptor(STRING_TYPE, STRING_TYPE, STRING_TYPE), 
            false
         );
        result.visitInsn(ARETURN);
        result.visitMaxs(3, 1);
        return result;
        
    }
    
    protected Object[] findOwnerInvokeDynamic(AbstractInsnNode instruction, List<MethodNode> ownerMethods) {
        if (instruction instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode n = (InvokeDynamicInsnNode) instruction;
            Handle bsm = n.bsm;
            if ("java/lang/invoke/LambdaMetafactory".equals(bsm.getOwner()) && "metafactory".equals(bsm.getName())) {
                Handle method = Arrays
                    .stream(n.bsmArgs)
                    .filter(Handle.class::isInstance)
                    .map(Handle.class::cast)
                    .filter(h -> h.getOwner().equals(classNode.name) /*&& h.getName().startsWith("lambda$")*/)
                    .findFirst()
                    .orElse(null);
                
                if (null != method) {
                    MethodNode targetMethodNode = getMethod(method.getName(), method.getDesc(), ownerMethods);
                    if (null != targetMethodNode && (targetMethodNode.access & (/*ACC_STATIC + ACC_PRIVATE +*/ ACC_SYNTHETIC)) == /*ACC_STATIC + ACC_PRIVATE + */ACC_SYNTHETIC) {
                        return new Object[] {method, targetMethodNode};
                    }
                }
            }
        }
        return null;
    }
    
    protected MethodVisitor createReplacementAsyncMethod(String asyncTaskClassName, Type runnableBaseClass, String runnableFieldName, Type runnableFieldType) {
        boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;
        int thisArgShift = isStatic ? 0 : 1;
        Type[] originalArgTypes = Type.getArgumentTypes(originalAsyncMethod.desc);
        int originalArity = originalArgTypes.length;

        MethodVisitor result = classNode.visitMethod( 
            originalAsyncMethod.access, originalAsyncMethod.name, 
            originalAsyncMethod.desc, originalAsyncMethod.signature, 
            null
        );
        
        //replacementAsyncMethodNode.invisibleAnnotations = copyAnnotations(invisibleAnnotationsOf(originalAsyncMethod));
        //Remove @async annotation
        //removeAsyncAnnotation(replacementAsyncMethodNode);
        invisibleAnnotationsOf(originalAsyncMethod)
            .stream()
            .filter(an -> !ASYNC_ANNOTATION_DESCRIPTOR.equals(an.desc))
            .forEach(an -> an.accept( result.visitAnnotation(an.desc, false) ) );
         
        //replacementAsyncMethodNode.visibleAnnotations = copyAnnotations(visibleAnnotationsOf(originalAsyncMethod));
        visibleAnnotationsOf(originalAsyncMethod)
            .forEach(an -> an.accept( result.visitAnnotation(an.desc, true) ) );

        //replacementAsyncMethodNode.invisibleTypeAnnotations = copyTypeAnnotations();
        invisibleTypeAnnotationsOf(originalAsyncMethod)
            .forEach(an -> an.accept( result.visitTypeAnnotation(an.typeRef, an.typePath, an.desc, false)) );
        //replacementAsyncMethodNode.visibleTypeAnnotations = copyTypeAnnotations(visibleTypeAnnotationsOf(originalAsyncMethod));
        visibleTypeAnnotationsOf(originalAsyncMethod)
            .forEach(an -> an.accept( result.visitTypeAnnotation(an.typeRef, an.typePath, an.desc, true)) );
        
        result.visitAnnotation(SUSPENDABLE_ANNOTATION_TYPE.getDescriptor(), true).visitEnd();
        
        //replacementAsyncMethodNode.invisibleParameterAnnotations = copyParameterAnnotations();
        copyParameterAnnotations(result, invisibleParameterAnnotationsOf(originalAsyncMethod), false);
        //replacementAsyncMethodNode.visibleParameterAnnotations = copyParameterAnnotations(visibleParameterAnnotationsOf(originalAsyncMethod));
        copyParameterAnnotations(result, visibleParameterAnnotationsOf(originalAsyncMethod), true);
        
        result.visitCode();

        int providedSchedulerParamIdx = schedulerProviderParamIdx(originalAsyncMethod);
        if (providedSchedulerParamIdx >= 0) {
            result.visitVarInsn(ALOAD, providedSchedulerParamIdx + thisArgShift);
        } else {
            result.visitInsn(ACONST_NULL);
        }
        // Resolve by owner if non-static
        if (isStatic) {
            result.visitInsn(ACONST_NULL);
        } else {
            result.visitVarInsn(ALOAD, 0);
        }
        result.visitLdcInsn(Type.getObjectType(classNode.name));
        result.visitMethodInsn(
            INVOKESTATIC, ASYNC_METHOD_EXECUTOR_TYPE.getInternalName(), "currentScheduler", 
            Type.getMethodDescriptor(SCHEDULER_TYPE, SCHEDULER_TYPE, OBJECT_TYPE, CLASS_TYPE), false
        );
        
        String constructorDesc = Type.getMethodDescriptor(
            Type.VOID_TYPE, 
            appendArray(
                isStatic ? originalArgTypes : prependArray(originalArgTypes, Type.getObjectType(classNode.name)),
                SCHEDULER_TYPE
            )
        );
        int schedulerVarIdx = Arrays.stream(originalArgTypes).mapToInt(a -> a.getSize()).sum() + thisArgShift;
        result.visitVarInsn(ASTORE, schedulerVarIdx);
        
        result.visitTypeInsn(NEW, asyncTaskClassName);
        result.visitInsn(DUP);
        if (!isStatic) {
            // Reference to outer this
            result.visitVarInsn(ALOAD, 0);
        }

        // load all method arguments into stack
        int paramVarIdx = thisArgShift;
        for (int i = 0; i < originalArity; i++) {
            Type originalArgType = originalArgTypes[i];
            result.visitVarInsn(originalArgType.getOpcode(ILOAD), paramVarIdx);
            paramVarIdx += originalArgType.getSize();
        }
        
        // load resolved scheduler
        result.visitVarInsn(ALOAD, schedulerVarIdx);

        result.visitMethodInsn(INVOKESPECIAL, asyncTaskClassName, "<init>", constructorDesc, false);
        int methodVarIdx = schedulerVarIdx + 1;
        result.visitVarInsn(ASTORE, methodVarIdx);

        result.visitVarInsn(ALOAD, methodVarIdx);
        result.visitMethodInsn(
            INVOKESTATIC, ASYNC_METHOD_EXECUTOR_TYPE.getInternalName(), "execute", 
            Type.getMethodDescriptor(Type.VOID_TYPE, ABSTRACT_ASYNC_METHOD_TYPE), false
        );

        Type returnType = Type.getReturnType(originalAsyncMethod.desc);
        boolean hasResult = !Type.VOID_TYPE.equals(returnType); 
        if (hasResult) {
            result.visitVarInsn(ALOAD, methodVarIdx);
            result.visitFieldInsn(
                GETFIELD, runnableBaseClass.getInternalName(), runnableFieldName, runnableFieldType.getDescriptor()
            );
            if (TASCALATE_PROMISE_TYPE.equals(returnType)) {
                result.visitMethodInsn(
                    INVOKESTATIC, TASCALATE_PROMISES_TYPE.getInternalName(), "from", 
                    Type.getMethodDescriptor(TASCALATE_PROMISE_TYPE, COMPLETION_STAGE_TYPE), false
                ); 
            }
            result.visitInsn(ARETURN);
        } else {
            result.visitInsn(RETURN);
        }

        result.visitMaxs(
            Math.max(3 /*to resolve scheduler*/, methodVarIdx + 1), // for constructor call (incl. scheduler + DUP)
            methodVarIdx // params count + outer this (for non static) + resolved scheduler + methodRunnable
        );

        result.visitEnd();
        return result;
    }
    
    protected void copyParameterAnnotations(MethodVisitor target, List<AnnotationNode>[] annotationsByIdx, boolean visible) {
        if (null == annotationsByIdx) {
            return;
        }
        for (int i = 0; i < annotationsByIdx.length; i++ ) {
            List<AnnotationNode> annotations = annotationsByIdx[i];
            if (null != annotations) {
                int idx = 0;
                annotations.forEach(an -> an.accept(target.visitParameterAnnotation(idx, an.desc, visible)) );
            }
        }
    }
    
    protected int schedulerProviderParamIdx(MethodNode methodNode) {
        int result = -1;
        List<AnnotationNode>[] annotationBatches = visibleParameterAnnotationsOf(methodNode); 
        if (null == annotationBatches) {
            return -1;
        }
        int idx = 0;
        for (List<AnnotationNode> annotations : annotationBatches) {
            if (null != annotations) {
                boolean found = annotations.stream().anyMatch(a -> SCHEDULER_PROVIDER_TYPE.getDescriptor().equals(a.desc));
                if (found) {
                    if (result < 0) {
                        result = idx; 
                    } else {
                        throw new IllegalStateException("More than one parameter of " + methodNode.desc + " is annotated as @SchedulerProvider");
                    }
                }
            }
            idx++;
        }
        return result;
    }
    
    protected static List<TypeAnnotationNode> copyTypeAnnotations(List<TypeAnnotationNode> originalAnnotations) {
        if (null == originalAnnotations || originalAnnotations.isEmpty()) {
            return null;
        }
        return originalAnnotations
            .stream()
            .map(t -> new TypeAnnotationNode(t.typeRef, t.typePath, t.desc))
            .collect(Collectors.toCollection(ArrayList::new));
    }
    
    protected void createAccessMethodsForAsyncMethod() {
        List<MethodNode> methods = methodsOf(classNode);
        for (Iterator<?> i = originalAsyncMethod.instructions.iterator(); i.hasNext();) {
            AbstractInsnNode instruction = (AbstractInsnNode) i.next();
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode methodInstructionNode = (MethodInsnNode) instruction;
                if ((methodInstructionNode.getOpcode() == INVOKEVIRTUAL || 
                     methodInstructionNode.getOpcode() == INVOKESPECIAL || 
                     methodInstructionNode.getOpcode() == INVOKESTATIC
                    ) && methodInstructionNode.owner.equals(classNode.name)) {
                    
                    MethodNode targetMethodNode = getMethod(methodInstructionNode.name, methodInstructionNode.desc,
                            methods);
                    if (null != targetMethodNode && (targetMethodNode.access & ACC_PRIVATE) != 0) {
                        log.debug("Found private call " + BytecodeTraceUtil.toString(methodInstructionNode));
                        createAccessMethod(methodInstructionNode,
                                (targetMethodNode.access & ACC_STATIC) != 0, methods);
                    }
                }

                if (methodInstructionNode.getOpcode() == INVOKESPECIAL && 
                    !"<init>".equals(methodInstructionNode.name)  && 
                    !methodInstructionNode.owner.equals(classNode.name)) {
                    // INVOKESPECIAL is used for constructors/super-call,
                    // private instance methods
                    // Here we filtered out only to private super-method calls
                    log.debug("Found super-call " + BytecodeTraceUtil.toString(methodInstructionNode));
                    createAccessMethod(methodInstructionNode, false, methods);
                }

            }
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode fieldInstructionNode = (FieldInsnNode) instruction;
                if (fieldInstructionNode.owner.equals(classNode.name)) {
                    FieldNode targetFieldNode = 
                        getField(classNode, fieldInstructionNode.name, fieldInstructionNode.desc);
                    
                    if (null != targetFieldNode && (targetFieldNode.access & ACC_PRIVATE) != 0) {
                        // log.debug("Found " +
                        // BytecodeTraceUtil.toString(fieldInstructionNode));
                        if (fieldInstructionNode.getOpcode() == GETSTATIC || 
                            fieldInstructionNode.getOpcode() == GETFIELD) {
                            
                            createAccessGetter(fieldInstructionNode, (targetFieldNode.access & ACC_STATIC) != 0, methods);
                            
                        } else if (fieldInstructionNode.getOpcode() == PUTSTATIC || 
                                   fieldInstructionNode.getOpcode() == PUTFIELD) {
                            
                            createAccessSetter(fieldInstructionNode, (targetFieldNode.access & ACC_STATIC) != 0, methods);
                        }
                    }
                }
            }
            if (instruction instanceof InvokeDynamicInsnNode) {
                Object[] result = findOwnerInvokeDynamic(instruction, methods);
                if (null != result) {
                    MethodNode method = (MethodNode)result[1];
                    createAccessLambda((InvokeDynamicInsnNode)instruction, (Handle)result[0], (method.access & ACC_STATIC) != 0, methods);
                }
            }            
        }
    }
    
    
    protected MethodNode createAccessLambda(InvokeDynamicInsnNode dynNode,
                                            Handle                h,
                                            boolean               isStatic,
                                            List<MethodNode>      methods) {
        
        MethodNode accessMethodNode = 
                getAccessMethod(h.getOwner(), h.getName(), h.getDesc(), "L");
            
        if (null != accessMethodNode) {
            return accessMethodNode;
        }

        String name = createAccessMethodName(methods);
        Type[] originalArgTypes = Type.getArgumentTypes(dynNode.desc);
        // Need to check why is it so!
        /*
        Type[] argTypes = isStatic ? originalArgTypes : prependArray(originalArgTypes, Type.getObjectType(classNode.name));
        */
        Type[] argTypes = originalArgTypes; // As isStatic == true always
        Type returnType = Type.getReturnType(dynNode.desc);
        String desc = Type.getMethodDescriptor(returnType, argTypes);

        int publicFlag = (classNode.access & ACC_INTERFACE) != 0 ? ACC_PUBLIC : 0;
        accessMethodNode = new MethodNode(ACC_STATIC + ACC_SYNTHETIC + publicFlag, name, desc, null, null);
        accessMethodNode.visitCode();

        // load all method arguments into stack
        int arity = argTypes.length;
        for (int i = 0; i < arity; i++) {
            int opcode = argTypes[i].getOpcode(ILOAD);
            log.debug("Using opcode " + opcode + " for loading " + argTypes[i]);
            accessMethodNode.visitVarInsn(opcode, i);
        }
        accessMethodNode.visitInvokeDynamicInsn(
            dynNode.name, dynNode.desc, dynNode.bsm, dynNode.bsmArgs
        );
        accessMethodNode.visitInsn(returnType.getOpcode(IRETURN));
        accessMethodNode.visitMaxs(argTypes.length, argTypes.length);
        accessMethodNode.visitEnd();

        // Register mapping
        registerAccessMethod(h.getOwner(), h.getName(), h.getDesc(), "L", accessMethodNode);
        methods.add(accessMethodNode);
        return accessMethodNode;
    }
    
    protected MethodNode createAccessMethod(MethodInsnNode   targetMethodNode, 
                                            boolean          isStatic,
                                            List<MethodNode> methods) {
        
        MethodNode accessMethodNode = 
            getAccessMethod(targetMethodNode.owner, targetMethodNode.name, targetMethodNode.desc, "M");
        
        if (null != accessMethodNode) {
            return accessMethodNode;
        }

        String name = createAccessMethodName(methods);
        Type[] originalArgTypes = Type.getArgumentTypes(targetMethodNode.desc);
        Type[] argTypes = isStatic ? originalArgTypes : prependArray(originalArgTypes, Type.getObjectType(classNode.name));
        Type returnType = Type.getReturnType(targetMethodNode.desc);
        String desc = Type.getMethodDescriptor(returnType, argTypes);

        accessMethodNode = new MethodNode(ACC_STATIC + ACC_SYNTHETIC, name, desc, null, null);
        accessMethodNode.visitCode();

        // load all method arguments into stack
        int arity = argTypes.length;
        for (int i = 0; i < arity; i++) {
            int opcode = argTypes[i].getOpcode(ILOAD);
            log.debug("Using opcode " + opcode + " for loading " + argTypes[i]);
            accessMethodNode.visitVarInsn(opcode, i);
        }
        accessMethodNode.visitMethodInsn(
            isStatic ? INVOKESTATIC : INVOKESPECIAL, targetMethodNode.owner, targetMethodNode.name, targetMethodNode.desc, targetMethodNode.itf
        );
        accessMethodNode.visitInsn(returnType.getOpcode(IRETURN));
        accessMethodNode.visitMaxs(argTypes.length, argTypes.length);
        accessMethodNode.visitEnd();

        // Register mapping
        registerAccessMethod(targetMethodNode.owner, targetMethodNode.name, targetMethodNode.desc, "M", accessMethodNode);
        methods.add(accessMethodNode);
        return accessMethodNode;
    }

    protected MethodNode createAccessGetter(FieldInsnNode    targetFieldNode, 
                                            boolean          isStatic,
                                            List<MethodNode> methods) {
        
        MethodNode accessMethodNode = 
            getAccessMethod(targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc, "G");
        
        if (null != accessMethodNode) {
            return accessMethodNode;
        }

        String name = createAccessMethodName(methods);
        Type[] argTypes = isStatic ? new Type[0] : new Type[] { Type.getObjectType(classNode.name) };
        Type returnType = Type.getType(targetFieldNode.desc);
        String desc = Type.getMethodDescriptor(returnType, argTypes);

        accessMethodNode = new MethodNode(ACC_STATIC + ACC_SYNTHETIC, name, desc, null, null);
        accessMethodNode.visitCode();

        // load all method arguments into stack
        int arity = argTypes.length;
        for (int i = 0; i < arity; i++) {
            int opcode = argTypes[i].getOpcode(ILOAD);
            log.debug("Using opcode " + opcode + " for loading " + argTypes[i]);
            accessMethodNode.visitVarInsn(opcode, i);
        }
        accessMethodNode.visitFieldInsn(
            isStatic ? GETSTATIC : GETFIELD, targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc
        );
        accessMethodNode.visitInsn(returnType.getOpcode(IRETURN));
        accessMethodNode.visitMaxs(1, argTypes.length);
        accessMethodNode.visitEnd();

        // Register mapping
        registerAccessMethod(targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc, "G", accessMethodNode);
        methods.add(accessMethodNode);
        return accessMethodNode;
    }

    protected MethodNode createAccessSetter(FieldInsnNode    targetFieldNode, 
                                            boolean          isStatic,
                                            List<MethodNode> methods) {
        
        MethodNode accessMethodNode = 
            getAccessMethod(targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc, "S");
        
        if (null != accessMethodNode) {
            return accessMethodNode;
        }

        String name = createAccessMethodName(methods);
        Type[] argTypes = isStatic ? 
                new Type[] { Type.getType(targetFieldNode.desc) } : 
                new Type[] { Type.getObjectType(classNode.name), Type.getType(targetFieldNode.desc) };                
                
        Type returnType = Type.VOID_TYPE;
        String desc = Type.getMethodDescriptor(returnType, argTypes);

        accessMethodNode = new MethodNode(ACC_STATIC + ACC_SYNTHETIC, name, desc, null, null);
        accessMethodNode.visitCode();

        // load all method arguments into stack
        int arity = argTypes.length;
        for (int i = 0; i < arity; i++) {
            int opcode = argTypes[i].getOpcode(ILOAD);
            log.debug("Using opcode " + opcode + " for loading " + argTypes[i]);
            accessMethodNode.visitVarInsn(opcode, i);
        }
        accessMethodNode.visitFieldInsn(
            isStatic ? PUTSTATIC : PUTFIELD, targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc
        );
        accessMethodNode.visitInsn(RETURN);
        accessMethodNode.visitMaxs(argTypes.length, argTypes.length);
        accessMethodNode.visitEnd();

        // Register mapping
        registerAccessMethod(targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc, "S", accessMethodNode);
        methods.add(accessMethodNode);
        return accessMethodNode;
    }

    private void registerAccessMethod(String owner, String name, String desc, String kind, MethodNode methodNode) {
        accessMethods.put(owner + name + desc + "-" + kind, methodNode);
    }

    protected MethodNode getAccessMethod(String owner, String name, String desc, String kind) {
        return accessMethods.get(owner + name + desc + "-" + kind);
    }

    protected static int findOriginalArgumentIndex(Type[] arguments, int var, boolean isStaticMethod) {
        int varParamIdx = isStaticMethod ? 0 : 1;
        int arity = arguments.length;
        for (int i = 0; i < arity && varParamIdx <= var; i++) {
          if (varParamIdx == var) {
              return i;
          }
          varParamIdx += arguments[i].getSize();
        }
        return -1;
    }
    
    protected static Type[] prependArray(Type[] array, Type value) {
        Type[] result = new Type[array.length + 1];
        result[0] = value;
        System.arraycopy(array, 0, result, 1, array.length);
        return result;
    }
    
    protected static Type[] appendArray(Type[] array, Type value) {
        Type[] result = new Type[array.length + 1];
        System.arraycopy(array, 0, result, 0, array.length);
        result[result.length - 1] = value;
        return result;
    }
    
}
