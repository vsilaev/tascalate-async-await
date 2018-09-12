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

import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_STRICT;
import static org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.ISTORE;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

class BytecodeIntrospection {
    private static final Log log = LogFactory.getLog(BytecodeIntrospection.class);
    
    static final String ASYNC_ANNOTATION_DESCRIPTOR = "Lnet/tascalate/async/async;";

    private BytecodeIntrospection() {
    }
    

    static String getMethodSignature(MethodNode methodNode, boolean outputExceptions) {
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
            @SuppressWarnings("unchecked")
            List<String> exceptions = (List<String>)methodNode.exceptions;
            result.append(
                exceptions.stream()
                    .map(v -> v.toString().replace('/', '.'))
                    .collect(Collectors.joining(", "))
            );
        }
        return result.toString();
    }
    

    // --- Instructions and Opcodes ---
    static boolean isLoadOpcode(int opcode) {
        return opcode >= ILOAD && opcode < ISTORE;
    }

    // --- Detecting blocking method ---
    static boolean isAsyncMethod(MethodNode methodNode) {
        if (hasAsyncAnnotation(methodNode)) {
            return true;
        }
        return false;
    }

    // --- Removing @async annotation
    /*
    static void removeAsyncAnnotation(MethodNode methodNode) {
        if (methodNode.invisibleAnnotations != null) {
            for (Iterator<AnnotationNode> it = invisibleAnnotationsOf(methodNode).iterator(); it.hasNext();) {
                AnnotationNode an = it.next();
                if (ASYNC_ANNOTATION_DESCRIPTOR.equals(an.desc)) {
                    it.remove();
                    log.debug("@async annotation removed, method: " + methodNode);
                    return;
                }
            }
        }
        throw new IllegalStateException("No @async annotation found to remove");
    }
    */

    @SuppressWarnings("unchecked")
    static List<MethodNode> methodsOf(ClassNode classNode) {
        return null == classNode.methods ? 
               Collections.<MethodNode> emptyList() : 
               (List<MethodNode>) classNode.methods;
    }

    @SuppressWarnings("unchecked")
    static List<InnerClassNode> innerClassesOf(ClassNode classNode) {
        return null == classNode.innerClasses ? 
               Collections.<InnerClassNode> emptyList() :
               (List<InnerClassNode>) classNode.innerClasses;
    }

    // --- Creating names ---
    static String createInnerClassName(ClassNode classNode) {
        int index = 1;
        String name;
        while (hasInnerClass(classNode, name = createInnerClassName(classNode, index))) {
            index++;
        }
        log.debug("Generated new inner class name: " + name);
        return name;
    }

    static String createAccessMethodName(List<MethodNode> methods) {
        int index = 0;
        String name;
        while (hasMethod(name = createAccessMethodName(index), methods)) {
            index++;
        }
        log.trace("Generated new method name: " + name);
        return name;
    }

    static String createOuterClassMethodArgFieldName(int index) {
        return "val$" + index;
    }

    // --- Finding methods ---
    static MethodNode getMethod(String methodName, String methodDesc, List<MethodNode> methods) {
        for (MethodNode methodNode : methods) {
            if (methodName.equals(methodNode.name) && (methodDesc == null || methodDesc.equals(methodNode.desc))) {
                return methodNode;
            }
        }
        return null;
    }

    static FieldNode getField(ClassNode classNode, String fieldName, String fieldDesc) {
        for (FieldNode fieldNode : fieldsOf(classNode)) {
            if (fieldName.equals(fieldNode.name) && (fieldDesc == null || fieldDesc.equals(fieldNode.desc))) {
                return fieldNode;
            }
        }
        return null;
    }

    private static String createInnerClassName(ClassNode classNode, int index) {
        return classNode.name + "$" + index;
    }

    private static String createAccessMethodName(int index) {
        return "access$" + index;
    }

    // --- Finding inner classes ---
    private static boolean hasInnerClass(ClassNode classNode, String innerClassName) {
        return getInnerClass(classNode, innerClassName) != null;
    }

    private static InnerClassNode getInnerClass(ClassNode classNode, String innerClassName) {
        for (InnerClassNode icn : innerClassesOf(classNode)) {
            if (innerClassName.equals(icn.name)) {
                return icn;
            }
        }
        return null;
    }

    private static boolean hasAsyncAnnotation(MethodNode methodNode) {
        boolean found = 
                annotationPresent(invisibleAnnotationsOf(methodNode), ASYNC_ANNOTATION_DESCRIPTOR) || 
                annotationPresent(visibleAnnotationsOf(methodNode), ASYNC_ANNOTATION_DESCRIPTOR);

        if (found) {
            log.debug("@Async annotation found, method: " + methodNode);
        }

        return found;
    }

    private static boolean annotationPresent(List<AnnotationNode> annotations, String targetAnnotationTypeDescriptor) {
        
        for (AnnotationNode annotation : annotations) {
            if (targetAnnotationTypeDescriptor.equals(annotation.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(String methodName, List<MethodNode> methods) {
        return getMethod(methodName, null, methods) != null;
    }

    @SuppressWarnings("unchecked")
    private static List<FieldNode> fieldsOf(ClassNode classNode) {
        return null == classNode.fields ? Collections.<FieldNode> emptyList() : (List<FieldNode>) classNode.fields;
    }

    static List<AnnotationNode> visibleAnnotationsOf(MethodNode methodNode) {
        return safeAnnotationsList(methodNode.visibleAnnotations);
    }
    
    @SuppressWarnings("unchecked")
    static List<AnnotationNode>[] visibleParameterAnnotationsOf(MethodNode methodNode) {
        return methodNode.visibleParameterAnnotations;
    }

    static List<TypeAnnotationNode> visibleTypeAnnotationsOf(MethodNode methodNode) {
        return safeAnnotationsList(methodNode.visibleTypeAnnotations);
    }
    
    static List<TypeAnnotationNode> visibleTypeAnnotationsOf(TryCatchBlockNode tryCatchBlockNode) {
        return safeAnnotationsList(tryCatchBlockNode.visibleTypeAnnotations);
    }

    static List<AnnotationNode> invisibleAnnotationsOf(MethodNode methodNode) {
        return safeAnnotationsList(methodNode.invisibleAnnotations);
    }
    
    @SuppressWarnings("unchecked")
    static List<AnnotationNode>[] invisibleParameterAnnotationsOf(MethodNode methodNode) {
        return methodNode.invisibleParameterAnnotations;
    }

    static List<TypeAnnotationNode> invisibleTypeAnnotationsOf(MethodNode methodNode) {
        return safeAnnotationsList(methodNode.invisibleTypeAnnotations);
    }

    static List<TypeAnnotationNode> invisibleTypeAnnotationsOf(TryCatchBlockNode tryCatchBlockNode) {
        return safeAnnotationsList(tryCatchBlockNode.invisibleTypeAnnotations);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> safeAnnotationsList(List<?> annotations) {
        return null == annotations ? Collections.<T> emptyList() : (List<T>) annotations;
    }
}
