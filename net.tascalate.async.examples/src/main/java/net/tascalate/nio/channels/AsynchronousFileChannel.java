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
package net.tascalate.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import net.tascalate.concurrent.Promise;

public class AsynchronousFileChannel extends java.nio.channels.AsynchronousFileChannel {

    protected final java.nio.channels.AsynchronousFileChannel delegate;

    public static AsynchronousFileChannel open(Path file, OpenOption... options) throws IOException {
        return new AsynchronousFileChannel(java.nio.channels.AsynchronousFileChannel.open(file, options));
    }

    public static AsynchronousFileChannel open(
            Path file,
            Set<? extends OpenOption> options,
            ExecutorService executor,
            FileAttribute<?>... attrs) throws IOException {
        return new AsynchronousFileChannel(java.nio.channels.AsynchronousFileChannel.open(file, options, executor, attrs));
    }

    protected AsynchronousFileChannel(java.nio.channels.AsynchronousFileChannel delegate) {
        this.delegate = delegate;
    }

    public boolean isOpen() {
        return delegate.isOpen();
    }

    public void close() throws IOException {
        delegate.close();
    }

    public long size() throws IOException {
        return delegate.size();
    }

    public AsynchronousFileChannel truncate(long size) throws IOException {
        delegate.truncate(size);
        return this;
    }

    public void force(boolean metaData) throws IOException {
        delegate.force(metaData);
    }

    public <A> void lock(long position, long size, boolean shared, A attachment,
            CompletionHandler<FileLock, ? super A> handler) {
        delegate.lock(position, size, shared);
    }

    public Promise<FileLock> lockAll() {
        return lockAll(false);
    }

    public Promise<FileLock> lockAll(boolean shared) {
        return doLock(0, Long.MAX_VALUE, shared);
    }

    public Promise<FileLock> lock(long position, long size, boolean shared) {
        return doLock(position, size, shared);
    }

    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        return delegate.tryLock(position, size, shared);
    }

    public <A> void read(ByteBuffer dst, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
        delegate.read(dst, position, attachment, handler);
    }

    public Promise<Integer> read(ByteBuffer dst, long position) {
        AsyncResult<Integer> asyncResult = new AsyncResult<>();
        delegate.read(dst, position, null, asyncResult.handler);
        return asyncResult;
    }

    public <A> void write(ByteBuffer src, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
        delegate.write(src, position, attachment, handler);
    }

    public Promise<Integer> write(ByteBuffer src, long position) {
        AsyncResult<Integer> asyncResult = new AsyncResult<>();
        delegate.write(src, position, null, asyncResult.handler);
        return asyncResult;
    }

    protected Promise<FileLock> doLock(long position, long size, boolean shared) {
        AsyncResult<FileLock> asyncResult = new AsyncResult<>();
        delegate.lock(position, size, shared, null, asyncResult.handler);
        return asyncResult;
    }
}
