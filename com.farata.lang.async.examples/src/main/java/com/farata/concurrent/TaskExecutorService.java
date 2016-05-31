package com.farata.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public interface TaskExecutorService extends ExecutorService {

    <T> CompletionFuture<T> submit(Callable<T> task);
    
    <T> CompletionFuture<T> submit(Runnable task, T result);
    
    CompletionFuture<?> submit(Runnable task);

}
