package net.tascalate.async.tools.core;

import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.ISTORE;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

class BytecodeIntrospection {
    private final static String ASYNC_ANNOTATION_DESCRIPTOR = "Lnet/tascalate/async/api/async;";
    private final static Log log = LogFactory.getLog(BytecodeIntrospection.class);

    private BytecodeIntrospection() {
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

    private static List<AnnotationNode> visibleAnnotationsOf(MethodNode methodNode) {
        return safeAnnotationsList(methodNode.visibleAnnotations);
    }

    private static List<AnnotationNode> invisibleAnnotationsOf(MethodNode methodNode) {
        return safeAnnotationsList(methodNode.invisibleAnnotations);
    }

    @SuppressWarnings("unchecked")
    private static List<AnnotationNode> safeAnnotationsList(List<?> annotations) {
        return null == annotations ? Collections.<AnnotationNode> emptyList() : (List<AnnotationNode>) annotations;
    }
}
