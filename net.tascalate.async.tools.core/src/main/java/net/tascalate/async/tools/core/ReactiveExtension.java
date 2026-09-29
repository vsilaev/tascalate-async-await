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

import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.tascalate.asmx.Type;

final class ReactiveExtension {
    
    final ReactiveTypeCardinality cardinality;
    final Type reactiveType;
    final Type implementation;
    final String method;
    final URL declaringResource;
    
    ReactiveExtension(ReactiveTypeCardinality cardinality, Type reactiveType, Type implementation, String method, URL declaringResource) {
        this.cardinality = cardinality;
        this.reactiveType = reactiveType;
        this.implementation = implementation;
        this.method = method;
        this.declaringResource = declaringResource;
    }
    
    String implementationInternalName() {
        return implementation.getInternalName();
    }
    
    static ReactiveExtension parse(String line, URL declaringResource, int lineIdx) throws IOException {
        Matcher m = PATTERN.matcher(line);
        if (!m.matches()) {
            throw new IOException(String.format(
                "Input line %s does not match the expected pattern: %s\nResource: %s", lineIdx, line, declaringResource.toExternalForm()
            ));
        }
        String cardinalityText = m.group(1);
        ReactiveTypeCardinality cardinality;
        if ("1".equals(cardinalityText)) {
            cardinality = ReactiveTypeCardinality.ONE;
        } else if ("M".equalsIgnoreCase(cardinalityText)) {
            cardinality = ReactiveTypeCardinality.MANY;
        } else {
            throw new IllegalStateException("Unexpected reactive type cardinality: " + cardinalityText);
        }
        String reactiveType = m.group(2).replace('.', '/');
        String implementation = m.group(3).replace('.', '/');
        String method = m.groupCount() > 3 ? m.group(4) : null;
        if (cardinality == ReactiveTypeCardinality.ONE && (method == null || method.length() == 0)) {
            throw new IOException(String.format(
                "Input line %s does not define #<async-method> required for single-cardinality reactive type: %s\nResource: %s", lineIdx, line, declaringResource.toExternalForm()
            ));
        }
        return new ReactiveExtension(cardinality, Type.getObjectType(reactiveType), Type.getObjectType(implementation), method, declaringResource);
    }
    
    private static final Pattern PATTERN = Pattern.compile(
        "^\\s*(1|M)\\s*\\:\\s*((?:[a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*)\\s*=\\s*((?:[a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*)(?:\\s*#\\s*([a-zA-Z_$][a-zA-Z\\d_$]*))?$");
}
