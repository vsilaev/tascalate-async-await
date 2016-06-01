package com.farata.concurrent;

import java.util.Collections;
import java.util.List;

public class MultitargetException extends Exception {
	final private static long serialVersionUID = 1L;
	
	final private List<Throwable> exceptions;
	
	public MultitargetException(final List<Throwable> exceptions) {
		this.exceptions = exceptions;
	}
	
	public List<Throwable> getExceptions() {
		return Collections.unmodifiableList(exceptions);
	}
	
	public static MultitargetException of(final Throwable exception) {
	    return new MultitargetException(Collections.singletonList(exception));
	}
}
