/**
 * Copyright 2015-2025 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.spi;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class ThreadVar<T> {
    private final ScopedValue<T> scopedValue = ScopedValue.newInstance();
    private final String name;
    private final T sentinel;
    
    public ThreadVar(String name, T sentinel) {
        this.name = name;
        this.sentinel = sentinel;
    }
    
    public T value() {
        if (null == sentinel) {
            return scopedValue.isBound() ? scopedValue.get() : null;
        } else {
            T result = scopedValue.orElse(sentinel);
            return result == sentinel ? null : result;
        }
    }

    public void runWith(T newValue, Runnable code) {
        ScopedValue.where(scopedValue, newValue).run(code);
    }
    
    public void runWith(T oldValue, T newValue, Runnable code) {
        ScopedValue.where(scopedValue, newValue).run(code);
    }
    
    public <V> V supplyWith(T newValue, Supplier<V> supplier) {
        return ScopedValue.where(scopedValue, newValue).call(supplier::get);
    }
    
    public <V> V supplyWith(T oldValue, T newValue, Supplier<V> supplier) {
        return ScopedValue.where(scopedValue, newValue).call(supplier::get);
    }
    
    public <V> V callWith(T newValue, Callable<V> callable) throws Exception {
        return ScopedValue.where(scopedValue, newValue).call(callable::call);
    }
    
    public <V> V callWith(T oldValue, T newValue, Callable<V> callable) throws Exception {
        return ScopedValue.where(scopedValue, newValue).call(callable::call);
    }
    
    @Override
    public String toString() {
        return getClass().getName() + '[' + name + ']';
    }
}
