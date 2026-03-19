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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

final class NamedThreadFactory implements ThreadFactory {
    
    private static final boolean USE_CONTINUATION_AWARE_THREAD = Boolean.getBoolean("net.tascalate.javaflow.check-thread");
    
    private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);
    
    private final ThreadGroup group;
    private final AtomicInteger counter = new AtomicInteger(1);
    private final String namePrefix;

    NamedThreadFactory(String namePrefix) {
        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        this.namePrefix = namePrefix == null || namePrefix.length() == 0? 
                "pool-" + POOL_NUMBER.getAndIncrement() + "-thread-"
                :
                namePrefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread result = USE_CONTINUATION_AWARE_THREAD ?
            new ContinuationAwareThread(group, r, namePrefix + counter.incrementAndGet(), 0)
            :
            new Thread(group, r, namePrefix + counter.incrementAndGet(), 0);
        
        if (!result.isDaemon()) {
            result.setDaemon(true);
        }
        
        if (result.getPriority() != Thread.NORM_PRIORITY) {
            result.setPriority(Thread.NORM_PRIORITY);
        }
        
        return result;
    }
    
}