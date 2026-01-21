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
package net.tascalate.async.spring.webservlet;

import java.util.function.Consumer;
import java.util.function.Supplier;

class ContextVar<T> {
    private final Supplier<? extends T> getter;
    private final Consumer<? super T> setter;
    private final Runnable eraser;
    
    ContextVar(Supplier<? extends T> getter, Consumer<? super T> setter, Runnable eraser) {
        this.getter = getter;
        this.setter = setter;
        this.eraser = eraser;
    }
    
    private static final ContextVar<Object>.Snapshot EMPTY_SNAPSHOT;
    static {
        ContextVar<Object> NOTHING = new ContextVar<>(null, null, null);
        ContextVar<Object>.Modification UNMODIFIED = NOTHING.new Modification(null) {
            @Override
            public void close() {
            }
        };
        
        EMPTY_SNAPSHOT = NOTHING.new Snapshot(null) {
            @Override
            boolean empty() {
                return true;
            }

            @Override
            ContextVar<Object>.Modification apply() {
                return UNMODIFIED;
            }
        };
    }
    
    Snapshot snapshot() {
        T value = getter.get();
        if (null == value) {
            @SuppressWarnings("unchecked")
            ContextVar<T>.Snapshot typedEmpty = (ContextVar<T>.Snapshot)EMPTY_SNAPSHOT;
            return typedEmpty;
        } else {
            return new Snapshot(value);
        }
    }
    
    class Snapshot {
        private final T savedValue;
        Snapshot(T savedValue) {
            this.savedValue = savedValue;
        }
        
        boolean empty() {
            return false;
        }
    
        Modification apply() {
            T prevValue = getter.get();
            setter.accept(savedValue);
            return new Modification(prevValue);
        }
    }
    
    class Modification implements AutoCloseable {
        private final T originalValue;
        Modification(T originalValue) {
            this.originalValue = originalValue;
        }
        
        @Override
        public void close() {
            if (null == originalValue) {
                eraser.run();
            } else {
                setter.accept(originalValue);
            }
        }
    }
}
