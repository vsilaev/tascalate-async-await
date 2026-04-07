/**
 * Copyright 2015-2025 Valery Silaev (http://vsilaev.com)
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

import static net.tascalate.async.tools.core.AnnotationIntrospection.invisibleAnnotationsOf;
import static net.tascalate.async.tools.core.AnnotationIntrospection.invisibleParameterAnnotationsOf;
import static net.tascalate.async.tools.core.AnnotationIntrospection.invisibleTypeAnnotationsOf;
import static net.tascalate.async.tools.core.AnnotationIntrospection.visibleAnnotationsOf;
import static net.tascalate.async.tools.core.AnnotationIntrospection.visibleParameterAnnotationsOf;
import static net.tascalate.async.tools.core.AnnotationIntrospection.visibleTypeAnnotationsOf;
import static net.tascalate.asmx.Opcodes.*;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tascalate.asmx.Handle;
import net.tascalate.asmx.MethodVisitor;
import net.tascalate.asmx.Opcodes;
import net.tascalate.asmx.Type;
import net.tascalate.asmx.tree.AbstractInsnNode;
import net.tascalate.asmx.tree.AnnotationNode;
import net.tascalate.asmx.tree.ClassNode;
import net.tascalate.asmx.tree.FieldInsnNode;
import net.tascalate.asmx.tree.FieldNode;
import net.tascalate.asmx.tree.InnerClassNode;
import net.tascalate.asmx.tree.InvokeDynamicInsnNode;
import net.tascalate.asmx.tree.LabelNode;
import net.tascalate.asmx.tree.LocalVariableAnnotationNode;
import net.tascalate.asmx.tree.LocalVariableNode;
import net.tascalate.asmx.tree.MethodInsnNode;
import net.tascalate.asmx.tree.MethodNode;
import net.tascalate.asmx.tree.TypeAnnotationNode;

abstract public class AbstractAsyncMethodTransformer {
    protected final static Logger log = LoggerFactory.getLogger(AsyncAwaitClassFileGenerator.class);

    static final String ASYNC_ANNOTATION_DESCRIPTOR = Type.getObjectType("net/tascalate/async/async").getDescriptor();
    
    protected final static String CALL_CONTEXT_NAME = "net/tascalate/async/CallContext";
    
    protected final static Type SUSPENDABLE_ANNOTATION_TYPE = Type.getObjectType("net/tascalate/async/suspendable");
    protected final static Type COMPLETION_STAGE_TYPE       = Type.getObjectType("java/util/concurrent/CompletionStage");
    protected final static Type OBJECT_TYPE                 = Type.getType(Object.class);
    protected final static Type ASYNC_METHOD_EXECUTOR_TYPE  = Type.getObjectType("net/tascalate/async/core/AsyncMethodExecutor");
    protected final static Type SCHEDULER_TYPE              = Type.getObjectType("net/tascalate/async/Scheduler");

    private final static Type STRING_TYPE                 = Type.getType(String.class);
    private final static Type CLASS_TYPE                  = Type.getType(Class.class);    
    private final static Type METHOD_HANDLES_TYPE         = Type.getType(MethodHandles.class);
    private final static Type METHOD_HANDLES_LOOKUP_TYPE  = Type.getType(MethodHandles.Lookup.class);
    private final static Type METHOD_DEFINITION_TYPE      = Type.getObjectType("net/tascalate/async/spi/MethodDefinition");
    private final static Type SCHEDULER_PROVIDER_TYPE     = Type.getObjectType("net/tascalate/async/SchedulerProvider");
    private final static Type ABSTRACT_ASYNC_METHOD_TYPE  = Type.getObjectType("net/tascalate/async/core/AbstractAsyncMethod");
    private final static Type TASCALATE_PROMISE_TYPE      = Type.getObjectType("net/tascalate/concurrent/Promise");
    private final static Type TASCALATE_PROMISES_TYPE     = Type.getObjectType("net/tascalate/concurrent/Promises");
    

    private final AsyncAwaitClassState classState;

    protected final ClassNode classNode;
    protected final MethodNode originalAsyncMethod;
    
    protected AbstractAsyncMethodTransformer(ClassNode classNode, MethodNode originalAsyncMethod, AsyncAwaitClassState classState) {
        this.classNode = classNode;
        this.originalAsyncMethod = originalAsyncMethod;
        this.classState = classState;
    }
    
    abstract protected ClassNode transform();
    
    protected ClassNode transform(Type superClassType) {
        if (log.isDebugEnabled()) {
            log.debug("Transforming blocking method: " + classNode.name + "." + originalAsyncMethod.name + 
                      originalAsyncMethod.desc);
        }
        //removeAsyncAnnotation(originalAsyncMethod);
        int classVersion = (classNode.version & 0x0000FFFF);
        boolean createAccessMethods = classVersion < V11 || !classState.supportsNestMemeber(); 
        if (createAccessMethods) {
            // Create accessor methods
            createAccessMethodsForAsyncMethod();            
        } else {
            createAccessMethodsForAsyncMethodLambdas();
        }
        // Create InnerClassNode for anonymous class
        String asyncTaskClassName = classState.generateAndRegisterInnerClassName();
        classNode.innerClasses.add(new InnerClassNode(asyncTaskClassName, null, null, ACC_FINAL));
        
        // Create ClassNode for anonymous class
        ClassNode asyncTaskClassNode = createAnonymousClass(asyncTaskClassName, superClassType);
        if (classVersion > V21) {
            // Otherwise Spring fails
            asyncTaskClassNode.innerClasses = Collections.singletonList(new InnerClassNode(asyncTaskClassName, null, null, ACC_FINAL));
        }
        
        if (!createAccessMethods) {
            if (classNode.nestHostClass == null) {
                asyncTaskClassNode.nestHostClass = classNode.name;
                if (classNode.nestMembers != null) {
                    List<String> nestMembers = new ArrayList<>(classNode.nestMembers);
                    nestMembers.add(asyncTaskClassName);
                    classNode.nestMembers = nestMembers;
                } else {
                    classNode.nestMembers = new ArrayList<>(Collections.singletonList(asyncTaskClassName));
                }
                if (log.isDebugEnabled()) {
                    log.debug(asyncTaskClassName + " DIRECT NEST HOST IS " + asyncTaskClassNode.nestHostClass);
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(asyncTaskClassName + " SKIP INDIRECT NEST HOST of " + classNode.nestHostClass);
                }
                classState.needNestMemeber(classNode.nestHostClass, asyncTaskClassName);
                asyncTaskClassNode.nestHostClass = classNode.nestHostClass;
            }
        }

        // Replace original method
        classState.putMethod((MethodNode)createReplacementAsyncMethod(asyncTaskClassName), false /* Added via visitor */);
        
        //System.out.println(BytecodeTraceUtil.toString(classNode));
        return asyncTaskClassNode;
    }
    
    abstract protected MethodVisitor createReplacementAsyncMethod(String asyncTaskClassName);
    abstract protected MethodVisitor addAnonymousClassRunMethod(ClassNode asyncRunnableClass, FieldNode outerClassField);
    
    private ClassNode createAnonymousClass(String asyncTaskClassName, Type superClassType) {
        boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;

        ClassNode asyncRunnableClass = new ClassNode();

        asyncRunnableClass.visit(classNode.version, ACC_FINAL + ACC_SUPER + ACC_SYNTHETIC, asyncTaskClassName, null, superClassType.getInternalName(), null);
        asyncRunnableClass.visitSource(classNode.sourceFile, null);
        asyncRunnableClass.visitOuterClass(classNode.name, originalAsyncMethod.name, originalAsyncMethod.desc);

        // SerialVersionUID
        asyncRunnableClass.visitField(
            ACC_PRIVATE + ACC_FINAL + ACC_STATIC, 
            "serialVersionUID", 
            "J", 
            null,
            Long.valueOf(1L)
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
        addAnonymousClassMetadata(asyncRunnableClass);
        addAnonymousClassConstructor(asyncRunnableClass, superClassType, outerClassField);
        addAnonymousClassRunMethod(asyncRunnableClass, outerClassField);
        addAnonymousClassToStringMethod(asyncRunnableClass, superClassType);
        return asyncRunnableClass;
    }
    
    private MethodVisitor addAnonymousClassConstructor(ClassNode asyncRunnableClass, Type superClassType, FieldNode outerClassField) {
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
        result.visitMaxs(hasDWordParam ? 3 : 2 /* stack */, paramVarIdx + 1 /* locals */);
        result.visitEnd();

        return result;        
    }

    private MethodVisitor addAnonymousClassToStringMethod(ClassNode asyncRunnableClass, Type runnableBaseClass) {
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
    
    private MethodVisitor addAnonymousClassMetadata(ClassNode asyncRunnableClass) {
        asyncRunnableClass.visitField(Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                                      "__METHOD_DEFINITION",
                                      METHOD_DEFINITION_TYPE.getDescriptor(), // descriptor
                                      METHOD_DEFINITION_TYPE.getDescriptor(), // generic signature (optional)
                                      null /* no compile-time constant */);
        MethodVisitor result = asyncRunnableClass.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        // 1) push the String name
        result.visitLdcInsn(originalAsyncMethod.name);
        // 2) push the return type as a Class literal, e.g. java.lang.String.class
        pushClassLiteral(result, Type.getReturnType(originalAsyncMethod.desc));

        // 3) build Class[] for varargs: new Class[n]; fill elements
        Type[] argTypes = Type.getArgumentTypes(originalAsyncMethod.desc);
        int n = argTypes.length;

        // push array length
        // push array length
        switch (n) {
            case 0: result.visitInsn(Opcodes.ICONST_0); break;
            case 1: result.visitInsn(Opcodes.ICONST_1); break;
            case 2: result.visitInsn(Opcodes.ICONST_2); break;
            case 3: result.visitInsn(Opcodes.ICONST_3); break;
            case 4: result.visitInsn(Opcodes.ICONST_4); break;
            case 5: result.visitInsn(Opcodes.ICONST_5); break;
            default:
                if (n <= Short.MAX_VALUE) {
                    result.visitIntInsn(Opcodes.SIPUSH, n);
                } else {
                    result.visitLdcInsn(n);
                }
        }

        // create new array of java.lang.Class
        result.visitTypeInsn(Opcodes.ANEWARRAY, CLASS_TYPE.getInternalName());

        // fill array: for each index do DUP, push index, push class literal, AASTORE
        for (int i = 0; i < n; i++) {
            result.visitInsn(Opcodes.DUP);

            // push index
            switch (i) {
                case 0: result.visitInsn(Opcodes.ICONST_0); break;
                case 1: result.visitInsn(Opcodes.ICONST_1); break;
                case 2: result.visitInsn(Opcodes.ICONST_2); break;
                case 3: result.visitInsn(Opcodes.ICONST_3); break;
                case 4: result.visitInsn(Opcodes.ICONST_4); break;
                case 5: result.visitInsn(Opcodes.ICONST_5); break;
                default:
                    if (i <= Short.MAX_VALUE) {
                        result.visitIntInsn(Opcodes.SIPUSH, i);
                    } else {
                        result.visitLdcInsn(i);
                    }
            }

            // push class literal for argTypes[i]
            pushClassLiteral(result, argTypes[i]);
            // store into array
            result.visitInsn(Opcodes.AASTORE);
        }
        
        result.visitMethodInsn(
            Opcodes.INVOKESTATIC, METHOD_DEFINITION_TYPE.getInternalName(), "create", 
            "(Ljava/lang/String;Ljava/lang/Class;[Ljava/lang/Class;)" + METHOD_DEFINITION_TYPE.getDescriptor(), false
        );
        result.visitFieldInsn(
            Opcodes.PUTSTATIC, asyncRunnableClass.name, "__METHOD_DEFINITION", METHOD_DEFINITION_TYPE.getDescriptor()
        );
        result.visitInsn(RETURN);
        result.visitMaxs(3, 0);
        return result;
    }
    
    protected MethodVisitor createReplacementAsyncMethod(String asyncTaskClassName, Type runnableBaseClass, String runnableFieldName, Type runnableFieldType) {
        boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;
        int thisArgShift = isStatic ? 0 : 1;
        Type[] originalArgTypes = Type.getArgumentTypes(originalAsyncMethod.desc);
        int originalArity = originalArgTypes.length;

        MethodVisitor result = classNode.visitMethod( 
            originalAsyncMethod.access, originalAsyncMethod.name, 
            originalAsyncMethod.desc, originalAsyncMethod.signature, 
            originalAsyncMethod.exceptions == null ? null : originalAsyncMethod.exceptions.toArray(new String[originalAsyncMethod.exceptions.size()])
        );
        if (null != originalAsyncMethod.attrs) {
            originalAsyncMethod.attrs.forEach(result::visitAttribute);
        }
        
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
        // result.visitLdcInsn(Type.getObjectType(classNode.name));
        result.visitMethodInsn(
            INVOKESTATIC, METHOD_HANDLES_TYPE.getInternalName(), "lookup", 
            Type.getMethodDescriptor(METHOD_HANDLES_LOOKUP_TYPE), false
        );
        result.visitFieldInsn(Opcodes.GETSTATIC, asyncTaskClassName, "__METHOD_DEFINITION", METHOD_DEFINITION_TYPE.getDescriptor());
        result.visitMethodInsn(
            INVOKESTATIC, ASYNC_METHOD_EXECUTOR_TYPE.getInternalName(), "currentScheduler", 
            Type.getMethodDescriptor(SCHEDULER_TYPE, SCHEDULER_TYPE, OBJECT_TYPE, METHOD_HANDLES_LOOKUP_TYPE, METHOD_DEFINITION_TYPE), false
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
            Math.max(4 /*to resolve scheduler*/, methodVarIdx + 2), // for constructor call (incl. scheduler + DUP)
            methodVarIdx + 1 // params count + outer this (for non static) + resolved scheduler + methodRunnable
        );

        result.visitEnd();
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
                    MethodNode targetMethodNode = classState.getMethod(method.getName(), method.getDesc());
                    if (null != targetMethodNode && (targetMethodNode.access & (/*ACC_STATIC + ACC_PRIVATE +*/ ACC_SYNTHETIC)) == /*ACC_STATIC + ACC_PRIVATE + */ACC_SYNTHETIC) {
                        return new Object[] {method, targetMethodNode};
                    }
                }
            }
        }
        return null;
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
    
    protected static List<TypeAnnotationNode> copyTypeAnnotations(List<TypeAnnotationNode> originalAnnotations) {
        if (null == originalAnnotations || originalAnnotations.isEmpty()) {
            return null;
        }
        return originalAnnotations
            .stream()
            .map(t -> new TypeAnnotationNode(t.typeRef, t.typePath, t.desc))
            .collect(Collectors.toCollection(ArrayList::new));
    }
    
    protected List<LocalVariableNode> copyRealVarsToRunMethod(ClassNode asyncRunnableClass, Map<Integer, Integer> oldToNewVarIndexes, 
                                                              Map<LabelNode, LabelNode> labelsMap, LabelNode start, LabelNode end) {
        List<LocalVariableNode> allCloned = new ArrayList<>();
        LocalVariableNode self = new LocalVariableNode("this", Type.getObjectType(asyncRunnableClass.name).getDescriptor(), null, start, end, 0);
        allCloned.add(self);

        if (originalAsyncMethod.localVariables != null && !originalAsyncMethod.localVariables.isEmpty()) {
            for (LocalVariableNode original : originalAsyncMethod.localVariables) {
                Integer newIndex = oldToNewVarIndexes.get(original.index);
                if (null == newIndex) {
                    continue;
                }
                LocalVariableNode cloned = new LocalVariableNode(
                    original.name, original.desc, original.signature,
                    labelsMap.get(original.start), labelsMap.get(original.end),
                    newIndex
                );
                allCloned.add(cloned);
            }
        }
        

        return allCloned;
    }
    
    protected static List<LocalVariableAnnotationNode> copyRealVarsAnnotations(List<LocalVariableAnnotationNode> annotations, Map<Integer, Integer> oldToNewVarIndexes, 
                                                                               Map<LabelNode, LabelNode> labelsMap) {
        if (null == annotations || annotations.isEmpty()) {
            return null;
        }
        
        List<LocalVariableAnnotationNode> result = new ArrayList<>();
        
        List<LabelNode> newStartLabels = new ArrayList<>();
        List<LabelNode> newEndLabels = new ArrayList<>();
        List<Integer> newIndicies = new ArrayList<>();
        for (LocalVariableAnnotationNode original : annotations) {
            for (Integer originalIndex : original.index) {
                Integer newIndex = oldToNewVarIndexes.get(originalIndex);
                if (null == newIndex) {
                    continue;
                }
                LabelNode newStartLabel = labelsMap.get(original.start.get(originalIndex));
                LabelNode newEndLabel = labelsMap.get(original.start.get(originalIndex));
                newStartLabels.add(newStartLabel);
                newEndLabels.add(newEndLabel);
                newIndicies.add(newIndex);
            }
            LocalVariableAnnotationNode cloned = new LocalVariableAnnotationNode(
                original.typeRef, original.typePath, 
                newStartLabels.toArray(new LabelNode[newStartLabels.size()]), newEndLabels.toArray(new LabelNode[newEndLabels.size()]), 
                newIndicies.stream().mapToInt(Integer::intValue).toArray(), original.desc);
            cloned.values = original.values;
            result.add(cloned);
        }
        return result;
    }
    
    private int schedulerProviderParamIdx(MethodNode methodNode) {
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
    
    private void createAccessMethodsForAsyncMethod() {
        List<MethodNode> methods = classNode.methods;
        Set<String> processedMethods = new HashSet<>();
        Set<String> processedFields = new HashSet<>();
        for (Iterator<?> i = originalAsyncMethod.instructions.iterator(); i.hasNext();) {
            AbstractInsnNode instruction = (AbstractInsnNode) i.next();
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode min = (MethodInsnNode) instruction;
                
                String key = min.owner + '#' + min.name + '#' + min.desc;
                if (!processedMethods.add(key)) {
                    continue;
                }
                
                boolean isOwnMethod = min.owner.equals(classNode.name);
                int mopcode = min.getOpcode();
                if (
                    (mopcode == INVOKEVIRTUAL || mopcode == INVOKESPECIAL || mopcode == INVOKESTATIC) && 
                    (isOwnMethod || classState.isSubClassOf(min.owner))) {
                    
                    String actualClassName;
                    // Even it's reported as an own method in insn, it may be declared in superclass
                    MethodNode targetMethodNode = isOwnMethod ? classState.getMethod(min.name, min.desc) : null;
                    
                    if (null == targetMethodNode) {
                        targetMethodNode = findSuperclassMethod(classNode.superName, min);
                        actualClassName = targetMethodNode != null ? targetMethodNode.signature : null; // Some superclass
                        isOwnMethod = false;
                    } else {
                        // this class
                        actualClassName = classNode.name;
                    }

                    boolean samePackageAccessible = 
                        // Note that INVOKESPECIAL IS NOT ACCESSIBLE OTSIDE CLASS ITSELF AT ALL
                        (mopcode == INVOKEVIRTUAL || mopcode == INVOKESTATIC || (mopcode == INVOKESPECIAL && "<init>".equals(min.name))) &&
                        null != targetMethodNode &&
                        (targetMethodNode.access & ACC_PRIVATE) == 0 &&
                        ((targetMethodNode.access & ACC_PUBLIC) != 0 || samePackage(classNode.name, actualClassName));        
                    
                    if (!samePackageAccessible) {
                        if (log.isTraceEnabled()) {
                            log.trace("Found private call " + BytecodeTraceUtil.toString(min));
                        }
                        // TODO Add special handling for private constructor calls
                        createAccessMethod(min);
                    }
                }
            }
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode fin = (FieldInsnNode) instruction;
                
                String key = fin.owner + '#' + fin.name + "#" + fin.desc;
                if (!processedFields.add(key)) {
                    continue;
                }
                
                boolean isOwnField = fin.owner.equals(classNode.name);
                if (isOwnField || classState.isSubClassOf(fin.owner)) {
                    String actualClassName;
                    FieldNode targetFieldNode = isOwnField ? classState.getField(fin.name, fin.desc) : null;
                    if (null == targetFieldNode) {
                        targetFieldNode = findSuperclassField(classNode.superName, fin);
                        actualClassName = null != targetFieldNode ? targetFieldNode.signature : null;
                        isOwnField = false;
                    } else {
                        actualClassName = classNode.name;
                    }
                    
                    boolean samePackageAccessible = 
                        null != targetFieldNode &&
                        (targetFieldNode.access & ACC_PRIVATE) == 0 &&
                        ((targetFieldNode.access & ACC_PUBLIC) != 0 || samePackage(classNode.name, actualClassName));
                    
                    if (!samePackageAccessible) {
                        if (log.isTraceEnabled()) {
                            log.trace("Found private field access " + BytecodeTraceUtil.toString(fin));
                        }
                        
                        if (fin.getOpcode() == GETSTATIC || 
                            fin.getOpcode() == GETFIELD) {
                            
                            createAccessGetter(fin);
                            
                        } else if (fin.getOpcode() == PUTSTATIC || 
                                   fin.getOpcode() == PUTFIELD) {
                            
                            createAccessSetter(fin);
                        }
                    }
                }
            }
            if (instruction instanceof InvokeDynamicInsnNode) {
                Object[] result = findOwnerInvokeDynamic(instruction, methods);
                if (null != result) {
                    createAccessLambda((InvokeDynamicInsnNode)instruction, (Handle)result[0], true);
                }
            }            
        }
    }
    
    private void createAccessMethodsForAsyncMethodLambdas() {
        List<MethodNode> methods = classNode.methods;
        for (Iterator<?> i = originalAsyncMethod.instructions.iterator(); i.hasNext();) {
            AbstractInsnNode instruction = (AbstractInsnNode) i.next();
            if (instruction instanceof InvokeDynamicInsnNode) {
                Object[] result = findOwnerInvokeDynamic(instruction, methods);
                if (null != result) {
                    createAccessLambda((InvokeDynamicInsnNode)instruction, (Handle)result[0], true);
                }
            }            
        }
    }
    
    private MethodNode createAccessLambda(InvokeDynamicInsnNode dynNode, Handle h, boolean isStatic) {
        MethodNode accessMethodNode = 
                getAccessMethod(h.getOwner(), h.getName(), h.getDesc(), "L");
            
        if (null != accessMethodNode) {
            return accessMethodNode;
        }

        String name = classState.createAccessMethodName();
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
        accessMethodNode.visitAnnotation(SUSPENDABLE_ANNOTATION_TYPE.getDescriptor(), true).visitEnd();
        accessMethodNode.visitCode();

        // load all method arguments into stack
        int paramVarIdx = 0;
        int arity = argTypes.length;
        for (int i = 0; i < arity; i++) {
            Type argType = argTypes[i];
            int opcode = argType.getOpcode(ILOAD);
            if (log.isTraceEnabled()) {
                log.trace("Using opcode " + opcode + " for loading " + argType);
            }
            accessMethodNode.visitVarInsn(opcode, paramVarIdx);
            paramVarIdx += argType.getSize();
        }
        accessMethodNode.visitInvokeDynamicInsn(
            dynNode.name, dynNode.desc, dynNode.bsm, dynNode.bsmArgs
        );
        accessMethodNode.visitInsn(returnType.getOpcode(IRETURN));
        accessMethodNode.visitMaxs(Math.max(paramVarIdx, returnType.getSize()), paramVarIdx);
        accessMethodNode.visitEnd();

        // Register mapping
        registerAccessMethod(h.getOwner(), h.getName(), h.getDesc(), "L", accessMethodNode);
        return accessMethodNode;
    }
    
    private MethodNode createAccessMethod(MethodInsnNode targetMethodNode) {
        
        MethodNode accessMethodNode = 
            getAccessMethod(targetMethodNode.owner, targetMethodNode.name, targetMethodNode.desc, "M");
        
        if (null != accessMethodNode) {
            return accessMethodNode;
        }

        String name = classState.createAccessMethodName();
        Type[] originalArgTypes = Type.getArgumentTypes(targetMethodNode.desc);
        Type[] argTypes = 
            targetMethodNode.getOpcode() == INVOKESTATIC ? 
            originalArgTypes 
            : 
            prependArray(originalArgTypes, Type.getObjectType(classNode.name));
        
        Type returnType = Type.getReturnType(targetMethodNode.desc);
        String desc = Type.getMethodDescriptor(returnType, argTypes);
        
        int publicFlag = (classNode.access & ACC_INTERFACE) != 0 ? ACC_PUBLIC : 0;

        accessMethodNode = new MethodNode(ACC_STATIC + ACC_SYNTHETIC + publicFlag, name, desc, null, null);
        accessMethodNode.visitAnnotation(SUSPENDABLE_ANNOTATION_TYPE.getDescriptor(), true).visitEnd();
        accessMethodNode.visitCode();

        // load all method arguments into stack
        int paramVarIdx = 0;
        int arity = argTypes.length;
        for (int i = 0; i < arity; i++) {
            Type argType = argTypes[i];
            int opcode = argType.getOpcode(ILOAD);
            if (log.isTraceEnabled()) {
                log.trace("Using opcode " + opcode + " for loading " + argType);
            }
            accessMethodNode.visitVarInsn(opcode, paramVarIdx);
            paramVarIdx += argType.getSize();
        }
        accessMethodNode.visitMethodInsn(
            targetMethodNode.getOpcode(), targetMethodNode.owner, targetMethodNode.name, targetMethodNode.desc, targetMethodNode.itf
        );
        accessMethodNode.visitInsn(returnType.getOpcode(IRETURN));
        accessMethodNode.visitMaxs(Math.max(paramVarIdx, returnType.getSize()), paramVarIdx);
        accessMethodNode.visitEnd();

        // Register mapping
        registerAccessMethod(targetMethodNode.owner, targetMethodNode.name, targetMethodNode.desc, "M", accessMethodNode);
        return accessMethodNode;
    }

    private MethodNode createAccessGetter(FieldInsnNode targetFieldNode) {
        
        MethodNode accessMethodNode = 
            getAccessMethod(targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc, "G");
        
        if (null != accessMethodNode) {
            return accessMethodNode;
        }

        String name = classState.createAccessMethodName();
        Type returnType = Type.getType(targetFieldNode.desc);
        if (targetFieldNode.getOpcode() == GETSTATIC) {
            String desc = Type.getMethodDescriptor(returnType);   
            accessMethodNode = new MethodNode(ACC_STATIC + ACC_SYNTHETIC, name, desc, null, null);
            accessMethodNode.visitCode();
            accessMethodNode.visitFieldInsn(GETSTATIC, targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc);
            accessMethodNode.visitInsn(returnType.getOpcode(IRETURN));
            accessMethodNode.visitMaxs(1, 0);
        } else {
            if (targetFieldNode.getOpcode() != GETFIELD) {
                throw new IllegalArgumentException("Unexpected opcode, should be either GETSTATIC / GETFILED: " + targetFieldNode.getOpcode());
            }            
            Type objectType = Type.getObjectType(classNode.name);
            String desc = Type.getMethodDescriptor(returnType, objectType);   
            accessMethodNode = new MethodNode(ACC_STATIC + ACC_SYNTHETIC, name, desc, null, null);
            accessMethodNode.visitCode();
            accessMethodNode.visitVarInsn(objectType.getOpcode(ILOAD), 0);
            accessMethodNode.visitFieldInsn(GETFIELD, targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc);
            accessMethodNode.visitInsn(returnType.getOpcode(IRETURN));
            accessMethodNode.visitMaxs(1, 1);
        }
        accessMethodNode.visitEnd();
        // Register mapping
        registerAccessMethod(targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc, "G", accessMethodNode);
        return accessMethodNode;
    }

    private MethodNode createAccessSetter(FieldInsnNode targetFieldNode) {
        
        MethodNode accessMethodNode = 
            getAccessMethod(targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc, "S");
        
        if (null != accessMethodNode) {
            return accessMethodNode;
        }

        String name = classState.createAccessMethodName();
        Type fieldType = Type.getType(targetFieldNode.desc); 
        if (targetFieldNode.getOpcode() == PUTSTATIC) {
            String desc = Type.getMethodDescriptor(Type.VOID_TYPE, fieldType);
            accessMethodNode = new MethodNode(ACC_STATIC + ACC_SYNTHETIC, name, desc, null, null);
            accessMethodNode.visitCode();
            accessMethodNode.visitVarInsn(fieldType.getOpcode(ILOAD), 0);
            accessMethodNode.visitFieldInsn(PUTSTATIC, targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc);
            accessMethodNode.visitInsn(RETURN);     
            accessMethodNode.visitMaxs(1, 1);
            
        } else {
            if (targetFieldNode.getOpcode() != PUTFIELD) {
                throw new IllegalArgumentException("Unexpected opcode, should be either PUTSTATIC / PUTFILED: " + targetFieldNode.getOpcode());
            }
            Type objectType = Type.getObjectType(classNode.name);
            String desc = Type.getMethodDescriptor(Type.VOID_TYPE, objectType, fieldType);
            accessMethodNode = new MethodNode(ACC_STATIC + ACC_SYNTHETIC, name, desc, null, null);
            accessMethodNode.visitCode();
            accessMethodNode.visitVarInsn(objectType.getOpcode(ILOAD), 0);
            accessMethodNode.visitVarInsn(fieldType.getOpcode(ILOAD), 1);
            accessMethodNode.visitFieldInsn(PUTFIELD, targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc);
            accessMethodNode.visitInsn(RETURN);
            accessMethodNode.visitMaxs(1 + fieldType.getSize(), 1 + fieldType.getSize());
        }
        accessMethodNode.visitEnd();

        // Register mapping
        registerAccessMethod(targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc, "S", accessMethodNode);
        return accessMethodNode;
    }
    
    private void registerAccessMethod(String owner, String name, String desc, String kind, MethodNode methodNode) {
        classState.registerAccessMethod(owner, name, desc, kind, methodNode);
    }

    protected MethodNode getAccessMethod(String owner, String name, String desc, String kind) {
        return classState.getAccessMethod(owner, name, desc, kind);
    }
    
    private MethodNode findSuperclassMethod(String className, MethodInsnNode methodInstructionNode) {
        AsyncAwaitClassState superclassState = classState.superclass(className);
        MethodNode result = superclassState.getMethod(methodInstructionNode.name, methodInstructionNode.desc);
        if (null != result) {
            result.signature = superclassState.classNode.name;
            return result;
        } else {
            String superClassName = superclassState.classNode.superName;
            if (null == superClassName) {
                return null;
            } else {
                return findSuperclassMethod(superClassName, methodInstructionNode);
            }
        }
    }
    
    private FieldNode findSuperclassField(String className, FieldInsnNode fieldInstructionNode) {
        AsyncAwaitClassState superclassState = classState.superclass(className);
        FieldNode result = superclassState.getField(fieldInstructionNode.name, fieldInstructionNode.desc);
        if (null != result) {
            result.signature = superclassState.classNode.name;
            return result;
        } else {
            String superClassName = superclassState.classNode.superName;
            if (null == superClassName) {
                return null;
            } else {
                return findSuperclassField(superClassName, fieldInstructionNode);
            }
        }
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

    protected static boolean isLoadOpcode(int opcode) {
        return opcode >= ILOAD && opcode < ISTORE;
    }
    
    protected static String createOuterClassMethodArgFieldName(int index) {
        return "val$" + index;
    }

    private static boolean samePackage(String classA, String classB) {
        return Objects.equals(packageNameOf(classA), packageNameOf(classB));
    }
    
    private static String packageNameOf(String clazz) {
        int ind = null == clazz ? -1 : clazz.lastIndexOf('/');
        return ind > 0 ? clazz.substring(0, ind) : "";
    }
    
    private static void pushClassLiteral(MethodVisitor mv, Type t) {
        if (t.getSort() == Type.VOID) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Void", "TYPE", "Ljava/lang/Class;");
        } else if (t.getSort() == Type.BOOLEAN) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;");
        } else if (t.getSort() == Type.CHAR) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Character", "TYPE", "Ljava/lang/Class;");
        } else if (t.getSort() == Type.BYTE) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Byte", "TYPE", "Ljava/lang/Class;");
        } else if (t.getSort() == Type.SHORT) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Short", "TYPE", "Ljava/lang/Class;");
        } else if (t.getSort() == Type.INT) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
        } else if (t.getSort() == Type.LONG) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;");
        } else if (t.getSort() == Type.FLOAT) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Float", "TYPE", "Ljava/lang/Class;");
        } else if (t.getSort() == Type.DOUBLE) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Double", "TYPE", "Ljava/lang/Class;");
        } else {
            mv.visitLdcInsn(t);
        }
    }
    
    private static String getMethodSignature(MethodNode methodNode, boolean outputExceptions) {
        StringBuilder result = new StringBuilder();
        int access = methodNode.access;
        if ((access & ACC_PUBLIC) != 0) result.append("public ");
        if ((access & ACC_PROTECTED) != 0) result.append("protected ");
        if ((access & ACC_PRIVATE) != 0) result.append("private ");
        if ((access & ACC_ABSTRACT) != 0) result.append("abstract ");
        if ((access & ACC_FINAL) != 0) result.append("final ");
        if ((access & ACC_STATIC) != 0) result.append("static ");
        if ((access & ACC_STRICT) != 0) result.append("strictfp ");
        if ((access & ACC_SYNCHRONIZED) != 0) result.append("synchronized ");
        result.append(Type.getReturnType(methodNode.desc).getClassName()).append(' ');
        result.append(methodNode.name);
        result.append('(');
        result.append(
            Arrays.stream( Type.getArgumentTypes(methodNode.desc) )
                .map(t -> t.getClassName())
                .collect(Collectors.joining(", "))
        );
        result.append(')');
        if (outputExceptions && null != methodNode.exceptions && !methodNode.exceptions.isEmpty()) {
            result.append(" throws ");
            List<String> exceptions = (List<String>)methodNode.exceptions;
            result.append(
                exceptions.stream()
                    .map(v -> v.toString().replace('/', '.'))
                    .collect(Collectors.joining(", "))
            );
        }
        return result.toString();
    }
}
