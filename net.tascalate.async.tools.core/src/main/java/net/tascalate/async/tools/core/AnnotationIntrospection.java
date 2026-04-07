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

import java.util.Collections;
import java.util.List;

import net.tascalate.asmx.tree.AnnotationNode;
import net.tascalate.asmx.tree.MethodNode;
import net.tascalate.asmx.tree.TryCatchBlockNode;
import net.tascalate.asmx.tree.TypeAnnotationNode;

class AnnotationIntrospection {
    
    private AnnotationIntrospection() {
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

    static boolean annotationPresent(List<AnnotationNode> annotations, String targetAnnotationTypeDescriptor) {
        for (AnnotationNode annotation : annotations) {
            if (targetAnnotationTypeDescriptor.equals(annotation.desc)) {
                return true;
            }
        }
        return false;
    }

    static List<AnnotationNode> visibleAnnotationsOf(MethodNode methodNode) {
        return safeAnnotationsList(methodNode.visibleAnnotations);
    }
    
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
