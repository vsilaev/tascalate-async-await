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
package net.tascalate.async.tools.core;

import net.tascalate.asmx.ClassReader;
import net.tascalate.asmx.ClassWriter;

public class OfflineClassWriter extends ClassWriter {
    private final ClassHierarchy classHierarchy;
    
    public OfflineClassWriter(ClassHierarchy classHierarchy, int flags) {
        super(flags);
        this.classHierarchy = classHierarchy;
    }
    
    public OfflineClassWriter(ClassHierarchy classHierarchy, ClassReader reader, int flags) {
        super(reader, flags);
        this.classHierarchy = classHierarchy;
    }
    
    @Override
    protected String getCommonSuperClass(final String type1, final String type2) {
        return classHierarchy.getCommonSuperClass(type1, type2);
    }
    
    @Override
    protected ClassLoader getClassLoader() {
        throw new UnsupportedOperationException();
    }
}
