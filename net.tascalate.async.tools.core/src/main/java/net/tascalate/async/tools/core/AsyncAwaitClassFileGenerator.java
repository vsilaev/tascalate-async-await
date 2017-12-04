
package net.tascalate.async.tools.core;

import static net.tascalate.async.tools.core.BytecodeIntrospection.*;
import static org.objectweb.asm.Opcodes.*;

import java.lang.instrument.IllegalClassFormatException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.javaflow.spi.ResourceLoader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

public class AsyncAwaitClassFileGenerator {

    private final static Log log = LogFactory.getLog(AsyncAwaitClassFileGenerator.class);

    private final static String ASYNC_TASK_NAME = "net/tascalate/async/core/AsyncTask";

    private final static String ASYNC_CALL_NAME = "net/tascalate/async/api/AsyncCall";
    private final static String ASYNC_EXECUTOR_NAME = "net/tascalate/async/core/AsyncExecutor";

    private final static String COMPLETION_STAGE_DESCRIPTOR = "Ljava/util/concurrent/CompletionStage;";

    private final static String CONTINUABLE_ANNOTATION_DESCRIPTOR = "Lorg/apache/commons/javaflow/api/continuable;";

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
        List<InnerClassNode> originalInnerClasses = new ArrayList<InnerClassNode>(innerClassesOf(classNode));
        for (MethodNode methodNode : new ArrayList<MethodNode>(methodsOf(classNode))) {
            if (isAsyncMethod(methodNode)) {
                transform(classNode, originalInnerClasses, methodNode);
                transformed = true;
            }
        }
        return transformed;
    }

    protected void transform(ClassNode            classNode, 
                             List<InnerClassNode> originalInnerClasses, 
                             MethodNode           originalAsyncMethodNode) {

        log.info("Transforming blocking method: " + classNode.name + "." + originalAsyncMethodNode.name
                + originalAsyncMethodNode.desc);
        // Remove @async annotation
        removeAsyncAnnotation(originalAsyncMethodNode);

        // Create InnerClassNode for anoymous class
        String asyncTaskClassName = createInnerClassName(classNode);
        innerClassesOf(classNode).add(new InnerClassNode(asyncTaskClassName, null, null, 0));

        // Create accessor methods
        createAccessMethodsForAsyncMethod(classNode, originalAsyncMethodNode);

        // Create ClassNode for anonymous class
        ClassNode asyncTaskClassNode = createAnonymousClass(
            classNode, originalInnerClasses, originalAsyncMethodNode, asyncTaskClassName
        );
        newClasses.add(asyncTaskClassNode);

        // Replace original method

        MethodNode replacementAsyncMethodNode = createReplacementAsyncMethod(
            classNode, originalAsyncMethodNode, asyncTaskClassName
        );
        
        List<MethodNode> methods = methodsOf(classNode);
        methods.set(methods.indexOf(originalAsyncMethodNode), replacementAsyncMethodNode);

    }

    protected ClassNode createAnonymousClass(ClassNode            originalOuterClass, 
                                             List<InnerClassNode> originalInnerClasses,
                                             MethodNode           originalAsyncMethod, 
                                             String               asyncClassName) {
        
        boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;

        ClassNode asyncRunnableClass = new ClassNode();

        asyncRunnableClass.visit(originalOuterClass.version, ACC_SUPER, asyncClassName, null, ASYNC_TASK_NAME, null);
        asyncRunnableClass.visitSource(originalOuterClass.sourceFile, null);
        asyncRunnableClass.visitOuterClass(originalOuterClass.name, originalAsyncMethod.name, originalAsyncMethod.desc);

        // Copy outer class inner classes
        List<InnerClassNode> asyncClassInnerClasses = innerClassesOf(asyncRunnableClass);
        for (InnerClassNode innerClassNode : originalInnerClasses) {
            asyncClassInnerClasses.add(innerClassNode);
        }

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
                "L" + originalOuterClass.name + ";", 
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
                asyncRunnableClass.visitField(ACC_PRIVATE + ACC_FINAL + ACC_SYNTHETIC, argName, argDesc, null, null);
            }
        }

        addAnonymousClassConstructor(originalOuterClass, originalAsyncMethod, asyncRunnableClass, outerClassField);
        addAnonymousClassRunMethod(originalOuterClass, originalAsyncMethod, asyncRunnableClass, outerClassField);
        return asyncRunnableClass;
    }

    protected MethodNode addAnonymousClassConstructor(ClassNode  originalOuterClass, 
                                                      MethodNode originalAsyncMethod,
                                                      ClassNode  asyncRunnableClass,
                                                      FieldNode  outerClassField) {

        boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;
        // Original methods arguments
        Type[] argTypes = Type.getArgumentTypes(originalAsyncMethod.desc);
        int originalArity = argTypes.length;

        String constructorDesc = Type.getMethodDescriptor(
            Type.VOID_TYPE,
            isStatic ? argTypes : prependArray(argTypes, Type.getObjectType(originalOuterClass.name))
        );

        MethodVisitor mv = asyncRunnableClass.visitMethod(0, "<init>", constructorDesc, null, null);
        mv.visitCode();

        if (!isStatic) {
            // Store outer class instance
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(PUTFIELD, asyncRunnableClass.name, outerClassField.name, outerClassField.desc);
        }

        // Store original method's arguments
        for (int i = 0; i < originalArity; i++) {
            String argName = createOuterClassMethodArgFieldName(i);
            String argDesc = argTypes[i].getDescriptor();

            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(argTypes[i].getOpcode(ILOAD), i + (isStatic ? 1 : 2));
            mv.visitFieldInsn(PUTFIELD, asyncRunnableClass.name, argName, argDesc);
        }

        // Invoke super()
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, ASYNC_TASK_NAME, "<init>", "()V", false);

        mv.visitInsn(RETURN);
        mv.visitMaxs(1 + (originalArity > 0 || !isStatic ? 1 : 0), originalArity + (isStatic ? 1 : 2));
        mv.visitEnd();

        return (MethodNode) mv;
    }

    protected MethodNode addAnonymousClassRunMethod(ClassNode  originalOuterClass, 
                                                    MethodNode originalAsyncMethod,
                                                    ClassNode  asyncRunnableClass,
                                                    FieldNode outerClassField) {

        boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;
        int thisWasInOriginal = isStatic ? 0 : 1;
        int thisShiftNecessary = isStatic ? 1 : 0;

        Type[] argTypes = Type.getArgumentTypes(originalAsyncMethod.desc);
        log.debug("Method has " + argTypes.length + " arguments");

        MethodNode asyncRunMethod = (MethodNode)asyncRunnableClass.visitMethod(ACC_PUBLIC, "run", "()V", null, null);

        asyncRunMethod.visitAnnotation(CONTINUABLE_ANNOTATION_DESCRIPTOR, true);

        // Local variables
        // amn.localVariables = methodNode.localVariables;

        LabelNode methodStart = new LabelNode();
        LabelNode globalCatchEnd = new LabelNode();
        LabelNode globalCatchHandler = new LabelNode();
        LabelNode methodEnd = new LabelNode();

        Map<LabelNode, LabelNode> labelsMap = new IdentityHashMap<>();
        for (AbstractInsnNode l = originalAsyncMethod.instructions.getFirst(); l != null; l = l.getNext()) {
            if (!(l instanceof LabelNode))
                continue;
            labelsMap.put((LabelNode) l, new LabelNode());
        }

        @SuppressWarnings("unchecked")
        List<TryCatchBlockNode> tryCatchBlocks = asyncRunMethod.tryCatchBlocks;
        // Try-catch blocks
        for (Iterator<?> it = originalAsyncMethod.tryCatchBlocks.iterator(); it.hasNext();) {
            TryCatchBlockNode tn = (TryCatchBlockNode) it.next();
            tryCatchBlocks.add(new TryCatchBlockNode(labelsMap.get(tn.start), labelsMap.get(tn.end),
                    labelsMap.get(tn.handler), tn.type));
        }
        // Should be the latest -- surrounding try-catch-all
        tryCatchBlocks.add(
            new TryCatchBlockNode(methodStart, globalCatchEnd, globalCatchHandler, "java/lang/Throwable")
        );

        InsnList newInstructions = new InsnList();
        newInstructions.add(methodStart);

        // Instructions
        for (AbstractInsnNode insn = originalAsyncMethod.instructions.getFirst(); null != insn; insn = insn.getNext()) {
            if (insn instanceof VarInsnNode) {
                VarInsnNode vin = (VarInsnNode) insn;
                // "this" -> outer class "this"
                if (!isStatic && vin.getOpcode() == ALOAD && vin.var == 0) {
                    log.debug("Found " + BytecodeTraceUtil.toString(vin));

                    newInstructions.add(new VarInsnNode(ALOAD, 0));
                    newInstructions.add(new FieldInsnNode(GETFIELD, asyncRunnableClass.name, outerClassField.name, outerClassField.desc));
                    continue;
                }

                // original method had arguments
                if (vin.getOpcode() != RET && (vin.var > 0 || isStatic)) {
                    log.debug("Found " + BytecodeTraceUtil.toString(vin));
                    // method argument -> inner class field
                    if (vin.var < argTypes.length + thisWasInOriginal) {
                        int i = vin.var - thisWasInOriginal; // method
                                                             // argument's index

                        String argName = createOuterClassMethodArgFieldName(i);
                        String argDesc = Type.getMethodDescriptor(argTypes[i], new Type[0]).substring(2);

                        newInstructions.add(new VarInsnNode(ALOAD, 0));
                        if (isLoadOpcode(vin.getOpcode())) {
                            assert (argTypes[i].getOpcode(ILOAD) == vin.getOpcode()) : 
                                "Wrong opcode " + vin.getOpcode() + ", expected " + argTypes[i].getOpcode(ILOAD);

                            newInstructions.add(new FieldInsnNode(GETFIELD, asyncRunnableClass.name, argName, argDesc));
                        } else {
                            assert (argTypes[i].getOpcode(ISTORE) == vin.getOpcode()) : 
                                "Wrong opcode " + vin.getOpcode() + ", expected " + argTypes[i].getOpcode(ISTORE);

                            newInstructions.add(new InsnNode(SWAP));
                            newInstructions.add(new FieldInsnNode(PUTFIELD, asyncRunnableClass.name, argName, argDesc));
                        }
                        continue;
                    }
                    // decrease local variable indexes
                    else {
                        newInstructions.add(new VarInsnNode(vin.getOpcode(), vin.var - argTypes.length + thisShiftNecessary));
                        continue;
                    }
                }
            } else if (insn instanceof FieldInsnNode) {
                FieldInsnNode fin = (FieldInsnNode) insn;
                MethodNode accessMethod;
                if ((fin.getOpcode() == GETSTATIC || fin.getOpcode() == GETFIELD) && 
                    (accessMethod = getAccessMethod(fin.owner, fin.name, fin.desc, "G")) != null) {
                    
                    newInstructions.add(
                        new MethodInsnNode(INVOKESTATIC, originalOuterClass.name, accessMethod.name, accessMethod.desc, false)
                    );
                    continue;
                }
                ;
                if ((fin.getOpcode() == PUTSTATIC || fin.getOpcode() == PUTFIELD) && 
                    (accessMethod = getAccessMethod(fin.owner, fin.name, fin.desc, "S")) != null) {
                    
                    newInstructions.add(
                        new MethodInsnNode(INVOKESTATIC, originalOuterClass.name, accessMethod.name, accessMethod.desc, false)
                    );
                    continue;
                }
                ;

            } else if (insn instanceof MethodInsnNode) {
                // instance method call -> outer class instance method call
                // using a generated access method
                MethodInsnNode min = (MethodInsnNode) insn;
                MethodNode accessMethod;

                if ((min.getOpcode() == INVOKEVIRTUAL || 
                     min.getOpcode() == INVOKESPECIAL || 
                     min.getOpcode() == INVOKESTATIC
                    ) && 
                    (accessMethod = getAccessMethod(min.owner, min.name, min.desc, "M")) != null) {
                    
                    log.debug("Found " + BytecodeTraceUtil.toString(min));
                    newInstructions.add(
                        new MethodInsnNode(INVOKESTATIC, originalOuterClass.name, accessMethod.name, accessMethod.desc, false)
                    );
                    continue;

                } else if (min.getOpcode() == INVOKESTATIC && "asyncResult".equals(min.name)  && 
                           ASYNC_CALL_NAME.equals(min.owner)) {

                    newInstructions.add(new VarInsnNode(ALOAD, 0));
                    newInstructions.add(
                        new MethodInsnNode(INVOKESTATIC, 
                                           ASYNC_TASK_NAME, 
                                           "$$result$$", 
                                           "(Ljava/lang/Object;L" + ASYNC_TASK_NAME + ";)" + COMPLETION_STAGE_DESCRIPTOR, 
                                           false
                        )
                    );
                    continue;

                }
            } else if (insn.getOpcode() == ARETURN) {
                // GOTO methodEnd instead of returning value
                newInstructions.add(new JumpInsnNode(GOTO, methodEnd));
                continue;
            }

            // do not make changes
            newInstructions.add(insn.clone(labelsMap));
        }
        newInstructions.add(globalCatchHandler);

        // Frame is computed anyway
        newInstructions.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" }));

        newInstructions.add(new VarInsnNode(ASTORE, 1));
        newInstructions.add(new VarInsnNode(ALOAD, 0));
        newInstructions.add(new VarInsnNode(ALOAD, 1));
        newInstructions.add(
            new MethodInsnNode(INVOKEVIRTUAL, 
                               ASYNC_TASK_NAME, 
                               "$$fault$$",                
                               "(Ljava/lang/Throwable;)" + COMPLETION_STAGE_DESCRIPTOR, 
                               false
            )
        );
        newInstructions.add(globalCatchEnd);

        newInstructions.add(methodEnd);

        // Frame is computed anyway
        newInstructions.add(new FrameNode(F_SAME, 0, null, 0, null));

        // POP value from stack that was placed before ARETURN
        newInstructions.add(new InsnNode(POP));
        newInstructions.add(new InsnNode(RETURN));

        asyncRunMethod.instructions = newInstructions;
        // Maxs
        // 2 for exception handling & asyncResult replacement
        asyncRunMethod.maxLocals = Math.max(originalAsyncMethod.maxLocals - argTypes.length + thisShiftNecessary, 2);
        asyncRunMethod.maxStack = Math.max(originalAsyncMethod.maxStack, 2);

        return asyncRunMethod;
    }

    protected MethodNode createReplacementAsyncMethod(ClassNode classNode, MethodNode originalAsyncMethodNode,
            String asyncTaskClassName) {

        boolean isStatic = (originalAsyncMethodNode.access & Opcodes.ACC_STATIC) != 0;
        int thisArgShift = isStatic ? 0 : 1;
        Type[] originalArgTypes = Type.getArgumentTypes(originalAsyncMethodNode.desc);
        int originalArity = originalArgTypes.length;

        MethodNode replacementAsyncMethodNode = new MethodNode(
            originalAsyncMethodNode.access, originalAsyncMethodNode.name, originalAsyncMethodNode.desc, null, null
        );

        replacementAsyncMethodNode.visitAnnotation(CONTINUABLE_ANNOTATION_DESCRIPTOR, true);
        replacementAsyncMethodNode.visitCode();

        replacementAsyncMethodNode.visitTypeInsn(NEW, asyncTaskClassName);
        replacementAsyncMethodNode.visitInsn(DUP);
        if (!isStatic) {
            // Reference to outer this
            replacementAsyncMethodNode.visitVarInsn(ALOAD, 0);
        }

        // load all method arguments into stack
        for (int i = 0; i < originalArity; i++) {
            // Shifted for this if necessary
            replacementAsyncMethodNode.visitVarInsn(originalArgTypes[i].getOpcode(ILOAD), i + thisArgShift);
        }

        String constructorDesc = Type.getMethodDescriptor(
            Type.VOID_TYPE, 
            isStatic ? originalArgTypes : prependArray(originalArgTypes, Type.getObjectType(classNode.name))
        );
        
        replacementAsyncMethodNode.visitMethodInsn(INVOKESPECIAL, asyncTaskClassName, "<init>", constructorDesc, false);
        replacementAsyncMethodNode.visitVarInsn(ASTORE, originalArity + thisArgShift);

        replacementAsyncMethodNode.visitVarInsn(ALOAD, originalArity + thisArgShift);
        replacementAsyncMethodNode.visitMethodInsn(INVOKESTATIC, ASYNC_EXECUTOR_NAME, "execute", "(Ljava/lang/Runnable;)V", false);

        replacementAsyncMethodNode.visitVarInsn(ALOAD, originalArity + thisArgShift);
        replacementAsyncMethodNode.visitFieldInsn(GETFIELD, ASYNC_TASK_NAME, "future", COMPLETION_STAGE_DESCRIPTOR);
        replacementAsyncMethodNode.visitInsn(ARETURN);

        replacementAsyncMethodNode.visitMaxs(
            Math.max(1, originalArity + thisArgShift), // for AsyncTask constructor call
            originalArity + thisArgShift + 1 // args count + outer this (for non static) + future var
        );
        replacementAsyncMethodNode.visitEnd();

        return replacementAsyncMethodNode;
    }

    protected void createAccessMethodsForAsyncMethod(ClassNode classNode, MethodNode methodNode) {
        List<MethodNode> methods = methodsOf(classNode);
        for (Iterator<?> i = methodNode.instructions.iterator(); i.hasNext();) {
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
                        createAccessMethod(classNode, methodInstructionNode,
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
                    createAccessMethod(classNode, methodInstructionNode, false, methods);
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
                            
                            createAccessGetter(classNode, fieldInstructionNode, (targetFieldNode.access & ACC_STATIC) != 0, methods);
                            
                        } else if (fieldInstructionNode.getOpcode() == PUTSTATIC || 
                                   fieldInstructionNode.getOpcode() == PUTFIELD) {
                            
                            createAccessSetter(classNode, fieldInstructionNode, (targetFieldNode.access & ACC_STATIC) != 0, methods);
                        }
                    }
                }
            }
        }
    }

    protected MethodNode createAccessMethod(ClassNode        classNode, 
                                            MethodInsnNode   targetMethodNode, 
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

    protected MethodNode createAccessGetter(ClassNode        classNode, 
                                            FieldInsnNode    targetFieldNode, 
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

    protected MethodNode createAccessSetter(ClassNode        classNode, 
                                            FieldInsnNode    targetFieldNode, 
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

    private MethodNode getAccessMethod(String owner, String name, String desc, String kind) {
        return accessMethods.get(owner + name + desc + "-" + kind);
    }

    private static Type[] prependArray(Type[] array, Type value) {
        Type[] result = new Type[array.length + 1];
        result[0] = value;
        System.arraycopy(array, 0, result, 1, array.length);
        return result;
    }

}
