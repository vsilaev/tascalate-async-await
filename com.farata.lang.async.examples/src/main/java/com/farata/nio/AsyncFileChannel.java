package com.farata.nio;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;

import java.util.Set;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class AsyncFileChannel extends AsynchronousFileChannel {

	final protected AsynchronousFileChannel delegate;
	
	public AsyncFileChannel(final Path file, final OpenOption... options) throws IOException {
		this(AsynchronousFileChannel.open(file, options));
	}
	
	public AsyncFileChannel(final Path file,
			                final Set<? extends OpenOption> options,
			                final ExecutorService executor,
			                final FileAttribute<?>... attrs) throws IOException {
		
		this(AsynchronousFileChannel.open(file, options, executor, attrs));
	}
	
	public AsyncFileChannel(final AsynchronousFileChannel delegate) {
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
		return delegate.truncate(size);
	}

	public void force(boolean metaData) throws IOException {
		delegate.force(metaData);
	}

	public <A> void lock(long position, long size, boolean shared, A attachment,
			CompletionHandler<FileLock, ? super A> handler) {
		doLock(position, size, shared, attachment, handler);
	}

	public CompletableFuture<FileLock> lockAll() {
		return lockAll(false);
	}
	
	public CompletableFuture<FileLock> lockAll(boolean shared) {
		return doLock(0, Long.MAX_VALUE, shared, null, null);
	}
	
	public CompletableFuture<FileLock> lock(long position, long size, boolean shared) {
		return doLock(position, size, shared, null, null);
	}

	public FileLock tryLock(long position, long size, boolean shared) throws IOException {
		return delegate.tryLock(position, size, shared);
	}

	public <A> void read(ByteBuffer dst, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
		doRead(dst, position, attachment, handler);
	}

	public CompletableFuture<Integer> read(ByteBuffer dst, long position) {
		return doRead(dst, position, null, null);
	}

	public <A> void write(ByteBuffer src, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
		doWrite(src, position, attachment, handler);
	}

	public CompletableFuture<Integer> write(ByteBuffer src, long position) {
		return doWrite(src, position, null, null);
	}
	
	protected <A> CompletableFuture<FileLock> doLock(final long position, 
	                                                 final long size, 
	                                                 final boolean shared, 
	                                                 final A attachment,
	                                                 final CompletionHandler<FileLock, ? super A> handler) {
		
		final CompletableFuture<FileLock> asyncResult = new CompletableFuture<>();
		delegate.lock(position, size, shared, attachment, new AsyncResultCompletionHandler<>(asyncResult, handler));
		return asyncResult;
	}
	
	protected <A> CompletableFuture<Integer> doWrite(final ByteBuffer src, 
	                                                 final long position, 
	                                                 final A attachment, 
	                                                 final CompletionHandler<Integer, ? super A> handler) {
		final CompletableFuture<Integer> asyncResult = new CompletableFuture<>();
		delegate.write(src, position, attachment, new AsyncResultCompletionHandler<>(asyncResult, handler));
		return asyncResult;
	}
	
	protected <A> CompletableFuture<Integer> doRead(final ByteBuffer dst, 
	                                                final long position, 
	                                                final A attachment, 
	                                                final CompletionHandler<Integer, ? super A> handler) {
		final CompletableFuture<Integer> asyncResult = new CompletableFuture<>();
		delegate.read(dst, position, attachment, new AsyncResultCompletionHandler<>(asyncResult, handler));
		return asyncResult;
	}
}
