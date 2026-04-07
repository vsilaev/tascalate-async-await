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

import static net.tascalate.async.tools.core.AnnotationIntrospection.annotationPresent;
import static net.tascalate.async.tools.core.AnnotationIntrospection.invisibleAnnotationsOf;
import static net.tascalate.async.tools.core.AnnotationIntrospection.visibleAnnotationsOf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tascalate.asmx.tree.ClassNode;
import net.tascalate.asmx.tree.FieldNode;
import net.tascalate.asmx.tree.MethodNode;

final class AsyncAwaitClassState {
    private static final Logger log = LoggerFactory.getLogger(AsyncAwaitClassState.class);
    
    final ClassNode classNode;
    
    private final BiPredicate<String, String> subclassCheck;
    private final Function<String, ClassNode> resolveClassNode;
    private final Map<String, List<String>> nestMemberRequest;
    
    private final Map<String, AsyncAwaitClassState> superclasses = new HashMap<>();

    // Original method's "method name + method desc" -> Access method's
    // MethodNode
    private final Map<String, MethodNode> accessMethods = new HashMap<>();
    private final Map<String, MethodNode> allMethods;
    private final Map<String, FieldNode> allFields;
    private final Set<String> accessMethodNames;
    private final Set<String> innerClassNames;

    AsyncAwaitClassState(ClassNode classNode, BiPredicate<String, String> subclassCheck, Function<String, ClassNode> resolveClassNode, Map<String, List<String>> nestMemberRequest) {
        this(classNode, subclassCheck, resolveClassNode, nestMemberRequest, false);
    }
    
    AsyncAwaitClassState(ClassNode classNode, BiPredicate<String, String> subclassCheck, Function<String, ClassNode> resolveClassNode, Map<String, List<String>> nestMemberRequest, boolean minimal) {
        this.classNode = classNode;
        this.subclassCheck = subclassCheck;
        this.resolveClassNode = resolveClassNode;
        this.nestMemberRequest = nestMemberRequest;
        
        if (classNode.innerClasses == null || minimal) {
            classNode.innerClasses = new ArrayList<>();
            innerClassNames = new HashSet<>();
        } else {
            innerClassNames = classNode.innerClasses
                                       .stream()
                                       .map(icn -> icn.name)
                                       .collect(Collectors.toSet());
        }
        
        if (classNode.methods == null) {
            classNode.methods = new ArrayList<>();
            allMethods = new HashMap<>();
            accessMethodNames = new HashSet<>();
        } else {
            allMethods = classNode.methods
                                  .stream()
                                  .collect(Collectors.toMap(m -> m.name + '#' + m.desc, Function.identity()));
            
            accessMethodNames = classNode.methods
                                         .stream()
                                         .map(mn -> mn.name)
                                         .filter(n -> n.startsWith("access$"))
                                         .collect(Collectors.toSet());
        }
        
        if (classNode.fields == null) {
            classNode.fields = new ArrayList<>();
            allFields = new HashMap<>();
        } else {
            allFields = classNode.fields
                                 .stream()
                                 .collect(Collectors.toMap(f -> f.name + '#' + f.desc, Function.identity()));
        }
    }
    
    FieldNode getField(String fieldName, String fieldDesc) {
        return allFields.get(fieldName + '#' + fieldDesc);
    }
    
    void putMethod(MethodNode method, boolean addToMethods) {
        MethodNode existing = allMethods.put(method.name + '#' + method.desc, method);
        if (null != existing) {
            classNode.methods.remove(existing);
        }
        
        if (addToMethods) {
            classNode.methods.add(method);
        }
        
        if (method.name.startsWith("access$")) {
            accessMethodNames.add(method.name);
        }
    }
    
    MethodNode getMethod(String methodName, String methodDesc) {
        return allMethods.get(methodName + '#' + methodDesc);
    }
    
    String createAccessMethodName() {
        int index = 0;
        String name;
        while (accessMethodNames.contains(name = "access$" + index)) {
            index++;
        }
        if (log.isDebugEnabled()) {
            log.trace("Generated new method name: " + name);
        }
        return name;
    }
    
    String generateAndRegisterInnerClassName() {
        int index = 0;
        String name;
        while (innerClassNames.contains(name = classNode.name + '$' + index)) {
            index++;
        }
        if (log.isDebugEnabled()) {
            log.debug("Generated new inner class name: " + name);
        }
        innerClassNames.add(name);
        return name;
    }
    
    AsyncAwaitClassState superclass(String className) {
        return superclasses.computeIfAbsent(className, cn -> superclass(resolveClassNode.apply(cn)));
    }
    
    private AsyncAwaitClassState superclass(ClassNode classNode) {
        if (classNode == this.classNode || classNode.name.equals(this.classNode.name)) {
            return this;
        }
        return new AsyncAwaitClassState(classNode, subclassCheck, resolveClassNode, nestMemberRequest, true);
    }
    
    boolean isSubClassOf(String maybeParentClass) {
        return subclassCheck.test(classNode.name, maybeParentClass);
    }
    
    void registerAccessMethod(String owner, String name, String desc, String kind, MethodNode methodNode) {
        accessMethods.put(owner + '#' + name + '#' + desc + '-' + kind, methodNode);
        putMethod(methodNode, true);
    }

    MethodNode getAccessMethod(String owner, String name, String desc, String kind) {
        return accessMethods.get(owner + '#' + name + '#' + desc + '-' + kind);
    }
    
    boolean supportsNestMemeber() {
        return null != nestMemberRequest;
    }
    
    void needNestMemeber(String ownerInternalName, String requestorInternalName) {
        if (null == nestMemberRequest) {
            throw new IllegalStateException("Memeber nesting requested but not supported");
        }
        nestMemberRequest.merge(ownerInternalName, Collections.singletonList(requestorInternalName), (a, b) -> {
            if (a == null || a.isEmpty()) {
                return b;
            } else if (b == null || b.isEmpty()) {
                return a;
            }
            Set<String> merged = new HashSet<>(a);
            merged.addAll(b);
            return new ArrayList<>(merged);
        });
    }

    boolean isAsyncMethod(MethodNode methodNode) {
        return hasAsyncAnnotation(methodNode);
    }
    
    private static boolean hasAsyncAnnotation(MethodNode methodNode) {
        boolean found = 
                annotationPresent(invisibleAnnotationsOf(methodNode), AbstractAsyncMethodTransformer.ASYNC_ANNOTATION_DESCRIPTOR) || 
                annotationPresent(visibleAnnotationsOf(methodNode), AbstractAsyncMethodTransformer.ASYNC_ANNOTATION_DESCRIPTOR);

        if (found && log.isDebugEnabled()) {
            log.debug("@Async annotation found, method: " + methodNode);
        }

        return found;
    }
}
