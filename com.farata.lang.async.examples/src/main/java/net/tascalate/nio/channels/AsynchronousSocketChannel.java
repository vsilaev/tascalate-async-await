package net.tascalate.nio.channels;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.CompletionHandler;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import net.tascalate.concurrent.CompletionFuture;

public class AsynchronousSocketChannel 
    extends java.nio.channels.AsynchronousSocketChannel 
    implements CompletableAsynchronousByteChannel {
    
    final private java.nio.channels.AsynchronousSocketChannel delegate;

    public static AsynchronousSocketChannel open() throws IOException {
        return new AsynchronousSocketChannel(java.nio.channels.AsynchronousSocketChannel.open());
    }

    public static AsynchronousSocketChannel open(AsynchronousChannelGroup group) throws IOException {
        return new AsynchronousSocketChannel(java.nio.channels.AsynchronousSocketChannel.open(group));
    }

    protected AsynchronousSocketChannel(final java.nio.channels.AsynchronousSocketChannel delegate) {
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
        delegate.bind(local);
        return this;
    }

    public <T> AsynchronousSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        delegate.setOption(name, value);
        return this;
    }

    public AsynchronousSocketChannel shutdownInput() throws IOException {
        delegate.shutdownInput();
        return this;
    }

    public AsynchronousSocketChannel shutdownOutput() throws IOException {
        delegate.shutdownOutput();
        return this;
    }

    public SocketAddress getRemoteAddress() throws IOException {
        return delegate.getRemoteAddress();
    }

    public <A> void connect(SocketAddress remote, A attachment, CompletionHandler<Void, ? super A> handler) {
        doConnect(remote, attachment, handler);
    }

    public CompletionFuture<Void> connect(SocketAddress remote) {
        return doConnect(remote, null, null);
    }

    public CompletionFuture<Integer> read(ByteBuffer dst) {
        return doRead(dst, -1, TimeUnit.MICROSECONDS, null, null);
    }

    public CompletionFuture<Integer> read(ByteBuffer dst, long timeout, TimeUnit unit) {
        return doRead(dst, timeout, unit, null, null);
    }

    public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        doRead(dst, timeout, unit, attachment, handler);
    }

    public CompletionFuture<Long> read(ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit) {
        return doRead(dsts, offset, length, timeout, unit, null, null);
    }

    public <A> void read(ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long, ? super A> handler) {
        doRead(dsts, offset, length, timeout, unit, attachment, handler);
    }

    public CompletionFuture<Integer> write(ByteBuffer src) {
        return doWrite(src, -1, TimeUnit.MICROSECONDS, null, null);
    }

    public CompletionFuture<Integer> write(ByteBuffer src, long timeout, TimeUnit unit) {
        return doWrite(src, timeout, unit, null, null);
    }	

    public <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        doWrite(src, timeout, unit, attachment, handler);
    }

    public CompletionFuture<Long> write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit) {
        return doWrite(srcs, offset, length, timeout, unit, null, null);
    }

    public <A> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long, ? super A> handler) {
        doWrite(srcs, offset, length, timeout, unit, attachment, handler);
    }

    public SocketAddress getLocalAddress() throws IOException {
        return delegate.getLocalAddress();
    }

    protected <A> CompletionFuture<Void> doConnect(
            final SocketAddress remote, 
            final A attachment, 
            final CompletionHandler<Void, ? super A> handler) {

        final AsyncResult<Void, ? super A> asyncResult = new AsyncResult<>(handler);
        delegate.connect(remote, attachment, asyncResult.handler);
        return asyncResult;
    }

    protected <A> CompletionFuture<Integer> doRead(
            final ByteBuffer dst, 
            final long timeout,
            final TimeUnit unit,
            final A attachment,
            final CompletionHandler<Integer, ? super A> handler) {

        final AsyncResult<Integer, ? super A> asyncResult = new AsyncResult<>(handler);
        delegate.read(dst, timeout, unit, attachment, asyncResult.handler);
        return asyncResult;
    }

    protected <A> CompletionFuture<Long> doRead(
            final ByteBuffer[] dsts, 
            final int offset,
            final int length,
            final long timeout,
            final TimeUnit unit,
            final A attachment,
            final CompletionHandler<Long, ? super A> handler) {

        final AsyncResult<Long, ? super A> asyncResult = new AsyncResult<>(handler);
        delegate.read(dsts, offset, length, timeout, unit, attachment, asyncResult.handler);
        return asyncResult;
    }

    protected <A> CompletionFuture<Integer> doWrite(
            final ByteBuffer src, 
            final long timeout, 
            final TimeUnit unit, 
            final A attachment, 
            final CompletionHandler<Integer, ? super A> handler) {

        final AsyncResult<Integer, ? super A> asyncResult = new AsyncResult<>(handler);
        delegate.write(src, timeout, unit, attachment, asyncResult.handler);
        return asyncResult;
    }

    protected <A> CompletionFuture<Long> doWrite(
            final ByteBuffer[] srcs, 
            final int offset, 
            final int length, 
            final long timeout, 
            final TimeUnit unit, 
            final A attachment,
            final CompletionHandler<Long, ? super A> handler) {

        final AsyncResult<Long, ? super A> asyncResult = new AsyncResult<>(handler);
        delegate.write(srcs, offset, length, timeout, unit, attachment, asyncResult.handler);
        return asyncResult;
    }


}
