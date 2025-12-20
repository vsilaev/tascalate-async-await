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
package net.tascalate.async.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class Cache<K, V> {
    private final KeyedLocks<K> producerMutexes = new KeyedLocks<>();
    private final ConcurrentMap<Object, Object> valueMap = new ConcurrentHashMap<>();
    
    private final ReferenceType keyRefType;
    private final ReferenceType valueRefType;
    private final ReferenceQueue<K> queue;
    
    public Cache() {
        this(ReferenceType.WEAK, ReferenceType.SOFT);
    }
    
    public Cache(ReferenceType keyRefType, ReferenceType valueRefType) {
        this.keyRefType = keyRefType;
        this.valueRefType = valueRefType;
        this.queue = keyRefType.createKeyReferenceQueue();
    }
    
    public V get(K key, Function<? super K, ? extends V> producer) {
        expungeStaleEntries();

        Object lookupKeyRef = keyRefType.createLookupKey(key);
        Object valueRef;

        // Try to get a cached value.
        valueRef = valueMap.get(lookupKeyRef);
        V value;
        
        if (valueRef != null) {
            value = valueRefType.dereference(valueRef);
            if (value != null) {
                // A cached value was found.
                return value;
            }
        }

        try (KeyedLocks.Lock lock = producerMutexes.acquire(key)) {
            // Double-check after getting mutex
            valueRef = valueMap.get(lookupKeyRef);
            value = valueRef == null ? null : valueRefType.dereference(valueRef);
            if (value == null) {
                value = producer.apply(key);
                valueMap.put(
                    keyRefType.createKeyReference(key, queue), 
                    valueRefType.createValueReference(value)
                );
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        return value;
    }


    public V remove(K key) {
        try (KeyedLocks.Lock lock = producerMutexes.acquire(key)) {
            Object valueRef = valueMap.remove(keyRefType.createLookupKey(key));
            return valueRef == null ? null : valueRefType.dereference(valueRef);            
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private void expungeStaleEntries() {
        if (null == queue) {
            return;
        }
        for (Reference<? extends K> ref; (ref = queue.poll()) != null;) {
            @SuppressWarnings("unchecked")
            Reference<K> keyRef = (Reference<K>) ref;
            // keyRef now is equal only to itself while referent is cleared already
            // so it's safe to remove it without ceremony (like getOrCreateMutex(keyRef) usage)
            valueMap.remove(keyRef);
        }
    }
}
