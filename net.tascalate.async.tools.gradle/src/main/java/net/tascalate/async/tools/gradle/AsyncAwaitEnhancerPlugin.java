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
package net.tascalate.async.tools.gradle;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;

import net.tascalate.async.tools.core.AsyncAwaitClassFileGenerator;
import net.tascalate.async.tools.core.ToolsHelper;

/**
 * Gradle plugin that will apply Async/Await class transformations on compiled
 * classes (bytecode instrumentation).
 * <p>
 * Example plugin configuration :
 * </p>
 * 
 * <pre>
 * buildscript {
 *     repositories {
 *         mavenCentral() // change this to mavenLocal() if you're testing a local build of this plugin
 *     }
 * 
 *     dependencies {
 *         classpath group: 'net.tascalate.async',  name: 'net.tascalate.async.tools.gradle',  version: 'PUT_CORRECT_VERSION_HERE'
 *         classpath group: 'net.tascalate.javaflow',  name: 'net.tascalate.javaflow.tools.gradle',  version: 'PUT_CORRECT_VERSION_HERE'
 *     }
 * }
 * 
 * apply plugin: "java"
 * apply plugin: "async-await"
 * apply plugin: "continuations"
 * 
 * continuations {
 *     // skip = true
 *     // includeTestClasses = false 
 * }
 * 
 * repositories {
 *     mavenCentral()
 * }
 * 
 * dependencies {
 *     compile group: 'net.tascalate.async', name: 'net.tascalate.async.runtime', version: 'PUT_CORRECT_VERSION_HERE'
 * }
 * </pre>
 * 
 */

public class AsyncAwaitEnhancerPlugin  implements Plugin<Project> {
    
    private Logger log;

    @Override
    public void apply(Project target) {
        AsyncAwaitEnhancerPluginConfiguration config = new AsyncAwaitEnhancerPluginConfiguration();
        target.getExtensions().add("async-await", config);
        
        log = target.getLogger();

        Set<Task> compileJavaTasks = target.getTasksByName("compileJava", true);
        for (Task task : compileJavaTasks) {
            addInstrumentActionToTask("main", task, config);
        }

        Set<Task> compileJavaTestTasks = target.getTasksByName("compileTestJava", true);
        for (Task task : compileJavaTestTasks) {
            addInstrumentActionToTask("test", task, config);
        }
    }    
    
    private void addInstrumentActionToTask(String sourceType, Task task, AsyncAwaitEnhancerPluginConfiguration config) {
        task.doLast(new Action<Task>() {
            @Override
            public void execute(Task arg0) {
                if (config.isSkip()) {
                    log.info("Skipping execution.");
                    return;
                }
                
                if ("test".equals(sourceType) && !config.isIncludeTestClasses()) {
                    log.info("Skipping execution on test classes.");
                    return;
                }

                try {
                    Project project = task.getProject();
                    SourceSetContainer sourceSetsContainer = (SourceSetContainer)project.getProperties().get("sourceSets");
                    SourceSet sourceSet = sourceSetsContainer.getByName(sourceType);
                    if (null != sourceSet) {
                        SourceSetOutput output = sourceSet.getOutput();
                        Set<File> classesDirs = output.getClassesDirs().getFiles();

                        // If runtime classpath would be necessary
                        /*
                        FileCollection compileClasspath = sourceSet.getCompileClasspath();
                        FileCollection runtimeClasspath = sourceSet.getRuntimeClasspath();
                        instrument(classesDirs, (null == runtimeClasspath ? compileClasspath : compileClasspath.plus(runtimeClasspath)).getFiles(), config);
                        */
                        Set<File> compileClasspath = sourceSet.getCompileClasspath().getFiles();
                        instrument(classesDirs, compileClasspath, config);
                    }
                } catch (Exception e) {
                    log.log(LogLevel.ERROR, "Coroutines instrumentation failed", e);
                    throw new IllegalStateException("Coroutines instrumentation failed" , e);
                }
            }
        });
    }

    private void instrument(Set<File> classesDirs, Set<File> compileClasspath, AsyncAwaitEnhancerPluginConfiguration config) {
        try {
            log.debug("Getting compile classpath");
            List<URL> classPath = new ArrayList<URL>();
            classPath.addAll(urlsOf(classesDirs));
            classPath.addAll(urlsOf(compileClasspath));
            
            log.debug("Classpath for instrumentation is as follows: " + classPath);
            AsyncAwaitClassFileGenerator generator = ToolsHelper.createGenerator(classPath);
            try {
                for (File inputDir : classesDirs) {
                    if (!inputDir.isDirectory()) {
                        continue;
                    }                
                    ToolsHelper.transformFiles(inputDir, generator, log::debug, log::info);
                }
            } finally {
                generator.reset();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to instrument", ex);
        }
    }
    
    private List<URL> urlsOf(Set<File> files) {
        if (null == files) {
            return Collections.emptyList();
        }
        List<URL> result = new ArrayList<URL>();
        for (File file : files) {
            URL url = resolveUrl(file);
            if (null == url) {
                continue;
            }                
            result.add(url);
        }
        return result.isEmpty() ? Collections.<URL>emptyList() : Collections.unmodifiableList(result);
    }

    private URL resolveUrl(File resource) {
        try {
            return resource.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
