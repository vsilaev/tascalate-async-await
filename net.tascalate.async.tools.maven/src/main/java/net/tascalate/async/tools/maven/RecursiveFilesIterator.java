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

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class RecursiveFilesIterator implements Iterator<File> {

    public static final FileFilter ANY_READABLE_FILE = 
            f -> f.exists() && f.isFile() && f.canRead();

    public static final FileFilter CLASS_FILE = 
            f -> ANY_READABLE_FILE.accept(f) && f.getName().endsWith(".class");

    private static final FileFilter DIRECTORY = 
            f -> f.exists() && f.isDirectory() && f.canRead();

    private final File rootDir;
    private final FileFilter fileFilter;

    private boolean usedFiles = false;
    private boolean usedDirs = false;
    private Iterator<File> currentDelegate;
    private Iterator<File> currentDirs;

    public static Iterable<File> scanClassFiles(File rootDir) {
        return scanFiles(rootDir, CLASS_FILE);
    }

    public static Iterable<File> scanFiles(File rootDir, FileFilter fileFilter) {
        return () -> new RecursiveFilesIterator(rootDir, fileFilter);
    }

    public RecursiveFilesIterator(File rootDir, FileFilter fileFilter) {
        if (null == rootDir) {
            throw new IllegalArgumentException("Directory parameter may not be null");
        }
        if (!DIRECTORY.accept(rootDir)) {
            throw new IllegalArgumentException(rootDir + " is not an existing readable directory");
        }
        this.rootDir = rootDir;
        this.fileFilter = fileFilter;
    }

    protected void setupDelegate() {
        if (!usedFiles) {
            usedFiles = true;
            File[] files = rootDir.listFiles(fileFilter);
            if (files != null && files.length > 0) {
                currentDelegate = Arrays.asList(files).iterator();
            } else {
                currentDelegate = Collections.<File> emptySet().iterator();
            }
        }

        if (!currentDelegate.hasNext()) {
            if (!usedDirs) {
                usedDirs = true;
                File[] dirs = rootDir.listFiles(DIRECTORY);
                if (dirs != null && dirs.length > 0) {
                    currentDirs = Arrays.asList(dirs).iterator();
                } else {
                    currentDirs = Collections.<File>emptySet().iterator();
                }
            }

            while (!currentDelegate.hasNext() && currentDirs.hasNext()) {
                currentDelegate = new RecursiveFilesIterator(currentDirs.next(), fileFilter);
            }
        }
    }

    public boolean hasNext() {
        setupDelegate();
        return null != currentDelegate && currentDelegate.hasNext();
    }

    public File next() {
        setupDelegate();
        if (null != currentDelegate) {
            return currentDelegate.next();
        } else {
            throw new NoSuchElementException();
        }
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

}
