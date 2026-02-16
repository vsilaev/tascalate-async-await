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

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import net.tascalate.async.core.AsyncMethodExecutor;
import net.tascalate.async.core.AsyncTaskMethod;

public final class ConcurrentGenerator<T> implements AutoCloseable {
    
    public abstract static class Result<T> {

        public boolean hasNext() {
            return true;
        }
        
        public boolean isValue() {
            return false;
        }
        
        public T value() {
            throw new UnsupportedOperationException();
        }
        
        public T orElse(T substitution) {
            return isValue() ? value() : substitution;
        }
        
        public final Stream<T> stream() {
            return isValue() ? Stream.of(value()) : Stream.empty();
        }
        
        private Result() {}
        
        public final static class Initial<T> extends Result<T> {
            
            Initial() { }
        }
        
        @SuppressWarnings("unchecked")
        public static <T> Initial<T> initial() {
            return (Initial<T>)INITIAL;
        }
        
        public final static class Value<T> extends Result<T> {
            
            private final T value;
            
            private Value(T value) {
                this.value = value;
            }

            @Override
            public boolean isValue() {
                return true;
            }

            @Override
            public T value() {
                return value;
            }
        }
        
        public final static class Done<T> extends Result<T> {
            
            Done() { }

            @Override
            public boolean hasNext() {
                return false;
            }
        }
        
        @SuppressWarnings("unchecked")
        public static <T> Done<T> done() {
            return (Done<T>) DONE;
        }
        
        public static <T> Value<T> value(T value) {
            return new Value<>(value);
        }
        
        private static final Initial<Object> INITIAL = new Initial<>();
        private static final Done<Object> DONE = new Done<>();
    }
    
    
   private final Sequence<? extends CompletionStage<? extends T>> sequence;
   private final Scheduler scheduler;
   private final AwaitableQueue<CompletableFuture<Result<T>>> queue = new AwaitableQueue<>();
   private final CompletableFuture<Result<T>> done;
   
   private CompletableFuture<?> completion;
   
   ConcurrentGenerator(Sequence<? extends CompletionStage<? extends T>> sequence, Scheduler scheduler) {
       this.sequence = sequence;
       this.scheduler = scheduler;
       this.done = new Done<>(scheduler);
   }
   
   ConcurrentGenerator<T> start() {
       Scheduler resolvedScheduler = AsyncMethodExecutor.currentScheduler(scheduler, this, MethodHandles.lookup());
       AsyncTaskMethod<Result<T>> method = new AsyncTaskMethod<Result<T>>(resolvedScheduler) {
           @Override
           protected @suspendable void doRun() {
               try (Sequence<?> closeable = sequence) {
                   outer:
                   while (true) {
                       queue.await();
                       
                       CompletableFuture<Result<T>> request;
                       while ((request = queue.poll()) != null) {
                           CompletionStage<? extends T> next = sequence.next();
                           if (null == next) {
                               request.complete(Result.done());
                               break outer;
                           } else {
                               T produced = AsyncMethodExecutor.await(next);
                               request.complete(Result.value(produced));
                           }
                       }
                   }
               } finally {
                   CompletableFuture<Result<T>> request;
                   while ((request = queue.poll()) != null) {
                       request.complete(Result.done()); 
                   }
               }
           }      
       };
       AsyncMethodExecutor.execute(method);
       completion = method.future;
       completion.whenComplete((r, e) -> {
           if (null != e) {
               CompletableFuture<Result<T>> request;
               while ((request = queue.poll()) != null) {
                   request.completeExceptionally(e);
               }
           }
       });
       return this;
   }
   
   public CompletionStage<Result<T>> take() {
       if (completion.isDone()) {
           return done;
       }
       CompletableFuture<Result<T>> next = new CompletableFuture<>();
       queue.offer(next);
       return next;
   }
   
   public boolean cancel() {
       return completion.cancel(true);
   }
   
   public void close() {
       cancel();
   }
   
   public Result<T> initial() {
       return Result.initial();
   }
   
   public static <T> CompletionStage<Result<T>> doneOn(Scheduler scheduler) {
       return new Done<>(scheduler);
   }
   
   static class Done<T> extends CompletableFuture<Result<T>>
                        implements AsyncResult<Result<T>> {
       
       private final Scheduler scheduler;
       
       Done(Scheduler scheduler) {
           this.scheduler = scheduler;
           super.complete(Result.done());
       }

       @Override
       public Scheduler scheduler() {
           return scheduler;
       }
        
       @Override
       public boolean complete(Result<T> value) {
           throw new UnsupportedOperationException("ResultPromise may not be completed explicitly");
       }
        
       @Override
       public boolean completeExceptionally(Throwable exception) {
           throw new UnsupportedOperationException("ResultPromise may not be completed explicitly");
       }   
   }
}
