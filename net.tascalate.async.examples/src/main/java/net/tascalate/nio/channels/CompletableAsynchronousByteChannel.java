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
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;

import net.tascalate.concurrent.Promise;

public interface CompletableAsynchronousByteChannel extends AsynchronousByteChannel {
    Promise<Integer> read(ByteBuffer dst);
    Promise<Integer> write(ByteBuffer src);
    
    public static CompletableAsynchronousByteChannel adapt(AsynchronousByteChannel original) {
        if (original instanceof CompletableAsynchronousByteChannel) {
            return (CompletableAsynchronousByteChannel)original;
        }
        return new Adapter(original);
    }
    
    static class Adapter implements CompletableAsynchronousByteChannel {
        private final AsynchronousByteChannel delegate;
        
        private Adapter(AsynchronousByteChannel delegate) {
            this.delegate = delegate;
        }

        @Override
        public Promise<Integer> read(ByteBuffer dst) {
            AsyncResult<Integer> asyncResult = new AsyncResult<>();
            delegate.read(dst, null, asyncResult.handler);
            return asyncResult;
        }
        
        @Override
        public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
            delegate.read(dst, attachment, handler);
        }
        
        @Override
        public Promise<Integer> write(ByteBuffer src) {
            AsyncResult<Integer> asyncResult = new AsyncResult<>();
            delegate.write(src, null, asyncResult.handler);
            return asyncResult;
        }

        @Override
        public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
            delegate.write(src, attachment, handler);
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }
        
        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
