/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class Cache<K, V> {
    private final ConcurrentMap<Reference<K>, Object> producerMutexes = new ConcurrentHashMap<>();
    private final ConcurrentMap<Reference<K>, V> valueMap = new ConcurrentHashMap<>();
    private final ReferenceQueue<K> queue = new ReferenceQueue<K>();

    public V get(K key, Function<? super K, ? extends V> producer) {
        expungeStaleEntries();

        Reference<K> lookupKeyRef = new KeyReference<K>(key);
        V value;

        // Try to get a cached value.
        value = valueMap.get(lookupKeyRef);

        if (value != null) {
            // A cached value was found.
            return value;
        }

        Object mutex = getOrCreateMutex(lookupKeyRef);
        synchronized (mutex) {
            try {
                // Double-check after getting mutex
                value = valueMap.get(lookupKeyRef);
                if (value == null) {
                    value = producer.apply(key);
                    final Reference<K> actualKeyRef = new KeyReference<K>(key, queue);
                    valueMap.put(actualKeyRef, value);
                }
            } finally {
                producerMutexes.remove(lookupKeyRef, mutex);
            }
        }

        return value;
    }

    public V remove(K key) {
        Reference<K> lookupKeyRef = new KeyReference<K>(key);
        Object mutex = getOrCreateMutex(lookupKeyRef);
        synchronized (mutex) {
            try {
                final V value = valueMap.remove(lookupKeyRef);
                return value;
            } finally {
                producerMutexes.remove(lookupKeyRef, mutex);
            }
        }
    }

    protected Object getOrCreateMutex(final Reference<K> keyRef) {
        return producerMutexes.computeIfAbsent(keyRef, newProducerLock());
    }

    private void expungeStaleEntries() {
        for (Reference<? extends K> ref; (ref = queue.poll()) != null;) {
            @SuppressWarnings("unchecked")
            Reference<K> keyRef = (Reference<K>) ref;
            // keyRef now is equal only to itself while referent is cleared
            // already
            // so it's safe to remove it without ceremony (like
            // getOrCreateMutex(keyRef) usage)
            valueMap.remove(keyRef);
        }
    }
    
    
    @SuppressWarnings("unchecked")
    private static <K> Function<K, Object> newProducerLock() {
        return (Function<K, Object>)NEW_PRODUCER_LOCK;
    }

    static class KeyReference<K> extends WeakReference<K> {
        private final int referentHashCode;

        KeyReference(K key) {
            this(key, null);
        }

        KeyReference(K key, ReferenceQueue<K> queue) {
            super(key, queue);
            referentHashCode = key == null ? 0 : key.hashCode();
        }

        public int hashCode() {
            return referentHashCode;
        }

        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (null == other || other.getClass() != KeyReference.class)
                return false;
            Object r1 = this.get();
            Object r2 = ((KeyReference<?>) other).get();
            return null == r1 ? null == r2 : r1.equals(r2);
        }
    }

    
    
    private static final Function<Object, Object> NEW_PRODUCER_LOCK = k -> new Object(); 
}