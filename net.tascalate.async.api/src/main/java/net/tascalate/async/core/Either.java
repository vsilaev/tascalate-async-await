package net.tascalate.async.core;

import java.io.Serializable;

class Either<R, E extends Throwable> implements Serializable {

    final private static long serialVersionUID = 4315928456202445814L;

    final private R result;
    final private E error;

    protected Either(final R result, final E error) {
        this.result = result;
        this.error = error;
    }

    final boolean isError() {
        return null != error;
    }

    final boolean isResult() {
        return !isError();
    }

    final R result() {
        return result;
    }

    final E error() {
        return error;
    }

    static <R, E extends Throwable> Either<R, E> result(final R result) {
        return new Either<R, E>(result, null);
    }

    static <R, E extends Throwable> Either<R, E> error(final E error) {
        return new Either<R, E>(null, error);
    }

    R done() throws E {
        if (isError()) {
            throw error;
        } else {
            return result;
        }
    }
    
    R doneUnchecked() {
        if (isError()) {
            return sneakyThrow(error);
        } else {
            return result;
        }
    }
    
    @SuppressWarnings("unchecked")
    static <T, E extends Throwable> T sneakyThrow(Throwable ex) throws E {
        throw (E)ex;
    }
}
