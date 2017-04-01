package net.tascalate.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

class CompletableSubTask<T> extends BlockingCompletionStage<T> {
	
	CompletableSubTask(Executor executor) {
		super(executor, new DelegatingCallable<T>());
	}
	
	@Override
	Runnable setupTransition(Callable<T> code) {
		// Ugly hacks leads for not the most elegant solution
		DelegatingCallable<T> transitionCall = (DelegatingCallable<T>)action; 
		transitionCall.setup(code);
		return task;
	}
	
	@Override
    protected <U> BlockingCompletionStage<U> createCompletionStage(Executor executor) {
    	return new CompletableSubTask<U>(executor);
    }	
}
