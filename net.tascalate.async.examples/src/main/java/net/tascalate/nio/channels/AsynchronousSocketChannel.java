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
