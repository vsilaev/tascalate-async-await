package net.tascalate.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RunnableFuture;

public class CompletableTask<T> extends AbstractCompletableTask<T> implements RunnableFuture<T> {
	
    public CompletableTask(final Executor executor, Callable<T> callable) {
        super(executor, callable);
    }
    
    @Override
    public void run() {
        task.run();
    }

	@Override
	Runnable setupTransition(Callable<T> code) {
		throw new UnsupportedOperationException();
	}
	
	@Override
    protected <U> AbstractCompletableTask<U> createCompletionStage(Executor executor) {
    	return new CompletableSubTask<U>(executor);
    }	
}

