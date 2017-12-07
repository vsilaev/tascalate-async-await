/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.

 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.tascalate.async.core;

import java.io.Serializable;

class Either<R, E extends Throwable> implements Serializable {

    private static final long serialVersionUID = 4315928456202445814L;

    private final R result;
    private final E error;

    protected Either(R result, E error) {
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

    static <R, E extends Throwable> Either<R, E> result(R result) {
        return new Either<R, E>(result, null);
    }

    static <R, E extends Throwable> Either<R, E> error(E error) {
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
