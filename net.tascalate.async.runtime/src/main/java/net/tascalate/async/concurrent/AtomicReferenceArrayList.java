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
package net.tascalate.async.concurrent;

import java.util.AbstractList;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicReferenceArray;

final class AtomicReferenceArrayList<E> extends AbstractList<E>
                                        implements RandomAccess {

    private final AtomicReferenceArray<E> array;

    public AtomicReferenceArrayList(int size) {
        this(new AtomicReferenceArray<>(size));
    }
    
    public AtomicReferenceArrayList(AtomicReferenceArray<E> array) {
        Objects.requireNonNull(array);
        this.array = array;
    }

    @Override
    public E get(int index) {
        rangeCheck(index);
        return array.get(index);
    }

    @Override
    public E set(int index, E element) {
        rangeCheck(index);
        return array.getAndSet(index, element);
    }

    @Override
    public int size() {
        return array.length();
    }

    private void rangeCheck(int index) {
        if (index < 0 || index >= array.length())
            throw new IndexOutOfBoundsException("index: " + index);
    }

    // Optional convenience: expose CAS
    public boolean compareAndSet(int index, E expect, E update) {
        rangeCheck(index);
        return array.compareAndSet(index, expect, update);
    }
}