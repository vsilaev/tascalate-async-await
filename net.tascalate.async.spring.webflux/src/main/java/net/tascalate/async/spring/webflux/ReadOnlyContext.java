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
package net.tascalate.async.spring.webflux;

import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;

import reactor.util.context.Context;

class ReadOnlyContext implements Context {
    private final Context delegate;
    
    public ReadOnlyContext(Context delegate) {
        this.delegate = delegate;
    }

    public <T> T get(Object key) {
        return delegate.get(key);
    }

    public <T> T get(Class<T> key) {
        return delegate.get(key);
    }

    public <T> T getOrDefault(Object key, T defaultValue) {
        return delegate.getOrDefault(key, defaultValue);
    }

    public <T> Optional<T> getOrEmpty(Object key) {
        return delegate.getOrEmpty(key);
    }

    public boolean hasKey(Object key) {
        return delegate.hasKey(key);
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public Context put(Object key, Object value) {
        return unsupported();
    }

    public Context putNonNull(Object key, Object valueOrNull) {
        return unsupported();
    }

    public Context delete(Object key) {
        return unsupported();
    }

    public int size() {
        return delegate.size();
    }

    public Stream<Entry<Object, Object>> stream() {
        return delegate.stream();
    }

    public Context putAll(Context other) {
        return unsupported();
    }
    
    private static <T> T unsupported() {
        throw new UnsupportedOperationException();
    }
}
