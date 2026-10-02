/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.spring;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

class ExecutorConditions {
    
    private ExecutorConditions() {
        
    }
    
    static class UsePlatformThreads implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return JAVA_VERSION < 21 || !virtualThreadsEnabled(context.getEnvironment());
        }
    }
    
    static class UseVirtualThreads implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return JAVA_VERSION >= 21 && virtualThreadsEnabled(context.getEnvironment());
        }
    }

    
    private static int javaVersion() {
        // Use specification version instead of java.version
        String version = System.getProperty("java.specification.version");
        if (version == null || version.isEmpty()) return 0;

        // Handle legacy Java 8 ("1.8")
        if (version.startsWith("1.")) {
            // Safe because "1." is always followed by at least one digit
            int dot2 = version.indexOf('.', 2);
            if (dot2 == -1) {
                return Integer.parseInt(version.substring(2));
            }
            return Integer.parseInt(version.substring(2, dot2));
        }

        // Handle modern Java ("11", "21", "22-ea")
        // Just read digits until we hit a non-digit character
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) {
            end++;
        }
        
        return (end == 0) ? 0 : Integer.parseInt(version.substring(0, end));
    }
    
    private static boolean virtualThreadsEnabled(Environment environment) {
        String value = environment.getProperty(USE_VIRTUAL_THREADS_PROPERTY);

        // matchIfMissing = true
        if (value == null) {
            return true;
        }

        return Boolean.parseBoolean(value.trim());
    }
    
    private static final String USE_VIRTUAL_THREADS_PROPERTY =
            "async-await.executor.use-virtual-threads";
    
    private static int JAVA_VERSION;
    static {
        JAVA_VERSION = javaVersion();
    }
}
