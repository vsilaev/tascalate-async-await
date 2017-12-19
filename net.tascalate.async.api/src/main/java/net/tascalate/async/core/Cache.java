package net.tascalate.async.core;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class Cache<K, V>  {
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
        return producerMutexes.computeIfAbsent(keyRef, k -> new Object()); 
    }
    
    private void expungeStaleEntries() {
        for (Reference<? extends K> ref; (ref = queue.poll()) != null;) {
            @SuppressWarnings("unchecked")
            Reference<K> keyRef = (Reference<K>) ref;
            // keyRef now is equal only to itself while referent is cleared already
            // so it's safe to remove it without ceremony (like getOrCreateMutex(keyRef) usage)
            valueMap.remove(keyRef);
        }
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

}