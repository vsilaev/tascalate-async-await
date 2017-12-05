package net.tascalate.async.tools.core;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * @author
 */
class BytecodeTraceUtil {

    public static String toString(byte[] clazz) {
        StringWriter strOut = new StringWriter();
        PrintWriter out = new PrintWriter(strOut);
        ClassVisitor cv = new TraceClassVisitor(out);

        ClassReader cr = new ClassReader(clazz);
        cr.accept(cv, Opcodes.ASM5);

        strOut.flush();
        return strOut.toString();
    }

    public static String toString(ClassNode cn) {
        StringWriter strOut = new StringWriter();
        PrintWriter out = new PrintWriter(strOut);

        cn.accept(new TraceClassVisitor(out));

        strOut.flush();
        return strOut.toString();
    }

    public static String toString(MethodNode mn) {
         Textifier t = new Textifier();
         TraceMethodVisitor tmv = new TraceMethodVisitor(t);
         mn.accept(tmv);
         return t.toString();
    }

    public static String toString(VarInsnNode vin) {
        // return AbstractVisitor.OPCODES[vin.getOpcode()] + " " + vin.var;
        return vin.getOpcode() + " " + vin.var;
    }

    public static String toString(MethodInsnNode min) {
        // return AbstractVisitor.OPCODES[min.getOpcode()] + " " + min.owner + "
        // " + min.name + " " + min.desc;
        return min.getOpcode() + " " + min.owner + " " + min.name + " " + min.desc;
    }

}
