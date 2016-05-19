package com.farata.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class J8ThreadPoolExecutor extends ThreadPoolExecutor implements J8ExecutorService {
    
    public J8ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public J8ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    public J8ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public J8ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }
    

    @Override
    public CompletableTask<?> submit(Runnable task) {
        return (CompletableTask<?>)super.submit(task);
    }

    @Override
    public <T> CompletableTask<T> submit(Runnable task, T result) {
        return (CompletableTask<T>)super.submit(task, result);
    }

    @Override
    public <T> CompletableTask<T> submit(Callable<T> task) {
        return (CompletableTask<T>)super.submit(task);
    }

    @Override
    protected <T> CompletableTask<T> newTaskFor(Runnable runnable, T value) {
        return CompletableTaskImpl.create(this, runnable, value);
    }

    @Override
    protected <T> CompletableTask<T> newTaskFor(Callable<T> callable) {
        return CompletableTaskImpl.create(this, callable);
    }

}
