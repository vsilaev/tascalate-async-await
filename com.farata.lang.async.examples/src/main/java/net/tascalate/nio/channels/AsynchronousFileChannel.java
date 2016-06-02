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

import net.tascalate.concurrent.CompletionFuture;

public class AsynchronousFileChannel extends java.nio.channels.AsynchronousFileChannel {

    final protected java.nio.channels.AsynchronousFileChannel delegate;

    public static AsynchronousFileChannel open(Path file, OpenOption... options) throws IOException {
        return new AsynchronousFileChannel(java.nio.channels.AsynchronousFileChannel.open(file, options));
    }

    public static AsynchronousFileChannel open(Path file,
            Set<? extends OpenOption> options,
            ExecutorService executor,
            FileAttribute<?>... attrs) throws IOException {
        return new AsynchronousFileChannel(java.nio.channels.AsynchronousFileChannel.open(file, options, executor, attrs));
    }

    protected AsynchronousFileChannel(final java.nio.channels.AsynchronousFileChannel delegate) {
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
        doLock(position, size, shared, attachment, handler);
    }

    public CompletionFuture<FileLock> lockAll() {
        return lockAll(false);
    }

    public CompletionFuture<FileLock> lockAll(boolean shared) {
        return doLock(0, Long.MAX_VALUE, shared, null, null);
    }

    public CompletionFuture<FileLock> lock(long position, long size, boolean shared) {
        return doLock(position, size, shared, null, null);
    }

    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        return delegate.tryLock(position, size, shared);
    }

    public <A> void read(ByteBuffer dst, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
        doRead(dst, position, attachment, handler);
    }

    public CompletionFuture<Integer> read(ByteBuffer dst, long position) {
        return doRead(dst, position, null, null);
    }

    public <A> void write(ByteBuffer src, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
        doWrite(src, position, attachment, handler);
    }

    public CompletionFuture<Integer> write(ByteBuffer src, long position) {
        return doWrite(src, position, null, null);
    }

    protected <A> CompletionFuture<FileLock> doLock(final long position, 
            final long size, 
            final boolean shared, 
            final A attachment,
            final CompletionHandler<FileLock, ? super A> handler) {

        final AsyncResult<FileLock, ? super A> asyncResult = new AsyncResult<>(handler);
        delegate.lock(position, size, shared, attachment, asyncResult.handler);
        return asyncResult;
    }

    protected <A> CompletionFuture<Integer> doWrite(final ByteBuffer src, 
            final long position, 
            final A attachment, 
            final CompletionHandler<Integer, ? super A> handler) {
        final AsyncResult<Integer, ? super A> asyncResult = new AsyncResult<>(handler);
        delegate.write(src, position, attachment, asyncResult.handler);
        return asyncResult;
    }

    protected <A> CompletionFuture<Integer> doRead(final ByteBuffer dst, 
            final long position, 
            final A attachment, 
            final CompletionHandler<Integer, ? super A> handler) {
        final AsyncResult<Integer, ? super A> asyncResult = new AsyncResult<>(handler);
        delegate.read(dst, position, attachment, asyncResult.handler);
        return asyncResult;
    }
}
