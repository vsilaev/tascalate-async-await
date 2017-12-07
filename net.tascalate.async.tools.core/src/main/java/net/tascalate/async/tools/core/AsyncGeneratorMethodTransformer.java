package net.tascalate.async.tools.core;

import static net.tascalate.async.tools.core.BytecodeIntrospection.createOuterClassMethodArgFieldName;
import static net.tascalate.async.tools.core.BytecodeIntrospection.isLoadOpcode;
import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

public class AsyncGeneratorMethodTransformer extends AbstractMethodTransformer {
    private final static Type ASYNC_GENERATOR_TYPE = Type.getObjectType("net/tascalate/async/core/AsyncGenerator");
    private final static Type LAZY_GENERATOR_TYPE = Type.getObjectType("net/tascalate/async/core/LazyGenerator");
    
    public AsyncGeneratorMethodTransformer(ClassNode               classNode,
                                           List<InnerClassNode>    originalInnerClasses,
                                           MethodNode              originalAsyncMethodNode,
                                           Map<String, MethodNode> accessMethods) {
        super(classNode, originalInnerClasses, originalAsyncMethodNode, accessMethods);
    }

    @Override
    public ClassNode transform() {
        return transform(ASYNC_GENERATOR_TYPE);
    }
    
    @Override
    protected MethodNode createReplacementAsyncMethod(String asyncTaskClassName) {
        return createReplacementAsyncMethod(asyncTaskClassName, ASYNC_GENERATOR_TYPE, "generator", LAZY_GENERATOR_TYPE);
    }
   
    @Override
    protected MethodNode addAnonymousClassRunMethod(ClassNode asyncRunnableClass, FieldNode outerClassField) {
        boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;
        int thisWasInOriginal = isStatic ? 0 : 1;
        int thisShiftNecessary = isStatic ? 1 : 0;

        Type[] argTypes = Type.getArgumentTypes(originalAsyncMethod.desc);
        log.debug("Method has " + argTypes.length + " arguments");

        MethodNode asyncRunMethod = (MethodNode)asyncRunnableClass.visitMethod(
            ACC_PROTECTED, "doRun", "()V", null, new String[]{"java/lang/Throwable"}
        );

        asyncRunMethod.visitAnnotation(CONTINUABLE_ANNOTATION_TYPE.getDescriptor(), true);

        LabelNode methodStart = new LabelNode();
        LabelNode methodEnd = new LabelNode();

        Map<LabelNode, LabelNode> labelsMap = new IdentityHashMap<>();
        for (AbstractInsnNode l = originalAsyncMethod.instructions.getFirst(); l != null; l = l.getNext()) {
            if (!(l instanceof LabelNode))
                continue;
            labelsMap.put((LabelNode) l, new LabelNode());
        }

        List<TryCatchBlockNode> tryCatchBlocks = new ArrayList<>();//originalAsyncMethod.tryCatchBlocks;
        // Try-catch blocks
        for (Iterator<?> it = originalAsyncMethod.tryCatchBlocks.iterator(); it.hasNext();) {
            TryCatchBlockNode tn = (TryCatchBlockNode) it.next();
            tryCatchBlocks.add(new TryCatchBlockNode(labelsMap.get(tn.start), labelsMap.get(tn.end),
                    labelsMap.get(tn.handler), tn.type));
        }
        asyncRunMethod.tryCatchBlocks = tryCatchBlocks;

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

                } else if (min.getOpcode() == INVOKESTATIC && ASYNC_CALL_NAME.equals(min.owner)) {
                    switch (min.name) {
                        case "yield":
                            Type[] args = Type.getArgumentTypes(min.desc);
                            newInstructions.add(new VarInsnNode(ALOAD, 0));
                            newInstructions.add(
                                new MethodInsnNode(INVOKESTATIC, 
                                                   ASYNC_GENERATOR_TYPE.getInternalName(), 
                                                   "$$yield$$", 
                                                   Type.getMethodDescriptor(
                                                       Type.getReturnType(min.desc), 
                                                       appendArray(args, ASYNC_GENERATOR_TYPE)
                                                   ),
                                                   false
                                )
                            );
                            continue;
                        case "await":
                            newInstructions.add(new VarInsnNode(ALOAD, 0));
                            newInstructions.add(
                                new MethodInsnNode(INVOKESTATIC, 
                                                   ASYNC_GENERATOR_TYPE.getInternalName(), 
                                                   "$$await$$", 
                                                   Type.getMethodDescriptor(
                                                       OBJECT_TYPE, COMPLETION_STAGE_TYPE, ASYNC_GENERATOR_TYPE
                                                   ), 
                                                   false
                                )
                            );
                            continue;
                        case "asyncResult":
                            throw new IllegalStateException("Async result must be used only inside non-generator methods");
                    }
                }
            } else if (insn.getOpcode() == ARETURN) {
                // GOTO methodEnd instead of returning value
                newInstructions.add(new JumpInsnNode(GOTO, methodEnd));
                continue;
            } else if (insn instanceof LabelNode) {
                newInstructions.add(labelsMap.get(insn));
                continue;
            }

            // do not make changes
            newInstructions.add(insn.clone(labelsMap));
        }
        newInstructions.add(methodEnd);

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
}
