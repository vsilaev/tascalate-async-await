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
package net.tascalate.async;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.tascalate.async.core.AsyncMethodExecutor;

final class AwaitableQueue<T> {
    private final Queue<T> items = new ConcurrentLinkedQueue<>();
    private final Queue<CompletableFuture<?>> signals = new LinkedList<>();
    private final Lock lock = new ReentrantLock();
    
    private CompletableFuture<?> currentAwaitingSignal;
    
    AwaitableQueue() {
        signals.offer(new CompletableFuture<>());
    }
    
    @suspendable void await() {
        CompletableFuture<?> signal;
        lock.lock();
        try {
            currentAwaitingSignal = signal = signals.peek();
        } finally {
            lock.unlock();
        }
        
        AsyncMethodExecutor.await(signal);
    }
    
    void offer(T item) {
        items.offer(item);
        lock.lock();
        try {
            CompletableFuture<?> headSignal = signals.peek();
            if (headSignal == currentAwaitingSignal && headSignal.complete(null)) {
                signals.offer(new CompletableFuture<>());
                signals.poll();
            } else if (headSignal != COMPLETED_SIGNAL) {
                    signals.offer(COMPLETED_SIGNAL);
                    signals.offer(new CompletableFuture<>());
                    signals.poll();
            }
        } finally {
            lock.unlock();
        }
    }
    
    T poll() {
        return items.poll();
    }
    
    private static final CompletableFuture<?> COMPLETED_SIGNAL = CompletableFuture.completedFuture(null);
}
