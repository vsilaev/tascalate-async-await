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

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "async-await.executor")
public class AsyncAwaitExecutorProperties {
    private IntValueFormula corePoolSize = IntValueFormula.scale(1, 1);
    private IntValueFormula maximumPoolSize = IntValueFormula.scale(1, 1);
    private Duration keepAliveTime = Duration.ofMinutes(1);
    private IntValueFormula workQueueSize = IntValueFormula.constant(Integer.MAX_VALUE);
    private String threadNamePrefix = "async-await-scheduler-thread_";
    
    public IntValueFormula getCorePoolSize() {
        return corePoolSize;
    }
    
    public void setCorePoolSize(IntValueFormula corePoolSize) {
        this.corePoolSize = corePoolSize;
    }
    
    public IntValueFormula getMaximumPoolSize() {
        return maximumPoolSize;
    }
    
    public void setMaximumPoolSize(IntValueFormula maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }
    
    public Duration getKeepAliveTime() {
        return keepAliveTime;
    }
    
    public void setKeepAliveTime(Duration keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
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
        int corePoolSizeValue = corePoolSize == null ? cores : corePoolSize.applyAsInt(cores);
        int maximumPoolSizeValue = maximumPoolSize == null ? cores : maximumPoolSize.applyAsInt(cores); 
        TimeMeasurment tm = keepAliveTime == null ? new TimeMeasurment(Duration.ofMinutes(1)) : new TimeMeasurment(keepAliveTime);
        int workQueueSizeValue = workQueueSize == null ? Integer.MAX_VALUE : workQueueSize.applyAsInt(cores);
        return new ThreadPoolExecutor(
                corePoolSizeValue > 0 ? corePoolSizeValue : cores, 
                maximumPoolSizeValue > 0 ? maximumPoolSizeValue : cores,
                tm.amount, tm.unit,
                new LinkedBlockingDeque<>(workQueueSizeValue > 0 ? workQueueSizeValue : Integer.MAX_VALUE),
                new NamedThreadFactory(null == threadNamePrefix || threadNamePrefix.isEmpty() ? "async-await-scheduler-thread_" : threadNamePrefix));        

    }


    static class TimeMeasurment {
        final TimeUnit unit;
        final long amount;
        
        TimeMeasurment(Duration duration) {
            // Try to get value with best precision without throwing ArythmeticException due to overflow
            if (duration.compareTo(MAX_BY_NANOS) < 0) {
                amount = duration.toNanos();
                unit = TimeUnit.NANOSECONDS;
            } else if (duration.compareTo(MAX_BY_MILLIS) < 0) {
                amount = duration.toMillis();
                unit = TimeUnit.MILLISECONDS;
            } else {
                amount = duration.getSeconds();
                unit = TimeUnit.SECONDS; 
            }
        }
    }
    
    private static final Duration MAX_BY_NANOS  = Duration.ofNanos(Long.MAX_VALUE);
    private static final Duration MAX_BY_MILLIS = Duration.ofMillis(Long.MAX_VALUE);
}
