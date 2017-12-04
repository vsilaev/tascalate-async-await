package net.tascalate.async.tools.core;

import static net.tascalate.async.tools.core.BytecodeIntrospection.createOuterClassMethodArgFieldName;
import static net.tascalate.async.tools.core.BytecodeIntrospection.innerClassesOf;
import static net.tascalate.async.tools.core.BytecodeIntrospection.isLoadOpcode;
import static org.objectweb.asm.Opcodes.*;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

public class AsynResultMethodTransformer extends AbstractMethodTransformer {
    protected final static String COMPLETION_STAGE_DESCRIPTOR = "Ljava/util/concurrent/CompletionStage;";
    protected final static String ASYNC_TASK_NAME = "net/tascalate/async/core/AsyncTask";
    
    public AsynResultMethodTransformer(ClassNode               classNode,
                                       List<InnerClassNode>    originalInnerClasses,
                                       MethodNode              originalAsyncMethodNode,
                                       List<ClassNode>         newClasses,
                                       Map<String, MethodNode> accessMethods) {
        super(classNode, originalInnerClasses, originalAsyncMethodNode, newClasses, accessMethods);
    }
    
    @Override
    protected ClassNode createAnonymousClass(String asyncTaskClassName) {
        boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;

        ClassNode asyncRunnableClass = new ClassNode();

        asyncRunnableClass.visit(classNode.version, ACC_SUPER, asyncTaskClassName, null, ASYNC_TASK_NAME, null);
        asyncRunnableClass.visitSource(classNode.sourceFile, null);
        asyncRunnableClass.visitOuterClass(classNode.name, originalAsyncMethod.name, originalAsyncMethod.desc);

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
                asyncRunnableClass.visitField(ACC_PRIVATE + ACC_FINAL + ACC_SYNTHETIC, argName, argDesc, null, null);
            }
        }

        addAnonymousClassConstructor(asyncRunnableClass, outerClassField);
        addAnonymousClassRunMethod(asyncRunnableClass, outerClassField);
        return asyncRunnableClass;
    }
    
    @Override
    protected MethodNode createReplacementAsyncMethod(String asyncTaskClassName) {

        boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;
        int thisArgShift = isStatic ? 0 : 1;
        Type[] originalArgTypes = Type.getArgumentTypes(originalAsyncMethod.desc);
        int originalArity = originalArgTypes.length;

        MethodNode replacementAsyncMethodNode = new MethodNode(
            originalAsyncMethod.access, originalAsyncMethod.name, originalAsyncMethod.desc, null, null
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

        Type returnType = Type.getReturnType(originalAsyncMethod.desc);
        boolean hasResult = !Type.VOID_TYPE.equals(returnType); 
        if (hasResult) {
            replacementAsyncMethodNode.visitVarInsn(ALOAD, originalArity + thisArgShift);
            replacementAsyncMethodNode.visitFieldInsn(GETFIELD, ASYNC_TASK_NAME, "future", COMPLETION_STAGE_DESCRIPTOR);
            replacementAsyncMethodNode.visitInsn(ARETURN);
        } else {
            replacementAsyncMethodNode.visitInsn(RETURN);
        }

        replacementAsyncMethodNode.visitMaxs(
            Math.max(1, originalArity + thisArgShift), // for AsyncTask constructor call
            originalArity + thisArgShift + (hasResult ? 1 : 0) // args count + outer this (for non static) + future var
        );
        replacementAsyncMethodNode.visitEnd();

        return replacementAsyncMethodNode;
    }
    
    protected MethodNode addAnonymousClassConstructor(ClassNode asyncRunnableClass, FieldNode outerClassField) {
        boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;
        // Original methods arguments
        Type[] argTypes = Type.getArgumentTypes(originalAsyncMethod.desc);
        int originalArity = argTypes.length;

        String constructorDesc = Type.getMethodDescriptor(
            Type.VOID_TYPE,
            isStatic ? argTypes : prependArray(argTypes, Type.getObjectType(classNode.name))
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
    
    protected MethodNode addAnonymousClassRunMethod(ClassNode asyncRunnableClass, FieldNode outerClassField) {
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
                        new MethodInsnNode(INVOKESTATIC, classNode.name, accessMethod.name, accessMethod.desc, false)
                    );
                    continue;
                }
                ;
                if ((fin.getOpcode() == PUTSTATIC || fin.getOpcode() == PUTFIELD) && 
                    (accessMethod = getAccessMethod(fin.owner, fin.name, fin.desc, "S")) != null) {
                    
                    newInstructions.add(
                        new MethodInsnNode(INVOKESTATIC, classNode.name, accessMethod.name, accessMethod.desc, false)
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
                        new MethodInsnNode(INVOKESTATIC, classNode.name, accessMethod.name, accessMethod.desc, false)
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
            } else if (insn.getOpcode() == ARETURN || insn.getOpcode() == RETURN) {
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

        Type returnType = Type.getReturnType(originalAsyncMethod.desc);
        boolean hasResult = !Type.VOID_TYPE.equals(returnType); 
        if (hasResult) {
            // POP value from stack that was placed before ARETURN
            newInstructions.add(new InsnNode(POP));
        }
        newInstructions.add(new InsnNode(RETURN));

        asyncRunMethod.instructions = newInstructions;
        // Maxs
        // 2 for exception handling & asyncResult replacement
        asyncRunMethod.maxLocals = Math.max(originalAsyncMethod.maxLocals - argTypes.length + thisShiftNecessary, 2);
        asyncRunMethod.maxStack = Math.max(originalAsyncMethod.maxStack, 2);

        return asyncRunMethod;        
    }
}
