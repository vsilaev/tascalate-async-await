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
package net.tascalate.async.spi;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class MethodDefinition {
    private final String name;
    private final Class<?> returnType;
    private final Class<?>[] argumentTypes;
    
    private MethodDefinition(String name, Class<?> returnType, Class<?>[] argumentTypes) {
        this.name = name;
        this.returnType = returnType;
        this.argumentTypes = argumentTypes;
    }
    
    public static MethodDefinition create(String name, Class<?> returnType, Class<?>... argumentTypes) {
        return new MethodDefinition(name, returnType, argumentTypes);
    }

    public String getName() {
        return name;
    }

    public Class<?> getReturnType() {
        return returnType;
    }

    public Class<?>[] getArgumentTypes() {
        return argumentTypes;
    }
    
    @Override
    public String toString( ) {
        return returnType.getName() + " " + name + 
               "(" + Stream.of(argumentTypes)
                           .map(Class::getName)
                           .collect(Collectors.joining(", ")) + ")";
    }
}
