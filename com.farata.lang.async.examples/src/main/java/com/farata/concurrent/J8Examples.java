package com.farata.concurrent;

import java.util.Arrays;

public class J8Examples {

    public static void main(final String[] argv) throws InterruptedException {
        final TaskExecutorService executorService = TaskExecutors.newFixedThreadPool(3);
        
        for (int i : Arrays.asList(9, -9, 42)) {
            final CompletionFuture<Integer> task1 = executorService.submit(() -> awaitAndProduce(i, 1500));
            final CompletionFuture<Integer> task2 = executorService.submit(() -> awaitAndProduce2(i + 1));
            task1.thenCombineAsync(
                     task2, 
                     (a,b) -> a + b
                 )
                .thenAcceptAsync(J8Examples::onComplete)
                .exceptionally(J8Examples::onError)
                ;
            if (i == 42) {
                Thread.sleep(200);
                task1.cancel(true);
            }
        }
        
        // Suicidal task to close gracefully
        executorService.submit(() -> {
            try {
                Thread.sleep(2000);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
            executorService.shutdown();
        });
    }
    
    
    private static int awaitAndProduce(int i, long delay) {
        try {
            System.out.println("Delay in " + Thread.currentThread());
            Thread.sleep(delay);
            if (i < 0) {
                throw new RuntimeException("Negative value: " + i);
            }
            return i * 10;
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
    
    private static int awaitAndProduce2(int i) {
        try {
            System.out.println("Delay in " + Thread.currentThread());
            Thread.sleep(150);
            return i * 10;
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
    
    private static void onComplete(int i) {
        System.out.println(">>> Result " + i + ", " + Thread.currentThread());
    }
    
    private static Void onError(Throwable i) {
        System.out.println(">>> Error " + i + ", " + Thread.currentThread());
        return null;
    }
}
