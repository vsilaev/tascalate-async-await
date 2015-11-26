package com.farata.nio;

import java.io.IOException;

import java.net.SocketAddress;
import java.net.SocketOption;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

import java.util.Set;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AsyncSocketChannel extends AsynchronousSocketChannel {
	final private AsynchronousSocketChannel delegate;
	
	public AsyncSocketChannel() throws IOException {
		this(AsynchronousSocketChannel.open());
	}
	
	public AsyncSocketChannel(final AsynchronousChannelGroup group) throws IOException {
		this(AsynchronousSocketChannel.open(group));
	}
	
	public AsyncSocketChannel(final AsynchronousSocketChannel delegate) {
		super(delegate.provider());
		this.delegate = delegate;
	}

	public boolean isOpen() {
		return delegate.isOpen();
	}

	public void close() throws IOException {
		delegate.close();
	}

	public <T> T getOption(SocketOption<T> name) throws IOException {
		return delegate.getOption(name);
	}

	public Set<SocketOption<?>> supportedOptions() {
		return delegate.supportedOptions();
	}

	public AsynchronousSocketChannel bind(SocketAddress local) throws IOException {
		return delegate.bind(local);
	}

	public <T> AsynchronousSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
		return delegate.setOption(name, value);
	}

	public AsynchronousSocketChannel shutdownInput() throws IOException {
		return delegate.shutdownInput();
	}

	public AsynchronousSocketChannel shutdownOutput() throws IOException {
		return delegate.shutdownOutput();
	}

	public SocketAddress getRemoteAddress() throws IOException {
		return delegate.getRemoteAddress();
	}

	public <A> void connect(SocketAddress remote, A attachment, CompletionHandler<Void, ? super A> handler) {
		doConnect(remote, attachment, handler);
	}

	public CompletableFuture<Void> connect(SocketAddress remote) {
		return doConnect(remote, null, null);
	}

	public CompletableFuture<Integer> read(ByteBuffer dst) {
		return doRead(dst, -1, TimeUnit.MICROSECONDS, null, null);
	}

	public CompletableFuture<Integer> read(ByteBuffer dst, long timeout, TimeUnit unit) {
		return doRead(dst, timeout, unit, null, null);
	}
	
	public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment,
			CompletionHandler<Integer, ? super A> handler) {
		doRead(dst, timeout, unit, attachment, handler);
	}

	public CompletableFuture<Long> read(ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit) {
		return doRead(dsts, offset, length, timeout, unit, null, null);
	}
	
	public <A> void read(ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit, A attachment,
			CompletionHandler<Long, ? super A> handler) {
		doRead(dsts, offset, length, timeout, unit, attachment, handler);
	}

	public CompletableFuture<Integer> write(ByteBuffer src) {
		return doWrite(src, -1, TimeUnit.MICROSECONDS, null, null);
	}
	
	public CompletableFuture<Integer> write(ByteBuffer src, long timeout, TimeUnit unit) {
		return doWrite(src, timeout, unit, null, null);
	}	
	
	public <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment,
			CompletionHandler<Integer, ? super A> handler) {
		doWrite(src, timeout, unit, attachment, handler);
	}

	public CompletableFuture<Long> write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit) {
		return doWrite(srcs, offset, length, timeout, unit, null, null);
	}
	
	public <A> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit, A attachment,
			CompletionHandler<Long, ? super A> handler) {
		doWrite(srcs, offset, length, timeout, unit, attachment, handler);
	}

	public SocketAddress getLocalAddress() throws IOException {
		return delegate.getLocalAddress();
	}
	
	protected <A> CompletableFuture<Void> doConnect(final SocketAddress remote, 
			                                        final A attachment, 
			                                        final CompletionHandler<Void, ? super A> handler) {
		
		final CompletableFuture<Void> asyncResult = new CompletableFuture<>();
		delegate.connect(remote, attachment, new AsyncResultCompletionHandler<>(asyncResult, handler));
		return asyncResult;
	}
	
	protected <A> CompletableFuture<Integer> doRead(final ByteBuffer dst, 
	                                                final long timeout,
	                                                final TimeUnit unit,
	                                                final A attachment,
	                                                final CompletionHandler<Integer, ? super A> handler) {
		
		final CompletableFuture<Integer> asyncResult = new CompletableFuture<>();
		delegate.read(dst, timeout, unit, attachment, new AsyncResultCompletionHandler<>(asyncResult, handler));
		return asyncResult;
	}
	
	protected <A> CompletableFuture<Long> doRead(final ByteBuffer[] dsts, 
	                                             final int offset,
	                                             final int length,
	                                             final long timeout,
	                                             final TimeUnit unit,
	                                             final A attachment,
	                                             final CompletionHandler<Long, ? super A> handler) {

		final CompletableFuture<Long> asyncResult = new CompletableFuture<>();
		delegate.read(dsts, offset, length, timeout, unit, attachment, new AsyncResultCompletionHandler<>(asyncResult, handler));
		return asyncResult;
	}

	protected <A> CompletableFuture<Integer> doWrite(final ByteBuffer src, 
	                                                 final long timeout, 
	                                                 final TimeUnit unit, 
	                                                 final A attachment, 
	                                                 final CompletionHandler<Integer, ? super A> handler) {

		final CompletableFuture<Integer> asyncResult = new CompletableFuture<>();
		delegate.write(src, timeout, unit, attachment, new AsyncResultCompletionHandler<>(asyncResult, handler));
		return asyncResult;
	}
	
	protected <A> CompletableFuture<Long> doWrite(final ByteBuffer[] srcs, 
	                                              final int offset, 
	                                              final int length, 
	                                              final long timeout, 
	                                              final TimeUnit unit, 
	                                              final A attachment,
	                                              final CompletionHandler<Long, ? super A> handler) {
		
		final CompletableFuture<Long> asyncResult = new CompletableFuture<>();
		delegate.write(srcs, offset, length, timeout, unit, attachment, new AsyncResultCompletionHandler<>(asyncResult, handler));
		return asyncResult;
	}
	
	
}
