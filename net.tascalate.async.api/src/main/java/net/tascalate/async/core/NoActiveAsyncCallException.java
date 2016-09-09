package net.tascalate.async.core;

/**
 * @author Valery Silaev
 */
public class NoActiveAsyncCallException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public NoActiveAsyncCallException(final String message) {
        super(message);
    }

}
