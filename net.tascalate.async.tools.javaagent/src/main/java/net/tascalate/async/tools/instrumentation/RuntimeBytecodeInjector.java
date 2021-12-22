/**
 * ﻿Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.tools.instrumentation;

import static net.tascalate.asmx.Opcodes.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.annotation.Documented;
import java.lang.instrument.ClassDefinition;

import net.tascalate.asmx.ClassReader;
import net.tascalate.asmx.ClassVisitor;
import net.tascalate.asmx.ClassWriter;
import net.tascalate.asmx.MethodVisitor;
import net.tascalate.asmx.Type;
import net.tascalate.asmx.commons.LocalVariablesSorter;

class RuntimeBytecodeInjector {
    
    private static final String CLASS = "java.lang.invoke.InnerClassLambdaMetafactory";
    
    static interface LambdaClassTransformer {
        byte[] transform(Class<?> lambdaOwningClass, byte[] lambdaClassBytes) throws Throwable;
    }
    
    static boolean isInjectionApplied() {
        Class<?> cls;
        try {
            cls = ClassLoader.getSystemClassLoader().loadClass(CLASS);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
        Documented anno = cls.getAnnotation(Documented.class);
        return anno != null;
    }

    static ClassDefinition modifyLambdaMetafactory() throws ClassNotFoundException, IOException {
        ClassDefinition original = loadClassDefinition(CLASS);
        ClassReader classReader = new ClassReader(new ByteArrayInputStream(original.getDefinitionClassFile()));
        ClassWriter classWriter = new ClassWriter(classReader, 0);

        class PatchClassDefinitionMethodVisitor extends LocalVariablesSorter {
            PatchClassDefinitionMethodVisitor(int access, String desc, MethodVisitor mv) {
                super(ASM9, access, desc, mv);
            }
            
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == INVOKEVIRTUAL && "toByteArray".equals(name) && owner.contains("ClassWriter")) {
                    
                    Type bytesType = Type.getType(byte[].class);
                    Type classType = Type.getType(Class.class);
                    
                    Type objectType = Type.getType(Object.class);
                    Type objectsType = Type.getType(Object[].class);
                    Type systemType = Type.getType(System.class);
                    Type printStreamType = Type.getType(PrintStream.class);
                    
                    int bytesVar = newLocal(bytesType);
                    int params = newLocal(objectsType);
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    visitVarInsn(bytesType.getOpcode(ISTORE), bytesVar);
                    
                    visitInsn(ICONST_3);
                    visitTypeInsn(ANEWARRAY, objectType.getInternalName());
                    visitVarInsn(objectsType.getOpcode(ISTORE), params);
                    
                    // params[0] = this
                    visitVarInsn(objectsType.getOpcode(ILOAD), params);
                    visitInsn(ICONST_0);
                    visitVarInsn(ALOAD, 0);
                    visitInsn(AASTORE);
                    
                    // params[1] = super.targetClass
                    visitVarInsn(objectsType.getOpcode(ILOAD), params);
                    visitInsn(ICONST_1);
                    visitVarInsn(ALOAD, 0);
                    visitFieldInsn(GETFIELD, "java/lang/invoke/AbstractValidatingLambdaMetafactory", "targetClass", classType.getDescriptor());
                    visitInsn(AASTORE);
                    
                    // params[2] = inBytes, after call replaced by outBytes
                    visitVarInsn(objectsType.getOpcode(ILOAD), params);
                    visitInsn(ICONST_2);
                    visitVarInsn(bytesType.getOpcode(ILOAD), bytesVar);
                    visitInsn(AASTORE);
                    
                    visitFieldInsn(GETSTATIC, systemType.getInternalName(), "out", printStreamType.getDescriptor());
                    visitVarInsn(objectsType.getOpcode(ILOAD), params);
                    visitMethodInsn(INVOKEVIRTUAL, printStreamType.getInternalName(), "print", Type.getMethodDescriptor(Type.VOID_TYPE, objectType), false);

                    // get outBytes, params[2]
                    visitVarInsn(objectsType.getOpcode(ILOAD), params);
                    visitInsn(ICONST_2);
                    visitInsn(AALOAD);
                } else {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            }
        };

        ClassVisitor cv = new ClassVisitor(ASM9, classWriter) {
            @Override
            public void visit(int version,
                              int access,
                              String name,
                              String signature,
                              String superName,String[] interfaces) {
                super.visit(version, access, name, signature, superName, interfaces);
                Type annoType = Type.getType(Documented.class);
                visitAnnotation(annoType.getDescriptor(), true).visitEnd();
            }
            
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if ("spinInnerClass".equals(name) || "generateInnerClass".equals(name)) {
                    return new PatchClassDefinitionMethodVisitor(access, desc, mv);
                } else {
                    return mv;
                }
            }
            
        };
        classReader.accept(cv, ClassReader.EXPAND_FRAMES);
        return new ClassDefinition(original.getDefinitionClass(), classWriter.toByteArray());
    }
    
    static void installTransformer(final LambdaClassTransformer transformer) {
        System.setOut(new PrintStream(System.out, true) {
            @Override
            public void print(Object o) {
                if (o instanceof Object[]) {
                    Object[] params = (Object[])o;
                    if (params.length == 3 &&
                        RuntimeBytecodeInjector.isValidCaller(params[0]) &&    
                        params[1] instanceof Class &&
                        params[2] instanceof byte[]) {
                        
                        byte[] inBytes = (byte[])params[2];
                        byte[] outBytes = inBytes;
                        try {
                            outBytes = transformer.transform((Class<?>)params[1], inBytes);
                        } catch (Throwable ex) {
                            
                        }
                        params[2] = outBytes == null ? inBytes : outBytes;
                        return;
                    }
                }
                super.println(o);
            }
        });        
    }
    
    private static boolean isValidCaller(Object o) {
        return o != null && CLASS.equals(o.getClass().getName());
    }
    
    private static ClassDefinition loadClassDefinition(Class<?> clazz) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buff = new byte[1024];
        try (InputStream in = Object.class.getResourceAsStream('/' + clazz.getName().replace('.', '/') + ".class")) {
            int c = 0;
            while ((c= in.read(buff)) > 0) {
                out.write(buff, 0, c);
            }
        }
        out.close();
        byte[] bytes = out.toByteArray();
        return new ClassDefinition(clazz, bytes);
    }
    
    private static ClassDefinition loadClassDefinition(String className) throws ClassNotFoundException, IOException {
        return loadClassDefinition(Class.forName(className));
    }

}
