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
package net.tascalate.async.tools.maven;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import net.tascalate.async.tools.core.ToolsHelper;

/**
 * Maven plugin that will apply Tascalate Async class transformations on
 * compiled classes (bytecode instrumentation).
 * <p>
 * Example plugin configuration :
 * </p>
 * 
 * <pre>
 *   &lt;configuration&gt;
 *       &lt;skip&gt;true&lt;/skip&gt;
 *       &lt;includeTestClasses&gt;false&lt;/includeTestClasses&gt;
 *       &lt;buildDir&gt;bin/classes&lt;/buildDir&gt;
 *       &lt;testBuildDir&gt;bin/test-classes&lt;/testBuildDir&gt;
 *   &lt;/configuration&gt;
 * </pre>
 * 
 */
@Mojo(name = "tascalate-async-enhance", 
      threadSafe = true,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES, 
      requiresDependencyResolution = ResolutionScope.TEST /* ALL DEPENDENCIES */)
public class AsyncAwaitEnhancerMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", property = "tascalate-async.enhancer.project", required = true, readonly = true)
    private MavenProject project;

    /** Skips all processing performed by this goal. */
    @Parameter(defaultValue = "false", property = "tascalate-async.enhancer.skip", required = false)
    private boolean skip;

    @Parameter(defaultValue = "true", property = "tascalate-async.enhancer.includeTestClasses", required = true)
    /** Whether or not to include test classes to be processed by enhancer. */
    private Boolean includeTestClasses;

    /**
     * Allows to customize the build directory of the project, used for both
     * finding classes to transform and outputing them once transformed. By
     * default, equals to maven's project output directory. Path must be either
     * absolute or relative to project base dir.
     */
    @Parameter(property = "tascalate-async.enhancer.buildDir", required = false)
    private String buildDir;

    /**
     * Allows to customize the build directory of the tests of the project, used
     * for both finding classes to transform and outputing them once
     * transformed. By default, equals to maven's project test output directory.
     * Path must be either absolute or relative to project base dir.
     */
    @Parameter(property = "tascalate-async.enhancer.testBuildDir", required = false)
    private String testBuildDir;
    
    @Component
    private MojoExecution execution;

    public void execute() throws MojoExecutionException {
        Log log = getLog();
        if (skip) {
            log.info("Skipping executing.");
            return;
        }

        try {
            File mainInputDirectory = buildDir == null ? 
                new File(project.getBuild().getOutputDirectory()) : computeDir(buildDir);

            if (mainInputDirectory.exists()) {
                // Use runtime instead of compile - runtime contains non less than compile
                Set<String> cp = new HashSet<String>();
                cp.addAll(project.getCompileClasspathElements());  
                cp.addAll(project.getRuntimeClasspathElements());  

                instrument(mainInputDirectory, cp); 
            } else {
                log.warn("No main build output directory available, skip enhancing main classes");
            }

            if (includeTestClasses) {
                File testInputDirectory = testBuildDir == null ? 
                    new File(project.getBuild().getTestOutputDirectory()) : computeDir(testBuildDir);
                
                if (testInputDirectory.exists()) {
                    instrument(testInputDirectory, project.getTestClasspathElements()); 
                } else if ("process-test-classes".equals(execution.getLifecyclePhase())) {
                    log.warn("No test build output directory available, skip enhancing test classes");
                }                
            }
        } catch (Exception e) {
            getLog().error(e.getMessage(), e);
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
    
    private void instrument(File inputDirectory, Collection<String> classPathEntries) throws IOException {
        Log log = getLog();
        List<URL> classPath = new ArrayList<URL>();
        for (String classPathEntry : classPathEntries) {
            classPath.add(resolveUrl(new File(classPathEntry)));
        }
        classPath.add(resolveUrl(inputDirectory));

        ToolsHelper.transformFiles(inputDirectory, 
                                   ToolsHelper.createGenerator(classPath), 
                                   log::debug, log::info);
    }

    private File computeDir(String dir) {
        File dirFile = new File(dir);
        if (dirFile.isAbsolute()) {
            return dirFile;
        } else {
            return new File(project.getBasedir(), buildDir).getAbsoluteFile();
        }
    }

    private URL resolveUrl(File resource) {
        try {
            return resource.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public boolean isSkip() {
        return skip;
    }

    public Boolean getIncludeTestClasses() {
        return includeTestClasses;
    }

    public String getBuildDir() {
        return buildDir;
    }

    public String getTestBuildDir() {
        return testBuildDir;
    }

}
