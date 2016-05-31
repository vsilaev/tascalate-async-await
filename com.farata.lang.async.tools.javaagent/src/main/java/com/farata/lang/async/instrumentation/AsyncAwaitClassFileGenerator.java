
package com.farata.lang.async.instrumentation;

import static org.objectweb.asm.Opcodes.*;

import java.lang.instrument.IllegalClassFormatException;
import java.util.ArrayList;
import java.util.Collections;
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
import org.objectweb.asm.tree.AnnotationNode;
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
	
	final private static Log log = LogFactory.getLog(AsyncAwaitClassFileGenerator.class);
	
	final private static String ASYNC_ANNOTATION_DESCRIPTOR = "Lcom/farata/lang/async/api/async;";
	final private static String ASYNC_TASK_NAME = "com/farata/lang/async/core/AsyncTask";
	
	final private static String ASYNC_CALL_NAME = "com/farata/lang/async/api/AsyncCall";
	final private static String ASYNC_EXECUTOR_NAME = "com/farata/lang/async/core/AsyncExecutor";
	
	final private static String COMPLETION_STAGE_DESCRIPTOR = "Ljava/util/concurrent/CompletionStage;";
	
	final private static String CONTINUABLE_ANNOTATION_DESCRIPTOR = "Lorg/apache/commons/javaflow/api/continuable;";
	
	// New generated classes
	final private List<ClassNode> newClasses = new ArrayList<ClassNode>();
	
	// Original method's "method name + method desc" -> Access method's MethodNode
	final private Map<String, MethodNode> accessMethods = new HashMap<String, MethodNode>();
	
	private void registerAccessMethod(final String owner, final String name, final String desc, final String kind, final MethodNode methodNode) {
		accessMethods.put(owner + name + desc + "-" + kind, methodNode);
	}
	
	private MethodNode getAccessMethod(final String owner, final String name, final String desc, final String kind) {
		return accessMethods.get(owner + name + desc + "-" + kind);
	}
	
	public byte[] transform(final String className, 
			                final byte[] classfileBuffer, 
			                final ResourceLoader resourceLoader) throws IllegalClassFormatException {
		// Read
		final ClassReader classReader = new ClassReader(classfileBuffer);
		final ClassNode classNode = new ClassNode();
		classReader.accept(classNode, ClassReader.EXPAND_FRAMES);

		// Transform
		if (!transform(classNode)) {
			// no modification, delegate further
			return null;
		}

		
		// Print transformed class
		log.debug("Transformed class:\n\n" + BytecodeTraceUtil.toString(classNode) + "\n\n");
		
		// Print generated classes
		for (final ClassNode newClass : newClasses) {
			log.debug("Generated class:\n\n" + BytecodeTraceUtil.toString(newClass) + "\n\n");
		}
		
		// Write
		final byte[] generatedClassBytes;
		{
			final ClassWriter cw = new ComputeClassWriter(0, resourceLoader);
			classNode.accept(cw);
			generatedClassBytes = cw.toByteArray();
		}
		return generatedClassBytes;
	}
	
	public Map<String, byte[]> getGeneratedClasses(final ResourceLoader resourceLoader) {
		final Map<String, byte[]> result = new HashMap<String, byte[]>();
		for (final ClassNode classNode : newClasses) {
			final ClassWriter cw = new ComputeClassWriter(ClassWriter.COMPUTE_FRAMES, resourceLoader);
			classNode.accept(cw);
			result.put(classNode.name, cw.toByteArray());
		}
		return result;
	}
	
	public void reset() {
		accessMethods.clear();
		newClasses.clear();
	}
	
	protected boolean transform(final ClassNode classNode) {
		boolean transformed = false;
		final List<InnerClassNode> originalInnerClasses = new ArrayList<InnerClassNode>(innerClassesOf(classNode));
		for (final MethodNode methodNode : new ArrayList<MethodNode>(methodsOf(classNode))) {
			if (isAsyncMethod(methodNode)) {
				transform(classNode, originalInnerClasses, methodNode);
				transformed = true;
			}
		}
		return transformed;
	}

	protected void transform(final ClassNode            classNode, 
		                     final List<InnerClassNode> originalInnerClasses, 
		                     final MethodNode           originalAsyncMethodNode) {
		
		log.info("Transforming blocking method: " + classNode.name + "." + originalAsyncMethodNode.name + originalAsyncMethodNode.desc);
		
		// Remove @async annotation
		removeAsyncAnnotation(originalAsyncMethodNode);
		
		// Create InnerClassNode for anoymous class
		final String asyncTaskClassName = createInnerClassName(classNode);
		innerClassesOf(classNode).add(new InnerClassNode(asyncTaskClassName, null, null, 0));
		
		// Create accessor methods
		createAccessMethodsForAsyncMethod(classNode, originalAsyncMethodNode);
		
		// Create ClassNode for anonymous class
		final ClassNode asyncTaskClassNode = createAnonymousClass(classNode, originalInnerClasses, originalAsyncMethodNode, asyncTaskClassName);
		newClasses.add(asyncTaskClassNode);
		
		// Replace original method 
		
		final MethodNode replacementAsyncMethodNode = createReplacementAsyncMethod(classNode, originalAsyncMethodNode, asyncTaskClassName);
		final List<MethodNode> methods = methodsOf(classNode);
		methods.set(methods.indexOf(originalAsyncMethodNode), replacementAsyncMethodNode);
		
	}
	
	protected ClassNode createAnonymousClass(final ClassNode            originalOuterClass, 
		                                     final List<InnerClassNode> originalInnerClasses, 
		                                     final MethodNode           originalAsyncMethod, 
		                                     final String               asyncClassName) {
		final boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;
		
		final ClassNode asyncRunnableClass = new ClassNode();
		
		asyncRunnableClass.visit(originalOuterClass.version, ACC_SUPER, asyncClassName, null, ASYNC_TASK_NAME, new String[]{});
		asyncRunnableClass.visitSource(originalOuterClass.sourceFile, null);
		asyncRunnableClass.visitOuterClass(originalOuterClass.name, originalAsyncMethod.name, originalAsyncMethod.desc);
		
		// Copy outer class inner classes
		final List<InnerClassNode> asyncClassInnerClasses = innerClassesOf(asyncRunnableClass);
		for (final InnerClassNode innerClassNode : originalInnerClasses) {
			asyncClassInnerClasses.add(innerClassNode);
		}
		
		// SerialVersionUID
		asyncRunnableClass.visitField(ACC_PRIVATE + ACC_FINAL + ACC_STATIC, "serialVersionUID", "J", null, new Long(1L));
		
		// Outer class instance field
		final FieldNode outerClassField;
		if (!isStatic) {
			outerClassField = (FieldNode) asyncRunnableClass.visitField(
				ACC_FINAL + ACC_PRIVATE + ACC_SYNTHETIC, 
				"this$0", "L" + originalOuterClass.name + ";", null, null
			);
		} else {
			outerClassField = null;
		}
		
		// Original methods arguments
		final Type[] argTypes = Type.getArgumentTypes(originalAsyncMethod.desc);
		final int originalArity = argTypes.length;
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
	
	protected MethodNode addAnonymousClassConstructor(final ClassNode  originalOuterClass, 
		                                              final MethodNode originalAsyncMethod,
		                                              final ClassNode  asyncRunnableClass,
		                                              final FieldNode  outerClassField) {
		
		final boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;
		// Original methods arguments
		final Type[] argTypes = Type.getArgumentTypes(originalAsyncMethod.desc);
		final int originalArity = argTypes.length;
		
		final String constructorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, isStatic ? 
			argTypes : prependArray(argTypes, Type.getReturnType("L" + originalOuterClass.name + ";")) 
		);
		
		final MethodVisitor mv = asyncRunnableClass.visitMethod(0, "<init>", constructorDesc, null, null);
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
			
		return (MethodNode)mv;
	}

	protected MethodNode addAnonymousClassRunMethod(final ClassNode  originalOuterClass, 
		                                            final MethodNode originalAsyncMethod, 
		                                            final ClassNode  asyncRunnableClass, 
		                                            final FieldNode  outerClassField) {
		
		final boolean isStatic = (originalAsyncMethod.access & Opcodes.ACC_STATIC) != 0;
		final int thisWasInOriginal = isStatic ? 0 : 1;
		final int thisShiftNecessary = isStatic ? 1 : 0;
		
		final Type[] argTypes = Type.getArgumentTypes(originalAsyncMethod.desc);
		log.debug("Method has " + argTypes.length + " arguments");
		
		final MethodNode asyncRunMethod = (MethodNode) asyncRunnableClass.visitMethod(
			ACC_PUBLIC, "run", "()V", null, new String[] {}
		);
		
		asyncRunMethod.visitAnnotation(CONTINUABLE_ANNOTATION_DESCRIPTOR, true);

		// Local variables
		//amn.localVariables = methodNode.localVariables;
		
		final LabelNode methodStart = new LabelNode();
		final LabelNode globalCatchEnd = new LabelNode();
		final LabelNode globalCatchHandler = new LabelNode();
		final LabelNode methodEnd = new LabelNode();
		
		final Map<LabelNode, LabelNode> labelsMap = new IdentityHashMap<>();
		for (AbstractInsnNode l = originalAsyncMethod.instructions.getFirst(); l != null; l = l.getNext()) {
			if (!(l instanceof LabelNode))
				continue;
			labelsMap.put((LabelNode)l, new LabelNode());	
		}
		
		@SuppressWarnings("unchecked")
		final List<TryCatchBlockNode> tryCatchBlocks = asyncRunMethod.tryCatchBlocks;
		// Try-catch blocks
		for (final Iterator<?> it = originalAsyncMethod.tryCatchBlocks.iterator(); it.hasNext();) {
			final TryCatchBlockNode tn = (TryCatchBlockNode) it.next();
			tryCatchBlocks.add(new TryCatchBlockNode(
				labelsMap.get(tn.start), 
				labelsMap.get(tn.end), 
				labelsMap.get(tn.handler), 
				tn.type)
			);
		}
		// Should be the latest -- surrounding try-catch-all
		tryCatchBlocks.add(new TryCatchBlockNode(methodStart, globalCatchEnd, globalCatchHandler, "java/lang/Throwable"));
		
		final InsnList newInstructions = new InsnList();
		newInstructions.add(methodStart);
		
		// Instructions
		for (AbstractInsnNode insn = originalAsyncMethod.instructions.getFirst(); null != insn; insn = insn.getNext()) {
			if (insn instanceof VarInsnNode) {
				final VarInsnNode vin = (VarInsnNode) insn;
				// "this" -> outer class "this"
				if (!isStatic && vin.getOpcode() == ALOAD && vin.var == 0) {
					log.debug("Found " + BytecodeTraceUtil.toString(vin));
					
					newInstructions.add(new VarInsnNode(ALOAD, 0));
					newInstructions.add(new FieldInsnNode(GETFIELD, 
						asyncRunnableClass.name, outerClassField.name, outerClassField.desc
					));
					continue;
				}
				
				// original method had arguments
				if (vin.getOpcode() != RET && (vin.var > 0 || isStatic)) {
					log.debug("Found " + BytecodeTraceUtil.toString(vin));
					// method argument -> inner class field 
					if (vin.var < argTypes.length + thisWasInOriginal) {
						int i = vin.var - thisWasInOriginal;	// method argument's index

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
				final FieldInsnNode fin = (FieldInsnNode)insn;
				MethodNode accessMethod;
				if ((fin.getOpcode() == GETSTATIC || fin.getOpcode() == GETFIELD) && 
					(accessMethod = getAccessMethod(fin.owner, fin.name, fin.desc, "G")) != null) {
					newInstructions.add(
						new MethodInsnNode(INVOKESTATIC, originalOuterClass.name, accessMethod.name, accessMethod.desc, false)
					);
					continue;
				};
				if ((fin.getOpcode() == PUTSTATIC || fin.getOpcode() == PUTFIELD) && 
					(accessMethod = getAccessMethod(fin.owner, fin.name, fin.desc, "S")) != null) {
					newInstructions.add(
						new MethodInsnNode(INVOKESTATIC, originalOuterClass.name, accessMethod.name, accessMethod.desc, false)
					);
					continue;
				};

			} else if (insn instanceof MethodInsnNode) {
				// instance method call -> outer class instance method call using a generated access method
				final MethodInsnNode min = (MethodInsnNode) insn;
				final MethodNode accessMethod;
				
				if ((min.getOpcode() == INVOKEVIRTUAL  || 
					 min.getOpcode() == INVOKESPECIAL ||
					 min.getOpcode() == INVOKESTATIC)
					 && (accessMethod = getAccessMethod(min.owner, min.name, min.desc, "M")) != null) {
					log.debug("Found " + BytecodeTraceUtil.toString(min));
					newInstructions.add(
						new MethodInsnNode(INVOKESTATIC, originalOuterClass.name, accessMethod.name, accessMethod.desc, false)
					);

					continue;

				} else if (
					min.getOpcode() == INVOKESTATIC && 
					"asyncResult".equals(min.name) && 
					ASYNC_CALL_NAME.equals(min.owner)) {
					
					newInstructions.add(new VarInsnNode(ALOAD, 0));
					newInstructions.add(new MethodInsnNode(
						INVOKESTATIC, ASYNC_TASK_NAME, 
						"$$result$$", "(Ljava/lang/Object;L" + ASYNC_TASK_NAME + ";)" + COMPLETION_STAGE_DESCRIPTOR,
						false
					));
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
		newInstructions.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"}));
		
		newInstructions.add(new VarInsnNode(ASTORE, 1));
		newInstructions.add(new VarInsnNode(ALOAD, 0));
		newInstructions.add(new VarInsnNode(ALOAD, 1));
		newInstructions.add(new MethodInsnNode(
			INVOKEVIRTUAL, ASYNC_TASK_NAME, 
			"$$fault$$", "(Ljava/lang/Throwable;)" + COMPLETION_STAGE_DESCRIPTOR, 
			false
		));
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
	
	protected MethodNode createReplacementAsyncMethod(final ClassNode classNode, 
		                                              final MethodNode originalAsyncMethodNode, 
		                                              final String asyncTaskClassName) {
		
		final boolean isStatic = (originalAsyncMethodNode.access & Opcodes.ACC_STATIC) != 0;
		final int thisArgShift = isStatic ? 0 : 1;
		final Type[] originalArgTypes = Type.getArgumentTypes(originalAsyncMethodNode.desc);
		final int originalArity = originalArgTypes.length;
		
		final MethodNode replacementAsyncMethodNode = new MethodNode(
			originalAsyncMethodNode.access, 
			originalAsyncMethodNode.name, 
			originalAsyncMethodNode.desc, 
			null, null
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
			//Shifted for this if necessary
			replacementAsyncMethodNode.visitVarInsn(originalArgTypes[i].getOpcode(ILOAD), i + thisArgShift );
		}

		final String constructorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, isStatic ?
			originalArgTypes : prependArray(originalArgTypes, Type.getReturnType("L" + classNode.name + ";"))
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
	
	protected void createAccessMethodsForAsyncMethod(final ClassNode classNode, final MethodNode methodNode) {
		final List<MethodNode> methods = methodsOf(classNode);
		for (final Iterator<?> i = methodNode.instructions.iterator(); i.hasNext(); ) {
			final AbstractInsnNode instruction = (AbstractInsnNode)i.next();
			if (instruction instanceof MethodInsnNode) {
				final MethodInsnNode methodInstructionNode = (MethodInsnNode) instruction;
				if ((methodInstructionNode.getOpcode() == INVOKEVIRTUAL  || 
					 methodInstructionNode.getOpcode() == INVOKESPECIAL ||
					 methodInstructionNode.getOpcode() == INVOKESTATIC)
					 && methodInstructionNode.owner.equals(classNode.name)) {
					final MethodNode targetMethodNode = getMethod(methodInstructionNode.name, methodInstructionNode.desc, methods);
					if (null != targetMethodNode && (targetMethodNode.access & ACC_PRIVATE) != 0) {
						log.debug("Found private call " + BytecodeTraceUtil.toString(methodInstructionNode));
						createAccessMethod(
							classNode, methodInstructionNode, (targetMethodNode.access & ACC_STATIC) != 0, methods
						);
					}
				}
				
				if (methodInstructionNode.getOpcode() == INVOKESPECIAL 
					&& !"<init>".equals(methodInstructionNode.name)
					&& !methodInstructionNode.owner.equals(classNode.name)) {
					// INVOKESPECIAL is used for constructors/super-call, private instance methods
					// Here we filtered out only to private super-method calls
					log.debug("Found super-call " + BytecodeTraceUtil.toString(methodInstructionNode));
					createAccessMethod(
						classNode, methodInstructionNode, false, methods
					);
				}

			}
			if (instruction instanceof FieldInsnNode) {
				final FieldInsnNode fieldInstructionNode = (FieldInsnNode) instruction;
				if (fieldInstructionNode.owner.equals(classNode.name)) {
					final FieldNode targetFieldNode = getField(classNode, fieldInstructionNode.name, fieldInstructionNode.desc);
					if (null != targetFieldNode && (targetFieldNode.access & ACC_PRIVATE) != 0) {
						//log.debug("Found " + BytecodeTraceUtil.toString(fieldInstructionNode));
						if (fieldInstructionNode.getOpcode() == GETSTATIC || fieldInstructionNode.getOpcode() == GETFIELD) {
							createAccessGetter(
								classNode, fieldInstructionNode, (targetFieldNode.access & ACC_STATIC) != 0, methods
							);
						} else if (fieldInstructionNode.getOpcode() == PUTSTATIC || fieldInstructionNode.getOpcode() == PUTFIELD) {
							createAccessSetter(
								classNode, fieldInstructionNode, (targetFieldNode.access & ACC_STATIC) != 0, methods
							);
						}
					}
				}
			}
		}
	}
	
	protected MethodNode createAccessMethod(final ClassNode classNode, final MethodInsnNode targetMethodNode, final boolean isStatic, final List<MethodNode> methods) {
		MethodNode accessMethodNode = getAccessMethod(targetMethodNode.owner, targetMethodNode.name, targetMethodNode.desc, "M");
		if (null != accessMethodNode) {
			return accessMethodNode;
		}
		
		final String name = createAccessMethodName(methods);
		final Type[] originalArgTypes = Type.getArgumentTypes(targetMethodNode.desc);
		final Type[] argTypes = isStatic ? originalArgTypes : prependArray(originalArgTypes, Type.getReturnType("L" + classNode.name + ";")); 
		final Type returnType = Type.getReturnType(targetMethodNode.desc);
		final String desc = Type.getMethodDescriptor(returnType, argTypes);
		
		accessMethodNode = new MethodNode(ACC_STATIC + ACC_SYNTHETIC, name, desc, null, null);
		accessMethodNode.visitCode();
		
		// load all method arguments into stack
		final int arity = argTypes.length;
		for (int i = 0; i < arity; i++) {
			final int opcode = argTypes[i].getOpcode(ILOAD);
			log.debug("Using opcode " + opcode + " for loading " + argTypes[i]);
			accessMethodNode.visitVarInsn(opcode, i);
		}
		accessMethodNode.visitMethodInsn(isStatic ? INVOKESTATIC : INVOKESPECIAL, targetMethodNode.owner, targetMethodNode.name, targetMethodNode.desc, targetMethodNode.itf);
		accessMethodNode.visitInsn(returnType.getOpcode(IRETURN));
		accessMethodNode.visitMaxs(argTypes.length, argTypes.length);
		accessMethodNode.visitEnd();
		
		// Register mapping
		registerAccessMethod(targetMethodNode.owner, targetMethodNode.name, targetMethodNode.desc, "M", accessMethodNode);
		methods.add(accessMethodNode);
		return accessMethodNode;
	}
	
	protected MethodNode createAccessGetter(final ClassNode classNode, final FieldInsnNode targetFieldNode, final boolean isStatic, final List<MethodNode> methods) {
		MethodNode accessMethodNode = getAccessMethod(targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc, "G");
		if (null != accessMethodNode) {
			return accessMethodNode;
		}
		
		final String name = createAccessMethodName(methods);
		final Type[] argTypes = isStatic ? new Type[0] : new Type[]{Type.getReturnType("L" + classNode.name + ";")}; 
		final Type returnType = Type.getReturnType(targetFieldNode.desc);
		final String desc = Type.getMethodDescriptor(returnType, argTypes);
		
		accessMethodNode = new MethodNode(ACC_STATIC + ACC_SYNTHETIC, name, desc, null, null);
		accessMethodNode.visitCode();
		
		// load all method arguments into stack
		final int arity = argTypes.length;
		for (int i = 0; i < arity; i++) {
			final int opcode = argTypes[i].getOpcode(ILOAD);
			log.debug("Using opcode " + opcode + " for loading " + argTypes[i]);
			accessMethodNode.visitVarInsn(opcode, i);
		}
		accessMethodNode.visitFieldInsn(isStatic ? GETSTATIC : GETFIELD, targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc);
		accessMethodNode.visitInsn(returnType.getOpcode(IRETURN));
		accessMethodNode.visitMaxs(1, argTypes.length);
		accessMethodNode.visitEnd();
		
		// Register mapping
		registerAccessMethod(targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc, "G", accessMethodNode);
		methods.add(accessMethodNode);
		return accessMethodNode;
	}
	
	protected MethodNode createAccessSetter(final ClassNode classNode, final FieldInsnNode targetFieldNode, final boolean isStatic, final List<MethodNode> methods) {
		MethodNode accessMethodNode = getAccessMethod(targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc, "S");
		if (null != accessMethodNode) {
			return accessMethodNode;
		}
		
		final String name = createAccessMethodName(methods);
		final Type[] argTypes = isStatic ? 
			new Type[]{Type.getReturnType(targetFieldNode.desc)} : 
			new Type[]{Type.getReturnType("L" + classNode.name + ";"), Type.getReturnType(targetFieldNode.desc)};
		final Type returnType = Type.getReturnType("V"); // <-- void
		final String desc = Type.getMethodDescriptor(returnType, argTypes);
		
		accessMethodNode = new MethodNode(ACC_STATIC + ACC_SYNTHETIC, name, desc, null, null);
		accessMethodNode.visitCode();
		
		// load all method arguments into stack
		final int arity = argTypes.length;
		for (int i = 0; i < arity; i++) {
			final int opcode = argTypes[i].getOpcode(ILOAD);
			log.debug("Using opcode " + opcode + " for loading " + argTypes[i]);
			accessMethodNode.visitVarInsn(opcode, i);
		}
		accessMethodNode.visitFieldInsn(isStatic ? PUTSTATIC : PUTFIELD, targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc);
		accessMethodNode.visitInsn(RETURN);
		accessMethodNode.visitMaxs(argTypes.length, argTypes.length);
		accessMethodNode.visitEnd();
		
		// Register mapping
		registerAccessMethod(targetFieldNode.owner, targetFieldNode.name, targetFieldNode.desc, "S", accessMethodNode);
		methods.add(accessMethodNode);
		return accessMethodNode;
	}
	
	
	// --- Removing @async annotation
	
	private static void removeAsyncAnnotation(final MethodNode methodNode) {
		if (methodNode.invisibleAnnotations != null) {
			for (final Iterator<AnnotationNode> it = invisibleAnnotationsOf(methodNode).iterator(); it.hasNext();) {
				final AnnotationNode an = it.next();
				if (ASYNC_ANNOTATION_DESCRIPTOR.equals(an.desc)) {
					it.remove();
					log.debug("@async annotation removed, method: " + methodNode);
					return;
				}
			}
		}
		throw new IllegalStateException("No @async annotation found to remove");
	}
	
	// --- Instructions and Opcodes ---
	
	private static boolean isLoadOpcode(final int opcode) {
		return opcode >= ILOAD && opcode < ISTORE;
	}
	
	// --- Creating names ---
	
	private static String createInnerClassName(final ClassNode classNode) {
		int index = 1;
		String name;
		while (hasInnerClass(classNode, name = createInnerClassName(classNode, index))) {
			index++;
		}
		log.debug("Generated new inner class name: " + name);
		return name;
	}
	
	private static String createInnerClassName(final ClassNode classNode, final int index) {
		return classNode.name + "$" + index;
	}
	
	private static String createAccessMethodName(final List<MethodNode> methods) {
		int index = 0;
		String name;
		while (hasMethod(name = createAccessMethodName(index), methods)) {
			index++;
		}
		log.trace("Generated new method name: " + name);
		return name;
	}
	
	private static String createAccessMethodName(final int index) {
		return "access$" + index;
	}
	
	private static String createOuterClassMethodArgFieldName(final int index) {
		return "val$" + index;
	}
	
	// --- Finding inner classes ---
	
	private static boolean hasInnerClass(final ClassNode classNode, final String innerClassName) {
		return getInnerClass(classNode, innerClassName) != null;
	}
	
	private static InnerClassNode getInnerClass(final ClassNode classNode, final String innerClassName) {
		for (final InnerClassNode icn : innerClassesOf(classNode)) {
			if (innerClassName.equals(icn.name)) {
				return icn;
			}
		}
		return null;
	}
	
	// --- Finding methods ---
	
	private static boolean hasMethod(final String methodName, final List<MethodNode> methods) {
		return getMethod(methodName, null, methods) != null;
	}
	
	private static MethodNode getMethod(final String methodName, final String methodDesc, final List<MethodNode> methods) {
		for (final MethodNode methodNode : methods) {
			if (methodName.equals(methodNode.name) && (methodDesc == null || methodDesc.equals(methodNode.desc))) {
				return methodNode;
			}
		}
		return null;
	}
	
	private static FieldNode getField(final ClassNode classNode, final String fieldName, final String fieldDesc) {
		for (final FieldNode fieldNode : fieldsOf(classNode)) {
			if (fieldName.equals(fieldNode.name) && (fieldDesc == null || fieldDesc.equals(fieldNode.desc))) {
				return fieldNode;
			}
		}
		return null;
	}
	
	// --- Detecting blocking method ---

	private static boolean isAsyncMethod(final MethodNode methodNode) {
		if (hasAsyncAnnotation(methodNode)) {
			return true;
		}
		return false;
	}
	
	private static boolean hasAsyncAnnotation(final MethodNode methodNode) {
		final boolean found = 
				annotationPresent(invisibleAnnotationsOf(methodNode), ASYNC_ANNOTATION_DESCRIPTOR) || 
				annotationPresent(visibleAnnotationsOf(methodNode), ASYNC_ANNOTATION_DESCRIPTOR);
		
		if (found) {
			log.debug("@Async annotation found, method: " + methodNode);
		}
		
		return found;
	}
	
	private static boolean annotationPresent(final List<AnnotationNode> annotations, final String targetAnnotationTypeDescriptor) {
		for (final AnnotationNode annotation : annotations) {
			if (targetAnnotationTypeDescriptor.equals(annotation.desc)) {
				return true;
			}
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	private static List<MethodNode> methodsOf(final ClassNode classNode) {
		return null == classNode.methods ? Collections.<MethodNode>emptyList() : (List<MethodNode>)classNode.methods;
	}
	
	@SuppressWarnings("unchecked")
	private static List<FieldNode> fieldsOf(final ClassNode classNode) {
		return null == classNode.fields ? Collections.<FieldNode>emptyList() : (List<FieldNode>)classNode.fields;
	}
	
	@SuppressWarnings("unchecked")
	private static List<InnerClassNode> innerClassesOf(final ClassNode classNode) {
		return null == classNode.innerClasses ? Collections.<InnerClassNode>emptyList() : (List<InnerClassNode>)classNode.innerClasses;
	}
	
	private static List<AnnotationNode> visibleAnnotationsOf(final MethodNode methodNode) {
		return safeAnnotationsList(methodNode.visibleAnnotations);
	}
	
	private static List<AnnotationNode> invisibleAnnotationsOf(final MethodNode methodNode) {
		return safeAnnotationsList(methodNode.invisibleAnnotations);
	}

	
	@SuppressWarnings("unchecked")
	private static List<AnnotationNode> safeAnnotationsList(final List<?> annotations) {
		return null == annotations ? Collections.<AnnotationNode>emptyList() : (List<AnnotationNode>)annotations;
	}
	
	private static Type[] prependArray(final Type[] array, final Type value) {
		final Type[] result = new Type[array.length + 1];
		result[0] = value;
		System.arraycopy(array, 0, result, 1, array.length);
		return result;
	}

 }
