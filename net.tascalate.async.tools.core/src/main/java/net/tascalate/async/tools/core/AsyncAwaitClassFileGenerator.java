
package net.tascalate.async.tools.core;

import static net.tascalate.async.tools.core.BytecodeIntrospection.innerClassesOf;
import static net.tascalate.async.tools.core.BytecodeIntrospection.isAsyncMethod;
import static net.tascalate.async.tools.core.BytecodeIntrospection.methodsOf;

import java.lang.instrument.IllegalClassFormatException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.javaflow.spi.ResourceLoader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

public class AsyncAwaitClassFileGenerator {

    private final static Log log = LogFactory.getLog(AsyncAwaitClassFileGenerator.class);
    private final static Type TYPE_COMPLETION_STAGE = Type.getObjectType("java/util/concurrent/CompletionStage");
    private final static Type TYPE_TASCALATE_PROMISE = Type.getObjectType("net/tascalate/concurrent/Promise");
    
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
                Type returnType = Type.getReturnType(methodNode.desc);
                AbstractMethodTransformer transformer = null;
                if (TYPE_COMPLETION_STAGE.equals(returnType) || TYPE_TASCALATE_PROMISE.equals(returnType) || Type.VOID_TYPE.equals(returnType)) {
                    transformer = new AsynResultMethodTransformer(classNode, originalInnerClasses, methodNode, newClasses, accessMethods);
                }
                if (null != transformer) {
                    transformer.transform();
                    transformed = true;
                }
            }
        }
        return transformed;
    }
}
