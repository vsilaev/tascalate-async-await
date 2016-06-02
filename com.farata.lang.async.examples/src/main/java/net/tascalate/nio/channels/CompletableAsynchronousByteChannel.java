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
        final private AsynchronousByteChannel delegate;
        
        private Adapter(AsynchronousByteChannel delegate) {
            this.delegate = delegate;
        }


        @Override
        public CompletionFuture<Integer> read(ByteBuffer dst) {
            return doRead(dst, null, null);
        }
        
        @Override
        public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
            doRead(dst, attachment, handler);
        }
        
        @Override
        public CompletionFuture<Integer> write(ByteBuffer src) {
            return doWrite(src, null, null);
        }

        @Override
        public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
            doWrite(src, attachment, handler);
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }
        
        @Override
        public void close() throws IOException {
            delegate.close();
        }

        protected <A> CompletionFuture<Integer> doRead(
                final ByteBuffer dst, 
                final A attachment,
                final CompletionHandler<Integer, ? super A> handler) {

            final AsyncResult<Integer, ? super A> asyncResult = new AsyncResult<>(handler);
            delegate.read(dst, attachment, asyncResult.handler);
            return asyncResult;
        }
        
        protected <A> CompletionFuture<Integer> doWrite(
                final ByteBuffer src, 
                final A attachment, 
                final CompletionHandler<Integer, ? super A> handler) {

            final AsyncResult<Integer, ? super A> asyncResult = new AsyncResult<>(handler);
            delegate.write(src, attachment, asyncResult.handler);
            return asyncResult;
        }
    }
}
