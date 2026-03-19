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
package net.tascalate.async.spring;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.javaflow.core.ScopedContinuationExecutor;
import org.apache.commons.javaflow.core.StackRecorder;

class ContinuationAwareThread extends Thread implements ScopedContinuationExecutor {
    
    private static final AtomicInteger NEXT_THREAD_ID = new AtomicInteger();
    
    private StackRecorder currentStackRecorder;
    
    public ContinuationAwareThread() {
        this(null, null, "Thread-" + NEXT_THREAD_ID.getAndIncrement(), 0);
    }
    
    public ContinuationAwareThread(Runnable target) {
        this(null, target, "Thread-" + NEXT_THREAD_ID.getAndIncrement(), 0);
    }

    public ContinuationAwareThread(ThreadGroup group, Runnable target) {
        this(group, target, "Thread-" + NEXT_THREAD_ID.getAndIncrement(), 0);
    }
    
    public ContinuationAwareThread(String name) {
        this(null, null, name, 0);
    }
    
    public ContinuationAwareThread(ThreadGroup group, String name) {
        this(group, null, name, 0);
    }
    
    public ContinuationAwareThread(Runnable target, String name) {
        this(null, target, name, 0);
    }
    
    public ContinuationAwareThread(ThreadGroup group, Runnable target, String name) {
        this(group, target, name, 0);
    }
    
    public ContinuationAwareThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, target, name, stackSize);
    }

    @Override
    public void runWith(StackRecorder stackRecorder, Runnable code) {
        StackRecorder previous = currentStackRecorder;
        currentStackRecorder = stackRecorder;
        try {
            code.run();
        } finally {
            currentStackRecorder = previous;
        }
    }

    @Override
    public StackRecorder currentStackRecorder() {
        return currentStackRecorder;
    }

}
