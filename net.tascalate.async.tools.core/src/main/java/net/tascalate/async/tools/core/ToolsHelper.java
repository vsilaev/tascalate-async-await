/**
 * ﻿Copyright 2015-2022 Valery Silaev (http://vsilaev.com)
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

import static java.lang.Thread.currentThread;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.javaflow.spi.ClasspathResourceLoader;
import org.apache.commons.javaflow.spi.RecursiveFilesIterator;

public class ToolsHelper {
    public static AsyncAwaitClassFileGenerator createGenerator(List<URL> classPath) {
        ClassLoader effectiveClassLoader = loadAdditionalClassPath(classPath);
        return new AsyncAwaitClassFileGenerator(
            new ClasspathResourceLoader(effectiveClassLoader)
       );
    }
    
    private static ClassLoader loadAdditionalClassPath(List<URL> classPath) {
        ClassLoader contextClassLoader = currentThread().getContextClassLoader();
        if (null == contextClassLoader) {
            contextClassLoader = ClassLoader.getSystemClassLoader();
        }

        if (classPath.isEmpty()) {
            return contextClassLoader;
        }

        URLClassLoader pluginClassLoader = URLClassLoader.newInstance(
            classPath.toArray(new URL[classPath.size()]), contextClassLoader
        );

        return pluginClassLoader;
    }

    public static void transformFiles(File inputDirectory, AsyncAwaitClassFileGenerator generator, Consumer<String> debug, Consumer<String> info) 
                                      throws IOException {
        long now = System.currentTimeMillis();

        for (File source : RecursiveFilesIterator.scanClassFiles(inputDirectory)) {
            if (source.lastModified() <= now) {
                debug.accept("Applying async/await support: " + source);
                boolean rewritten = rewriteClassFile(source, generator, source);
                if (rewritten) {
                    info.accept("Rewritten async-enabled class file: " + source);
                }
            }
        }
        
    }

    private static boolean rewriteClassFile(File source, AsyncAwaitClassFileGenerator generator, File target)
                                            throws IOException {
        
        byte[] original = toByteArray(source);

        try {
            byte[] transformed = generator.transform(original);
            if (transformed != original
                /* Exact equality means not transformed */ || !source.equals(target)) {
                writeFile(target, transformed != null ? transformed : original);
                if (transformed != original) {
                    Map<String, byte[]> extraClasses = generator.getGeneratedClasses();
                    for (Map.Entry<String, byte[]> e : renameInMemoryResources(extraClasses).entrySet()) {
                        writeFile(new File(target.getParentFile(), e.getKey()), e.getValue());
                    }
                }
                return true;
            } else {
                return false;
            }
        } finally {
            if (null != generator) {
                generator.reset();
            }
        }
    }

    private static Map<String, byte[]> renameInMemoryResources(Map<String, byte[]> generatedClasses) {
        Map<String, byte[]> resources = new HashMap<String, byte[]>();
        for (Map.Entry<String, byte[]> e : generatedClasses.entrySet()) {
            String name = e.getKey();
            int idx = name.lastIndexOf('/');
            if (idx >= 0 && idx < name.length() - 1) {
                name = name.substring(idx + 1);
            }
            resources.put(name + ".class", e.getValue());
        }
        return resources;
    }

    private static void writeFile(File target, byte[] content) throws IOException {
        Files.write(target.toPath(), content);
    }

    private static byte[] toByteArray(File source) throws IOException {
        return Files.readAllBytes(source.toPath());
    }
}
