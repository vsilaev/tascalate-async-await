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
package net.tascalate.async.spring;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ThrottledExecutorServiceAlt extends AbstractExecutorService {
    
    private static final VarHandle STATE;
    
    static {
        try {
            STATE = MethodHandles.lookup().findVarHandle(
                ThrottledExecutorServiceAlt.class, "state", int.class
            );
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    // states: RUNNING -> SHUTDOWN -> TERMINATED
    private static final int RUNNING    = 0;
    private static final int SHUTDOWN   = 1;
    private static final int TERMINATED = 2;
    private volatile int state;

    private final int maxConcurrentThreads;
    private final Semaphore semaphore;
    private final BlockingQueue<Runnable> queue;
    private final RejectedExecutionHandler<? super ThrottledExecutorServiceAlt> rejectedExecutionHandler;
    
    private final ExecutorService delegate;
    private final CountDownLatch terminationSignal = new CountDownLatch(1);
    
    public ThrottledExecutorServiceAlt(ThreadFactory threadFactory, int maxConcurrentThreads) {
        this(threadFactory, maxConcurrentThreads, 0);
    }
    
    public ThrottledExecutorServiceAlt(ThreadFactory threadFactory, int maxConcurrentThreads, int queueCapacity) {
        this(threadFactory, maxConcurrentThreads, queueCapacity, RejectedExecutionHandler.ABORT_POLICY);
    }
    
    public ThrottledExecutorServiceAlt(ThreadFactory threadFactory, int maxConcurrentThreads, 
                                       RejectedExecutionHandler<? super ThrottledExecutorServiceAlt> rejectedExecutionHandler) {
        this(threadFactory, maxConcurrentThreads, 0, RejectedExecutionHandler.ABORT_POLICY);
    }
    
    public ThrottledExecutorServiceAlt(ThreadFactory threadFactory, int maxConcurrentThreads, int queueCapacity, 
                                       RejectedExecutionHandler<? super ThrottledExecutorServiceAlt> rejectedExecutionHandler) {
        this(threadFactory, maxConcurrentThreads, queueByCapacity(queueCapacity), rejectedExecutionHandler);
    }

    public ThrottledExecutorServiceAlt(ThreadFactory threadFactory, int maxConcurrentThreads, BlockingQueue<Runnable> queue) {
        this(threadFactory, maxConcurrentThreads, queue, RejectedExecutionHandler.ABORT_POLICY);
    }

    public ThrottledExecutorServiceAlt(ThreadFactory threadFactory, int maxConcurrentThreads, BlockingQueue<Runnable> queue, 
                                       RejectedExecutionHandler<? super ThrottledExecutorServiceAlt> rejectedExecutionHandler) {
        this(Executors.newThreadPerTaskExecutor(threadFactory), maxConcurrentThreads, queue, rejectedExecutionHandler);
    }

    protected ThrottledExecutorServiceAlt(ExecutorService delegate, int maxConcurrentThreads, BlockingQueue<Runnable> queue, 
                                          RejectedExecutionHandler<? super ThrottledExecutorServiceAlt> rejectedExecutionHandler) {
        this.delegate = delegate;
        this.maxConcurrentThreads = maxConcurrentThreads;
        this.semaphore = new Semaphore(maxConcurrentThreads);
        this.queue = queue;
        this.rejectedExecutionHandler = rejectedExecutionHandler != null 
                                        ? rejectedExecutionHandler 
                                        : RejectedExecutionHandler.ABORT_POLICY;
        
    }

    public BlockingQueue<Runnable> getQueue() {
        return this.queue;
    }    
    
    @Override
    public void shutdown() {
        if (STATE.compareAndSet(this, RUNNING, SHUTDOWN)) {
            delegate.shutdown(); // Tell the delegate executor to stop accepting tasks
            tryTerminate();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> drained = new ArrayList<>();
        if (!isTerminated()) {
            if (STATE.compareAndSet(this, RUNNING, SHUTDOWN) || state == SHUTDOWN) {
                queue.drainTo(drained);
                delegate.shutdownNow(); // Interrupts all running delegate threads instantly
                tryTerminate();
            }
        }
        return drained;
    }
    
    @Override
    public boolean isShutdown() {
        return state >= SHUTDOWN;
    }

    @Override
    public boolean isTerminated() {
        return state >= TERMINATED; 
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit);
        if (isTerminated()) {
            return true;
        } else {
            return terminationSignal.await(timeout, unit);
        }
    }
    
    protected void beforeExecute(Thread t, Runnable r) { }
    
    protected void afterExecute(Runnable r, Throwable t) { }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "Command passed is null");
        if (state >= SHUTDOWN) {
            // shutdown or terminated
            throw new RejectedExecutionException();
        }

        //  Try to acquire a concurrency slot immediately
        if (semaphore.tryAcquire()) {
            executeDirect(command);
        } else {
            //  No slot available: enqueue the task
            if (!queue.offer(command)) {
                rejectedExecutionHandler.rejectedExecution(command, this);
            }
            
            executeNextQueued();
        }
    }
    
    private void executeNextQueued() {
        if (isTerminated()) {
            return;
        }
        
        if (semaphore.tryAcquire()) {
            Runnable task = queue.poll();
            if (task != null) {
                executeDirect(task);
            } else {
                // Queue is empty, release the permit back
                semaphore.release();
                if (state == SHUTDOWN) {
                    tryTerminate();
                }
            }
        }
    }
    
    private void executeDirect(Runnable task) {
        try {
            delegate.execute(() -> {
                try {
                    beforeExecute(Thread.currentThread(), task);
                    
                    Throwable thrown = null;
                    try {
                        // Execute the actual task
                        task.run();
                    } catch (Throwable x) {
                        thrown = x;
                        throw x; // Re-throw to trigger UncaughtExceptionHandler / kill thread
                    } finally {
                        afterExecute(task, thrown);
                    }
                } finally {
                    Runnable nextTask = queue.poll();
                    if (nextTask != null) {
                        // PASS THE PERMIT: This ALWAYS runs, ensuring no permits leak.
                        executeDirect(nextTask);
                    } else {
                        // RELEASE THE PERMIT
                        semaphore.release();
                        executeNextQueued();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            // If the delegate rejects the task (e.g., it was shut down concurrently),
            // we must release the permit to prevent a deadlock.
            semaphore.release();
            executeNextQueued();
            throw e;
        }
    }

    
    private void tryTerminate() {
        assert state >= SHUTDOWN;
        // We no longer need to check a 'threads' set. 
        // The semaphore mathematically proves no tasks are active.
        if (semaphore.availablePermits() == maxConcurrentThreads &&
            queue.isEmpty() &&
            STATE.compareAndSet(this, SHUTDOWN, TERMINATED)) {
            
            terminationSignal.countDown();
        }
    }    
    
    private static <E> BlockingQueue<E> queueByCapacity(int queueCapacity) {
        if (queueCapacity < 0) {
            return new LinkedBlockingQueue<>();
        } else if (queueCapacity == 0) {
            return new SynchronousQueue<>();
        } else {
            return new LinkedBlockingQueue<>(queueCapacity);
        }
    }
}