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
package net.tascalate.async.tools.maven;

import static java.lang.Thread.currentThread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.IllegalClassFormatException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.javaflow.spi.ClasspathResourceLoader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import net.tascalate.async.tools.core.AsyncAwaitClassFileGenerator;

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
@Mojo(name = "tascalate-async-enhance", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
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

    public void execute() throws MojoExecutionException {
        Log log = getLog();
        if (skip) {
            log.info("Skipping executing.");
            return;
        }

        ClassLoader originalContextClassLoader = currentThread().getContextClassLoader();
        try {
            List<URL> classPath = new ArrayList<URL>();

            for (String runtimeResource : project.getRuntimeClasspathElements()) {
                classPath.add(resolveUrl(new File(runtimeResource)));
            }

            File inputDirectory = buildDir == null ? 
                new File(project.getBuild().getOutputDirectory()) : computeDir(buildDir);

            classPath.add(resolveUrl(inputDirectory));

            ClassLoader effectiveClassLoader = loadAdditionalClassPath(classPath);
            AsyncAwaitClassFileGenerator generator = new AsyncAwaitClassFileGenerator(
                new ClasspathResourceLoader(effectiveClassLoader)
            );

            // final ResourceTransformer dirTransformer =
            // RewritingUtils.createTransformer(
            // classPath.toArray(new URL[]{}), TransformerType.ASM5
            // );

            long now = System.currentTimeMillis();

            for (File source : RecursiveFilesIterator.scanClassFiles(inputDirectory)) {
                if (source.lastModified() <= now) {
                    log.debug("Applying async/await support: " + source);
                    boolean rewritten = rewriteClassFile(source, generator, source);
                    if (rewritten) {
                        log.info("Rewritten async-enabled class file: " + source);
                    }
                }
            }

            if (includeTestClasses) {
                File testInputDirectory = testBuildDir == null ? 
                    new File(project.getBuild().getTestOutputDirectory()) : computeDir(testBuildDir);

                if (testInputDirectory.exists()) {
                    for (File source : RecursiveFilesIterator.scanClassFiles(testInputDirectory)) {
                        if (source.lastModified() <= now) {
                            log.debug("Applying async/await support: " + source);
                            final boolean rewritten = rewriteClassFile(source, null, source);
                            if (rewritten) {
                                log.info("Rewritten async-enabled class file: " + source);
                            }

                        }
                    }
                }
            }
        } catch (Exception e) {
            getLog().error(e.getMessage(), e);
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    protected boolean rewriteClassFile(File source, AsyncAwaitClassFileGenerator generator, File target)
                                       throws IOException, IllegalClassFormatException {
        
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
            generator.reset();
        }
    }

    private ClassLoader loadAdditionalClassPath(List<URL> classPath) {
        ClassLoader contextClassLoader = currentThread().getContextClassLoader();

        if (classPath.isEmpty()) {
            return contextClassLoader;
        }

        URLClassLoader pluginClassLoader = URLClassLoader.newInstance(
            classPath.toArray(new URL[classPath.size()]), contextClassLoader
        );

        currentThread().setContextClassLoader(pluginClassLoader);
        return pluginClassLoader;
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
        try (FileOutputStream os = new FileOutputStream(target)) {
            os.write(content);
        }
    }

    private static byte[] toByteArray(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return toByteArray(in);
        }
    }

    private static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(in, baos);
        return baos.toByteArray();
    }

    private static int copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
            total += n;
        }
        return total;
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