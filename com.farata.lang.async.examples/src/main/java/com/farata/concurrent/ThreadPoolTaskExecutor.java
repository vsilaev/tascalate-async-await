package com.farata.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolTaskExecutor extends ThreadPoolExecutor implements TaskExecutorService {
    
    public ThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public ThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    public ThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public ThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }
    

    @Override
    public CompletionFuture<?> submit(Runnable task) {
        return (CompletionFuture<?>)super.submit(task);
    }

    @Override
    public <T> CompletionFuture<T> submit(Runnable task, T result) {
        return (CompletionFuture<T>)super.submit(task, result);
    }

    @Override
    public <T> CompletionFuture<T> submit(Callable<T> task) {
        return (CompletionFuture<T>)super.submit(task);
    }

    @Override
    protected <T> CompletableTask<T> newTaskFor(Runnable runnable, T value) {
        return CompletableTask.create(this, runnable, value);
    }

    @Override
    protected <T> CompletableTask<T> newTaskFor(Callable<T> callable) {
        return CompletableTask.create(this, callable);
    }

}
