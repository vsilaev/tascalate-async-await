package net.tascalate.nio.channels;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.CompletionHandler;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import net.tascalate.concurrent.Promise;

public class AsynchronousSocketChannel 
    extends java.nio.channels.AsynchronousSocketChannel 
    implements CompletableAsynchronousByteChannel {
    
    private final java.nio.channels.AsynchronousSocketChannel delegate;

    public static AsynchronousSocketChannel open() throws IOException {
        return new AsynchronousSocketChannel(java.nio.channels.AsynchronousSocketChannel.open());
    }

    public static AsynchronousSocketChannel open(AsynchronousChannelGroup group) throws IOException {
        return new AsynchronousSocketChannel(java.nio.channels.AsynchronousSocketChannel.open(group));
    }

    protected AsynchronousSocketChannel(java.nio.channels.AsynchronousSocketChannel delegate) {
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
        delegate.connect(remote, attachment, handler);
    }

    public Promise<Void> connect(SocketAddress remote) {
        return doConnect(remote);
    }

    public Promise<Integer> read(ByteBuffer dst) {
        return doRead(dst, -1, TimeUnit.MICROSECONDS);
    }

    public Promise<Integer> read(ByteBuffer dst, long timeout, TimeUnit unit) {
        return doRead(dst, timeout, unit);
    }

    public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        delegate.read(dst, timeout, unit, attachment, handler);
    }

    public Promise<Long> read(ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit) {
        return doRead(dsts, offset, length, timeout, unit);
    }

    public <A> void read(ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long, ? super A> handler) {
        delegate.read(dsts, offset, length, timeout, unit, attachment, handler);
    }

    public Promise<Integer> write(ByteBuffer src) {
        return doWrite(src, -1, TimeUnit.MICROSECONDS);
    }

    public Promise<Integer> write(ByteBuffer src, long timeout, TimeUnit unit) {
        return doWrite(src, timeout, unit);
    }	

    public <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        delegate.write(src, timeout, unit, attachment, handler);
    }

    public Promise<Long> write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit) {
        return doWrite(srcs, offset, length, timeout, unit);
    }

    public <A> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long, ? super A> handler) {
        delegate.write(srcs, offset, length, timeout, unit, attachment, handler);
    }

    public SocketAddress getLocalAddress() throws IOException {
        return delegate.getLocalAddress();
    }

    protected Promise<Void> doConnect(SocketAddress remote) {
        AsyncResult<Void> asyncResult = new AsyncResult<>();
        delegate.connect(remote, null, asyncResult.handler);
        return asyncResult;
    }

    protected Promise<Integer> doRead(ByteBuffer dst, long timeout, TimeUnit unit) {

        final AsyncResult<Integer> asyncResult = new AsyncResult<>();
        delegate.read(dst, timeout, unit, null, asyncResult.handler);
        return asyncResult;
    }

    protected Promise<Long> doRead(
            ByteBuffer[] dsts, 
            int offset,
            int length,
            long timeout,
            TimeUnit unit) {

        AsyncResult<Long> asyncResult = new AsyncResult<>();
        delegate.read(dsts, offset, length, timeout, unit, null, asyncResult.handler);
        return asyncResult;
    }

    protected Promise<Integer> doWrite(ByteBuffer src, long timeout, TimeUnit unit) {
        AsyncResult<Integer> asyncResult = new AsyncResult<>();
        delegate.write(src, timeout, unit, null, asyncResult.handler);
        return asyncResult;
    }

    protected Promise<Long> doWrite(
            ByteBuffer[] srcs, 
            int offset, 
            int length, 
            long timeout, 
            TimeUnit unit) {

        AsyncResult<Long> asyncResult = new AsyncResult<>();
        delegate.write(srcs, offset, length, timeout, unit, null, asyncResult.handler);
        return asyncResult;
    }


}
