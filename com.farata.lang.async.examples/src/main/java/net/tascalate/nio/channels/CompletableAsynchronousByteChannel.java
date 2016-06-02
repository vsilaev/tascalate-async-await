package net.tascalate.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;

import net.tascalate.concurrent.CompletionFuture;

public interface CompletableAsynchronousByteChannel extends AsynchronousByteChannel {
    CompletionFuture<Integer> read(ByteBuffer dst);
    CompletionFuture<Integer> write(ByteBuffer src);
    
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
        public CompletionFuture<Integer> read(ByteBuffer dst) {
            AsyncResult<Integer> asyncResult = new AsyncResult<>();
            delegate.read(dst, null, asyncResult.handler);
            return asyncResult;
        }
        
        @Override
        public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
            delegate.read(dst, attachment, handler);
        }
        
        @Override
        public CompletionFuture<Integer> write(ByteBuffer src) {
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
