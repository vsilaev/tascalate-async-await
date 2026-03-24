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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;

import net.tascalate.async.concurrent.CombiningCompletionStage;
import net.tascalate.async.core.AsyncMethodExecutor;
import net.tascalate.async.core.AsyncTaskMethod;
import net.tascalate.async.core.CompletionStageHelper;
import net.tascalate.async.core.InternalCallContext;

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
        static <T> Done<T> done() {
            return (Done<T>) DONE;
        }
        
        static <T> Value<T> of(T value) {
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
                           try {
                               CompletionStage<? extends T> next = sequence.next();
                               if (null == next) {
                                   request.complete(Result.done());
                                   break outer;
                               } else {
                                   // If request is canceled by client, then we are canceling an item we are waiting for
                                   request.whenComplete((r,e) -> CompletionStageHelper.cancelCompletionStage(next, true));
                                   
                                   T produced = AsyncMethodExecutor.await(next);
                                   request.complete(Result.of(produced));
                               }
                           } catch (Throwable ex) {
                               if (InternalCallContext.isExitSignal(ex)) {
                                   request.complete(Result.done());
                                   throw (Error)ex;
                               } else {
                                   request.completeExceptionally(ex);
                                   // Don't throw -- this will handle the scenario when source sequence tolerates errors 
                                   /*
                                   InternalCallContext.sneakyThrow(ex);
                                   break;
                                   */
                               }
                           }
                       }

                   }
               }
           }      
       };
       AsyncMethodExecutor.execute(method);
       completion = method.future;
       completion.whenComplete((r, e) -> {
           CompletableFuture<Result<T>> request;
           boolean isException = null != e && !(e instanceof CancellationException);
           while ((request = queue.poll()) != null) {
               if (isException) {
                   request.completeExceptionally(e);  
               } else {
                   request.complete(Result.done());
               }
           }
       });
       return this;
   }
   
   public CompletionStage<Result<T>> take() {
       if (completion.isDone()) {
           return finalResult();
       }
       
       CompletableFuture<Result<T>> next = new CompletableFuture<>();
       queue.offer(next);
       
       if (completion.isDone()) {
           // Clear requests queue
           while (queue.poll() != null) {}
           return finalResult();
       }
       
       return next;
   }
   
   private CompletionStage<Result<T>> finalResult() {
       if (completion.isCancelled()) {
           return done;
       }
       if (completion.isCompletedExceptionally()) {
           // It holds no value, so we can cast
           @SuppressWarnings("unchecked")
           CompletionStage<Result<T>> result = (CompletionStage<Result<T>>)completion;
           return result;
       } else {
           return done;
       }       
   }

   public void close() {
       completion.cancel(true);
   }
   
   public Result<T> initial() {
       return Result.initial();
   }
   
   public static <T> CompletionStage<Result<T>> doneOn(Scheduler scheduler) {
       return new Done<>(scheduler);
   }
   
   @SafeVarargs
   public static <T> CompletionStage<T> any(CompletionStage<Result<T>>... sources) {
       return any(true, sources);
   }
   
   @SafeVarargs
   public static <T> CompletionStage<T> any(boolean cancelOthers, CompletionStage<Result<T>>... sources) {
       return any(cancelOthers, Arrays.asList(sources));
   }
   
   public static <T> CompletionStage<T> any(List<? extends CompletionStage<Result<T>>> sources) {
       return any(true, sources);
   }
   
   public static <T> CompletionStage<T> any(boolean cancelOthers, List<? extends CompletionStage<Result<T>>> sources) {
       return CombiningCompletionStage.any(sources, cancelOthers, Result::isValue, Result::value);
   }
   
   @SafeVarargs
   public static <T> CompletionStage<T> anyStrict(CompletionStage<Result<T>>... sources) {
       return anyStrict(true, sources);
   }
   
   @SafeVarargs
   public static <T> CompletionStage<T> anyStrict(boolean cancelOthers, CompletionStage<Result<T>>... sources) {
       return anyStrict(cancelOthers, Arrays.asList(sources));
   }
   
   public static <T> CompletionStage<T> anyStrict(List<? extends CompletionStage<Result<T>>> sources) {
       return anyStrict(true, sources);
   }
   
   public static <T> CompletionStage<T> anyStrict(boolean cancelOthers, List<? extends CompletionStage<Result<T>>> sources) {
       return CombiningCompletionStage.anyStrict(sources, cancelOthers, Result::isValue, Result::value);
   }
   
   @SafeVarargs
   public static <T> CompletionStage<List<T>> all(CompletionStage<Result<T>>... sources) {
       return all(Arrays.asList(sources));
   }
   
   public static <T> CompletionStage<List<T>> all(List<? extends CompletionStage<Result<T>>> sources) {
       return combine(sources, Function.identity());
   }
   
   public static <T, R> CompletionStage<R> combine(List<? extends CompletionStage<Result<T>>> sources, Function<? super List<T>, ? extends R> converter) {
       return CombiningCompletionStage.combine(sources, false, Result::isValue, Result::value, converter);
   }
   
   static final class Done<T> extends CompletableFuture<Result<T>>
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
           throw new UnsupportedOperationException("Done promise may not be completed explicitly");
       }
        
       @Override
       public boolean completeExceptionally(Throwable exception) {
           throw new UnsupportedOperationException("Done promise may not be completed explicitly");
       }   
   }
}
