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
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.CompletionHandler;
import java.util.Set;

import net.tascalate.concurrent.Promise;

public class AsynchronousServerSocketChannel extends java.nio.channels.AsynchronousServerSocketChannel {

    protected final java.nio.channels.AsynchronousServerSocketChannel delegate;
    
    public static AsynchronousServerSocketChannel open() throws IOException {
        return new AsynchronousServerSocketChannel(java.nio.channels.AsynchronousServerSocketChannel.open());
    }
    
    public static AsynchronousServerSocketChannel open(AsynchronousChannelGroup group) throws IOException {
        return new AsynchronousServerSocketChannel(java.nio.channels.AsynchronousServerSocketChannel.open(group));
    }
    
    protected AsynchronousServerSocketChannel(java.nio.channels.AsynchronousServerSocketChannel delegate) {
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

    public AsynchronousServerSocketChannel bind(SocketAddress local, int backlog) throws IOException {
        delegate.bind(local, backlog);
        return this;
    }

    public <T> AsynchronousServerSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        delegate.setOption(name, value);
        return this;
    }
    
    public Promise<? extends AsynchronousSocketChannel> acceptClient() {
        final AsyncResult<AsynchronousSocketChannel> asyncResult = new AsyncResult<>();
        doAccept(null, asyncResult.handler);
        return asyncResult;
    }

    public Promise<java.nio.channels.AsynchronousSocketChannel> accept() {
        final AsyncResult<java.nio.channels.AsynchronousSocketChannel> asyncResult = new AsyncResult<>();
        doAccept(null, asyncResult.handler);
        return asyncResult;
    }
    
    public <A> void acceptClient(A attachment, CompletionHandler<AsynchronousSocketChannel, ? super A> handler) {
        doAccept(attachment, handler);
    }
    
    public <A> void accept(A attachment, CompletionHandler<java.nio.channels.AsynchronousSocketChannel, ? super A> handler) {
        doAccept(attachment, handler);
    }

    public SocketAddress getLocalAddress() throws IOException {
        return delegate.getLocalAddress();
    }
    
    private <V extends java.nio.channels.AsynchronousSocketChannel, A> void doAccept(A attachment, CompletionHandler<V, A> handler) {
        if (null == handler) {
            delegate.accept(attachment, null);
            return;
        }
        delegate.accept(attachment, new CompletionHandler<java.nio.channels.AsynchronousSocketChannel, A>() {
            @Override
            public void completed(java.nio.channels.AsynchronousSocketChannel result, A attachment) {
                @SuppressWarnings("unchecked")
                final V wrapped = (V)new AsynchronousSocketChannel(result);
                handler.completed(wrapped, attachment);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                handler.failed(exc, attachment);
            }
        });
    }

}
