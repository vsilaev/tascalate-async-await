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

import java.util.concurrent.ExecutorService;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Lazy
@Component
@ConfigurationProperties(prefix = "async-await.executor")
public class AsyncAwaitExecutorProperties21 {
    private IntValueFormula maximumConcurrentThreads = IntValueFormula.scale(1000, 4);
    private IntValueFormula workQueueSize = IntValueFormula.constant(Integer.MAX_VALUE);
    private String threadNamePrefix = "async-await-scheduler-vthread_";
    
    public IntValueFormula getMaximumConcurrentThreads() {
        return maximumConcurrentThreads;
    }
    
    public void setMaximumConcurrentThreads(IntValueFormula maximumConcurrentThreads) {
        this.maximumConcurrentThreads = maximumConcurrentThreads;
    }

    public IntValueFormula getWorkQueueSize() {
        return workQueueSize;
    }
    
    public void setWorkQueueSize(IntValueFormula workQueueSize) {
        this.workQueueSize = workQueueSize;
    }
    
    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }
    
    public ExecutorService createExecutorService() {
        Runtime rt = Runtime.getRuntime();
        int cores = rt.availableProcessors();
        int maximumConcurrentThreadsValue = maximumConcurrentThreads == null ? cores : maximumConcurrentThreads.applyAsInt(cores); 
        int workQueueSizeValue = workQueueSize == null ? Integer.MAX_VALUE : workQueueSize.applyAsInt(cores);
        return new ThrottledExecutorService(
            Thread.ofVirtual()
                  .name(null == threadNamePrefix || threadNamePrefix.isEmpty() ? "async-await-scheduler-vthread_" : threadNamePrefix, 0)
                  .factory(),
            maximumConcurrentThreadsValue,
            workQueueSizeValue
        );  
    }
}
