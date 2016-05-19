package com.farata.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public interface J8ExecutorService extends ExecutorService {

    <T> CompletableTask<T> submit(Callable<T> task);
    
    <T> CompletableTask<T> submit(Runnable task, T result);
    
    CompletableTask<?> submit(Runnable task);

}
