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

import static net.tascalate.async.tools.core.BytecodeIntrospection.createOuterClassMethodArgFieldName;
import static net.tascalate.async.tools.core.BytecodeIntrospection.isLoadOpcode;
import static net.tascalate.async.tools.core.BytecodeIntrospection.visibleTypeAnnotationsOf;
import static net.tascalate.async.tools.core.BytecodeIntrospection.invisibleTypeAnnotationsOf;
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
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

public class AsyncTaskMethodTransformer extends AsyncMethodTransformer {
    private final static Type ASYNC_TASK_TYPE = Type.getObjectType("net/tascalate/async/core/AsyncTask"); 
    private final static Type TASCALATE_PROMISE_TYPE = Type.getObjectType("net/tascalate/concurrent/Promise");
    
    public AsyncTaskMethodTransformer(ClassNode               classNode,
                                      MethodNode              originalAsyncMethodNode,
                                      Map<String, MethodNode> accessMethods) {
        super(classNode, originalAsyncMethodNode, accessMethods);
    }
    
    @Override
    public ClassNode transform() {
        return transform(ASYNC_TASK_TYPE);
    }
    
    @Override
    protected MethodNode createReplacementAsyncMethod(String asyncTaskClassName) {
        return createReplacementAsyncMethod(asyncTaskClassName, ASYNC_TASK_TYPE, "promise", TASCALATE_PROMISE_TYPE);
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

        asyncRunMethod.visitAnnotation(SUSPENDABLE_ANNOTATION_TYPE.getDescriptor(), true);
        // Local variables
        // amn.localVariables = methodNode.localVariables;

        LabelNode methodStart = new LabelNode();
        //LabelNode globalCatchEnd = new LabelNode();
        //LabelNode globalCatchHandler = new LabelNode();
        LabelNode methodEnd = new LabelNode();

        Map<LabelNode, LabelNode> labelsMap = new IdentityHashMap<>();
        for (AbstractInsnNode l = originalAsyncMethod.instructions.getFirst(); l != null; l = l.getNext()) {
            if (!(l instanceof LabelNode))
                continue;
            labelsMap.put((LabelNode) l, new LabelNode());
        }

        List<TryCatchBlockNode> tryCatchBlocks = new ArrayList<>();
        // Try-catch blocks
        for (Iterator<?> it = originalAsyncMethod.tryCatchBlocks.iterator(); it.hasNext();) {
            TryCatchBlockNode tn = (TryCatchBlockNode) it.next();
            TryCatchBlockNode newTn = new TryCatchBlockNode(
                labelsMap.get(tn.start), labelsMap.get(tn.end),
                labelsMap.get(tn.handler), tn.type);
            newTn.invisibleTypeAnnotations = copyTypeAnnotations(invisibleTypeAnnotationsOf(tn));
            newTn.visibleTypeAnnotations = copyTypeAnnotations(visibleTypeAnnotationsOf(tn));
            tryCatchBlocks.add(newTn);
        }
        // Should be the latest -- surrounding try-catch-all
        /*
        tryCatchBlocks.add(
            new TryCatchBlockNode(methodStart, globalCatchEnd, globalCatchHandler, "java/lang/Throwable")
        );
        */
        asyncRunMethod.tryCatchBlocks = tryCatchBlocks;

        InsnList newInstructions = new InsnList();
        newInstructions.add(methodStart);

        Type returnType = Type.getReturnType(originalAsyncMethod.desc);
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
                        String argDesc = argTypes[i].getDescriptor();

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
                    } else {
                        // decrease local variable indexes
                        newInstructions.add(new VarInsnNode(vin.getOpcode(), vin.var - argTypes.length + thisShiftNecessary));
                        continue;
                    }
                }
            } else if (insn instanceof IincInsnNode) {
                IincInsnNode iins = (IincInsnNode)insn; 
                if (iins.var < argTypes.length + thisWasInOriginal) {
                    int i = iins.var - thisWasInOriginal; // method
                    // argument's index
                    String argName = createOuterClassMethodArgFieldName(i);
                    String argDesc = argTypes[i].getDescriptor();
                    // i+=2 ==> this.val$2 = this.val$2 + 2;
                    newInstructions.add(new VarInsnNode(ALOAD, 0));
                    newInstructions.add(new FieldInsnNode(GETFIELD, asyncRunnableClass.name, argName, argDesc));
                    newInstructions.add(new IntInsnNode(SIPUSH, iins.incr));
                    newInstructions.add(new InsnNode(IADD));
                    newInstructions.add(new VarInsnNode(ALOAD, 0));
                    newInstructions.add(new InsnNode(SWAP));
                    newInstructions.add(new FieldInsnNode(PUTFIELD, asyncRunnableClass.name, argName, argDesc));                    
                } else {
                    newInstructions.add(new IincInsnNode(iins.var - argTypes.length + thisShiftNecessary, iins.incr));
                }
                continue;
                
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
                if ((fin.getOpcode() == PUTSTATIC || fin.getOpcode() == PUTFIELD) && 
                    (accessMethod = getAccessMethod(fin.owner, fin.name, fin.desc, "S")) != null) {
                    
                    newInstructions.add(
                        new MethodInsnNode(INVOKESTATIC, classNode.name, accessMethod.name, accessMethod.desc, false)
                    );
                    continue;
                }
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
                        case "asyncResult":
                            if (Type.VOID_TYPE.equals(returnType)) {
                                throw new IllegalStateException("Async result must be used only inside methods that return value");
                            }
                            newInstructions.add(new VarInsnNode(ALOAD, 0));
                            newInstructions.add(new InsnNode(SWAP));
                            newInstructions.add(
                                new MethodInsnNode(INVOKEVIRTUAL, 
                                                   ASYNC_TASK_TYPE.getInternalName(), 
                                                   "complete",
                                                   Type.getMethodDescriptor(Type.getReturnType(min.desc), OBJECT_TYPE),
                                                   false
                                )
                            );
                            continue;
                        case "interrupted":
                            newInstructions.add(new VarInsnNode(ALOAD, 0));
                            newInstructions.add(
                                    new MethodInsnNode(INVOKEVIRTUAL, 
                                                       ASYNC_TASK_TYPE.getInternalName(), 
                                                       "interrupted", 
                                                       Type.getMethodDescriptor(Type.BOOLEAN_TYPE), 
                                                       false
                                    )
                            );                            
                            continue;                            
                        case "await":
                            newInstructions.add(
                                new MethodInsnNode(INVOKESTATIC, 
                                                   ASYNC_METHOD_EXECUTOR_TYPE.getInternalName(), 
                                                   "await", 
                                                   Type.getMethodDescriptor(OBJECT_TYPE, COMPLETION_STAGE_TYPE),
                                                   false
                                )
                            );
                            continue;                            
                        case "yield":
                            throw new IllegalStateException("Yield must be used only inside generator methods");
                    }
                }
            } else if (insn.getOpcode() == ARETURN || insn.getOpcode() == RETURN) {
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
        /*
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
        */
        newInstructions.add(methodEnd);

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
