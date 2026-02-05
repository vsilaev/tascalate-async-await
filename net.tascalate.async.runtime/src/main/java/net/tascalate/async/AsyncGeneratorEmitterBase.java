/**
 * Copyright 2015-2025 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;

import net.tascalate.async.core.AsyncGeneratorMethod;
import net.tascalate.async.core.AsyncMethodExecutor;

abstract class AsyncGeneratorEmitterBase<T> {
    private final AtomicBoolean subscribed = new AtomicBoolean(false);
    private final CompletableFuture<?> subscription = new CompletableFuture<>();
    private final AwaitableQueue<Command<T>> commands = new AwaitableQueue<>();
    
    private final long batchSize;
    
    private LongConsumer requestItemsOp;
    private Runnable cancelOp;
    
    AsyncGeneratorEmitterBase(long batchSize) {
        this.batchSize = batchSize;
    }
    
    public void subscribe(LongConsumer requestItemsOp, Runnable cancelOp) {
        if (!subscribed.compareAndSet(false, true)) {
            throw new IllegalStateException("Multiple subscription requests to the single emitter");
        }
        this.requestItemsOp = requestItemsOp;
        this.cancelOp = cancelOp != null ? cancelOp : () -> {};
        
        subscription.complete(null);
    }
    
    public void emitNextItem(T item) {
        commands.offer(new EmitItem<>(item));
    }
    
    public void emitError(Throwable error) {
        commands.offer(new EmitError<>(error));
    }
    
    public void emitCompletion() {
        commands.offer(completionCommand());
    }
    
    void request(long itemsCount) {
        requestItemsOp.accept(itemsCount);
    }
    
    void cancel() {
        cancelOp.run();
    }
    
    AsyncGenerator<T> emitAll(Scheduler scheduler) {
        AsyncGeneratorMethod<T> method = new AsyncGeneratorMethod<T>(scheduler) {
            @Override
            protected @suspendable void doRun() throws Throwable {
                // If iteration starts before actual subscription happens - then do async wait
                AsyncMethodExecutor.await(subscription);
                
                long unprocessed = 0;
                
                boolean skipCancel = false;
                try {
                    outer:
                    while( true ) {
                        // Request items if none left unprocessed
                        if (unprocessed < 1) {
                            unprocessed = batchSize;
                            request(batchSize);
                        }
                        
                        commands.await();
                        
                        Command<T> command;
                        while ((command = commands.poll()) != null) {
                            if (command.isCompletion()) {
                                skipCancel = true;
                                break outer;
                            } else if (command.isError()) {
                                skipCancel = true;
                                sneakyThrow(command.error());
                                break outer;
                            } else {
                                this.yield(command.item());
                                unprocessed--;
                            }
                        }
                    }
                } finally {
                    if (!skipCancel) {
                        cancel();
                    }
                }                
            }
            
        };
        AsyncMethodExecutor.execute(method);
        return method.generator;
    }
    
    @SuppressWarnings("unchecked")
    static <T, E extends Throwable> T sneakyThrow(Throwable ex) throws E {
        throw (E)ex;
    }
    
    @SuppressWarnings("unchecked")
    static <T> Command<T> completionCommand() {
        return (Command<T>)COMPLETION_COMMAND;
    }
    
    abstract static class Command<T> {
        boolean isCompletion() {
            return false;
        }
        
        boolean isError() {
            return false;
        }
        
        Throwable error() {
            throw new UnsupportedOperationException();            
        }
        
        T item() {
            throw new UnsupportedOperationException();
        }
    }
    
    static final class EmitItem<T> extends Command<T> {
        private final T item;
        EmitItem(T item) {
            this.item = item;
        }
        
        @Override
        T item() {
            return item;
        }
    }
    
    static final class EmitError<T> extends Command<T> {
        private final Throwable error;
        EmitError(Throwable error) {
            this.error = error;
        }
        
        @Override        
        boolean isError() {
            return true;
        }
        
        @Override
        Throwable error() {
            return error;
        }
    }
    
    private static Command<Object> COMPLETION_COMMAND = new Command<Object>() {
        @Override
        boolean isCompletion() {
            return true;
        }
    };
}
