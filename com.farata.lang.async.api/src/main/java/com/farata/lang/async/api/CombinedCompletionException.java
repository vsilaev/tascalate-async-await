package com.farata.lang.async.api;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;

public class CombinedCompletionException extends CompletionException {
	final private static long serialVersionUID = 1L;
	
	final private List<Throwable> exceptions;
	
	public CombinedCompletionException(final List<Throwable> exceptions) {
		this.exceptions = exceptions;
	}
	
	public List<Throwable> getExceptions() {
		return Collections.unmodifiableList(exceptions);
	}
}
