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

import java.util.function.Function;

import net.tascalate.async.util.Cache;
import net.tascalate.async.util.ReferenceType;

public final class Memoization {
    private Memoization() {
        
    }
    
    public static <K, V> Function<K, V> weakKeysHardValues(Function<? super K, ? extends V> producer) {
        return memoize(ReferenceType.WEAK, ReferenceType.HARD, producer);
    }
    
    public static <K, V> Function<K, V> weakKeysSoftValues(Function<? super K, ? extends V> producer) {
        return memoize(ReferenceType.WEAK, ReferenceType.SOFT, producer);
    }
    
    public static <K, V> Function<K, V> weakKeysWeakValues(Function<? super K, ? extends V> producer) {
        return memoize(ReferenceType.WEAK, ReferenceType.WEAK, producer);
    }
    
    public static <K, V> Function<K, V> softKeysHardValues(Function<? super K, ? extends V> producer) {
        return memoize(ReferenceType.SOFT, ReferenceType.HARD, producer);
    }
    
    public static <K, V> Function<K, V> softKeysSoftValues(Function<? super K, ? extends V> producer) {
        return memoize(ReferenceType.SOFT, ReferenceType.SOFT, producer);
    }

    public static <K, V> Function<K, V> softKeysWeakValues(Function<? super K, ? extends V> producer) {
        return memoize(ReferenceType.SOFT, ReferenceType.WEAK, producer);
    }
    
    public static <K, V> Function<K, V> hardKeysSoftValues(Function<? super K, ? extends V> producer) {
        return memoize(ReferenceType.HARD, ReferenceType.SOFT, producer);
    }
    
    public static <K, V> Function<K, V> hardKeysWeakValues(Function<? super K, ? extends V> producer) {
        return memoize(ReferenceType.HARD, ReferenceType.WEAK, producer);
    }
    
    private static <K, V> Function<K, V> memoize(ReferenceType ktype, ReferenceType vtype, Function<? super K, ? extends V> producer) {
        Cache<K, V> cache = new Cache<>(ktype, vtype);
        return k -> cache.get(k, producer);
    }
}
