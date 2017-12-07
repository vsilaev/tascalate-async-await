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
